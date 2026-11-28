package com.fitt.workouts.controller;

import com.fitt.workouts.domain.SplitType;
import com.fitt.workouts.dto.CreateWorkoutPlanDayRequest;
import com.fitt.workouts.dto.CreateWorkoutPlanRequest;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.NextLiftResponse;
import com.fitt.workouts.service.NextLiftService;
import com.fitt.workouts.service.WorkoutPlanService;
import com.fitt.workouts.service.WorkoutSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Starting a workout on a plan day the user picks, through the whole stack: real
// service, real database, real error mapping. Each test has its own user, because
// a user may have only one open session.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PickPlanDayTest {

    private static final String URL = "/api/users/{userId}/workout-sessions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkoutSessionService service;

    @Autowired
    private WorkoutPlanService planService;

    @Autowired
    private NextLiftService nextLiftService;

    @Autowired
    private JdbcTemplate jdbc;

    private long userId;
    private long planId;
    private Instant startedAt;

    @BeforeEach
    void anActiveThreeDayPlan() {
        userId = ThreadLocalRandom.current().nextLong(100_000L, 1_000_000_000L);
        startedAt = Instant.now().minus(Duration.ofHours(2)).truncatedTo(ChronoUnit.SECONDS);
        planId = planService.createPlan(userId, new CreateWorkoutPlanRequest("Pick plan", 3, SplitType.FULL_BODY,
                List.of(new CreateWorkoutPlanDayRequest(0, "A", List.of()),
                        new CreateWorkoutPlanDayRequest(1, "B", List.of()),
                        new CreateWorkoutPlanDayRequest(2, "C", List.of())))).id();
        planService.activatePlan(userId, planId);
    }

    @Test
    void start_withAPickedDay_trainsThatDay() throws Exception {
        long sessionId = start("{\"startedAt\":\"" + startedAt + "\",\"planId\":" + planId + ",\"planDayIndex\":2}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planId").value(planId))
                .andReturn().getResponse().getContentAsString().transform(PickPlanDayTest::id);

        assertThat(storedDay(sessionId)).isEqualTo(2);
    }

    @Test
    void start_withoutAPickedDay_followsTheRotationAsBefore() throws Exception {
        long first = service.createSession(userId, new CreateWorkoutSessionRequest(startedAt, planId, null)).id();
        assertThat(storedDay(first)).isZero();
        service.finishSession(userId, first);

        long second = start("{\"startedAt\":\"" + Instant.now().truncatedTo(ChronoUnit.SECONDS)
                + "\",\"planId\":" + planId + "}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(PickPlanDayTest::id);

        assertThat(storedDay(second)).isEqualTo(1);
    }

    @Test
    void nextLift_movesOnFromThePickedDay() {
        long sessionId = service.createSession(userId,
                new CreateWorkoutSessionRequest(startedAt, planId, null, null, 2)).id();
        service.finishSession(userId, sessionId);

        NextLiftResponse next = nextLiftService.getNextLift(userId);

        // Day C was trained, so the rotation wraps to day A, not to day B.
        assertThat(next.dayNumber()).isEqualTo(1);
        assertThat(next.day().dayLabel()).isEqualTo("A");
    }

    @Test
    void start_withADayThePlanDoesNotHave_returns400UnderTheField_andStoresNothing() throws Exception {
        expectFieldError("{\"startedAt\":\"" + startedAt + "\",\"planId\":" + planId + ",\"planDayIndex\":3}");
        expectFieldError("{\"startedAt\":\"" + startedAt + "\",\"planId\":" + planId + ",\"planDayIndex\":-1}");

        assertThat(service.getOpenSession(userId)).isEmpty();
    }

    @Test
    void start_withADayButNoPlan_returns400UnderTheField() throws Exception {
        expectFieldError("{\"startedAt\":\"" + startedAt + "\",\"planDayIndex\":0}");

        assertThat(service.getOpenSession(userId)).isEmpty();
    }

    @Test
    void aRepeatWithTheSameClientId_returnsTheStoredSession_evenWithAnotherDay() throws Exception {
        String clientId = UUID.randomUUID().toString();
        long first = service.createSession(userId,
                new CreateWorkoutSessionRequest(startedAt, planId, null, clientId, 1)).id();

        long repeat = start("{\"startedAt\":\"" + startedAt + "\",\"planId\":" + planId
                + ",\"clientId\":\"" + clientId + "\",\"planDayIndex\":2}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(PickPlanDayTest::id);

        assertThat(repeat).isEqualTo(first);
        assertThat(storedDay(first)).isEqualTo(1);
    }

    private ResultActions start(String body) throws Exception {
        return mockMvc.perform(post(URL, userId)
                .with(jwt().jwt(token -> token.claim("uid", userId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void expectFieldError(String body) throws Exception {
        start(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.planDayIndex").exists());
    }

    private Integer storedDay(long sessionId) {
        return jdbc.queryForObject("select plan_day_index from workout_sessions where id = ?", Integer.class, sessionId);
    }

    private static long id(String json) {
        int start = json.indexOf("\"id\":") + 5;
        int end = json.indexOf(',', start);
        return Long.parseLong(json.substring(start, end).trim());
    }
}
