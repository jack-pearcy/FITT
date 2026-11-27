package com.fitt.workouts.controller;

import com.fitt.workouts.domain.SplitType;
import com.fitt.workouts.dto.AddExerciseEntryRequest;
import com.fitt.workouts.dto.CreateWorkoutPlanDayRequest;
import com.fitt.workouts.dto.CreateWorkoutPlanRequest;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.LogSetRequest;
import com.fitt.workouts.dto.SetEntryResponse;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.repository.ExerciseRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Editing a logged set and a session after the fact, through the whole stack:
// real service, real database, real error mapping. Each test has its own user,
// because a user may have only one open session.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionEditTest {

    private static final String SET_URL = "/api/users/{userId}/workout-sessions/{sessionId}/entries/{entryId}/sets/{setId}";
    private static final String SESSION_URL = "/api/users/{userId}/workout-sessions/{sessionId}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkoutSessionService service;

    @Autowired
    private WorkoutPlanService planService;

    @Autowired
    private NextLiftService nextLiftService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long userId;
    private Instant startedAt;
    private long sessionId;
    private long entryId;
    private long setId;

    @BeforeEach
    void aSessionThreeDaysOldWithOneLoggedSet() {
        userId = ThreadLocalRandom.current().nextLong(100_000L, 1_000_000_000L);
        startedAt = Instant.now().minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS);
        ExerciseEntity exercise = exerciseRepository.save(
                new ExerciseEntity(null, "Edit Squat " + UUID.randomUUID(), "LEGS", false));
        sessionId = service.createSession(userId, new CreateWorkoutSessionRequest(startedAt, null, "Legs")).id();
        entryId = service.addExerciseEntry(userId, sessionId, new AddExerciseEntryRequest(exercise.getId()))
                .exerciseEntries().get(0).id();
        setId = service.logSet(userId, sessionId, entryId,
                        new LogSetRequest(8, new BigDecimal("100.00"), new BigDecimal("8.0")))
                .exerciseEntries().get(0).sets().get(0).id();
    }

    // ---- set edit

    @Test
    void updateSet_replacesTheNumbers_andTheChangeIsStored() throws Exception {
        service.finishSession(userId, sessionId);

        send(put(SET_URL, userId, sessionId, entryId, setId), "{\"reps\":6,\"weight\":102.5,\"rpe\":9}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseEntries[0].sets[0].reps").value(6));

        SetEntryResponse stored = service.getSession(userId, sessionId).exerciseEntries().get(0).sets().get(0);
        assertThat(stored.id()).isEqualTo(setId);
        assertThat(stored.setNumber()).isEqualTo(1);
        assertThat(stored.reps()).isEqualTo(6);
        assertThat(stored.weight()).isEqualByComparingTo("102.5");
        assertThat(stored.rpe()).isEqualByComparingTo("9");
        assertThat(jdbc.queryForObject("select reps from set_entries where id = ?", Integer.class, setId))
                .isEqualTo(6);
    }

    @Test
    void updateSet_withNoWeightOrRpe_clearsThem() throws Exception {
        send(put(SET_URL, userId, sessionId, entryId, setId), "{\"reps\":12}")
                .andExpect(status().isOk());

        SetEntryResponse stored = service.getSession(userId, sessionId).exerciseEntries().get(0).sets().get(0);
        assertThat(stored.weight()).isNull();
        assertThat(stored.rpe()).isNull();
    }

    @Test
    void updateSet_outOfBounds_returns400UnderTheField_andChangesNothing() throws Exception {
        expectFieldError(put(SET_URL, userId, sessionId, entryId, setId), "{\"reps\":-1}", "reps");
        expectFieldError(put(SET_URL, userId, sessionId, entryId, setId), "{}", "reps");
        expectFieldError(put(SET_URL, userId, sessionId, entryId, setId), "{\"reps\":5,\"weight\":10000}", "weight");
        expectFieldError(put(SET_URL, userId, sessionId, entryId, setId), "{\"reps\":5,\"weight\":1.234}", "weight");
        expectFieldError(put(SET_URL, userId, sessionId, entryId, setId), "{\"reps\":5,\"rpe\":10.5}", "rpe");

        assertThat(service.getSession(userId, sessionId).exerciseEntries().get(0).sets().get(0).reps()).isEqualTo(8);
    }

    @Test
    void updateSet_aSetFromAnotherEntryOrSession_returns404() throws Exception {
        long otherUser = userId + 1;
        long otherSession = service.createSession(otherUser,
                new CreateWorkoutSessionRequest(startedAt, null, null)).id();

        send(put(SET_URL, userId, sessionId, entryId, 987_654_321L), "{\"reps\":5}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Set Entry Not Found"));
        send(put(SET_URL, userId, sessionId, 987_654_321L, setId), "{\"reps\":5}")
                .andExpect(status().isNotFound());
        // The other user's session under the caller's own path.
        send(put(SET_URL, userId, otherSession, entryId, setId), "{\"reps\":5}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workout Session Not Found"));
    }

    @Test
    void updateSet_onAnotherUsersPath_returns403() throws Exception {
        mockMvc.perform(put(SET_URL, userId + 1, sessionId, entryId, setId)
                        .with(jwt().jwt(token -> token.claim("uid", userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reps\":5}"))
                .andExpect(status().isForbidden());
    }

    // ---- session edit

    @Test
    void updateSession_movesTheTimesOfAFinishedWorkout() throws Exception {
        service.finishSession(userId, sessionId);
        Instant newStart = startedAt.minus(Duration.ofHours(1));
        Instant newFinish = startedAt.plus(Duration.ofMinutes(50));

        send(patch(SESSION_URL, userId, sessionId),
                "{\"startedAt\":\"" + newStart + "\",\"finishedAt\":\"" + newFinish + "\",\"notes\":\"  Heavy  \"}")
                .andExpect(status().isOk());

        WorkoutSessionResponse stored = service.getSession(userId, sessionId);
        assertThat(stored.startedAt()).isEqualTo(newStart);
        assertThat(stored.finishedAt()).isEqualTo(newFinish);
        assertThat(stored.notes()).isEqualTo("Heavy");
    }

    @Test
    void updateSession_missingFieldsKeepWhatIsStored_andBlankNotesClear() throws Exception {
        send(patch(SESSION_URL, userId, sessionId), "{}").andExpect(status().isOk());
        assertThat(service.getSession(userId, sessionId).notes()).isEqualTo("Legs");
        assertThat(service.getSession(userId, sessionId).startedAt()).isEqualTo(startedAt);

        send(patch(SESSION_URL, userId, sessionId), "{\"notes\":\"   \"}").andExpect(status().isOk());
        assertThat(service.getSession(userId, sessionId).notes()).isNull();
    }

    @Test
    void updateSession_finishBeforeStart_isRefused() throws Exception {
        service.finishSession(userId, sessionId);

        expectFieldError(patch(SESSION_URL, userId, sessionId),
                "{\"finishedAt\":\"" + startedAt.minusSeconds(60) + "\"}", "finishedAt");
        expectFieldError(patch(SESSION_URL, userId, sessionId),
                "{\"finishedAt\":\"" + startedAt + "\"}", "finishedAt");
        // Only the start sent: compared with the finish already stored.
        send(patch(SESSION_URL, userId, sessionId),
                "{\"finishedAt\":\"" + startedAt.plus(Duration.ofHours(1)) + "\"}")
                .andExpect(status().isOk());
        expectFieldError(patch(SESSION_URL, userId, sessionId),
                "{\"startedAt\":\"" + startedAt.plus(Duration.ofHours(2)) + "\"}", "startedAt");
    }

    @Test
    void updateSession_finishTimeOnAnOpenWorkout_isRefused_andItStaysOpen() throws Exception {
        expectFieldError(patch(SESSION_URL, userId, sessionId),
                "{\"finishedAt\":\"" + startedAt.plus(Duration.ofHours(1)) + "\"}", "finishedAt");

        assertThat(service.getSession(userId, sessionId).finishedAt()).isNull();
    }

    @Test
    void updateSession_timesInTheFutureOrBefore2000_areRefused() throws Exception {
        service.finishSession(userId, sessionId);

        expectFieldError(patch(SESSION_URL, userId, sessionId),
                "{\"finishedAt\":\"" + Instant.now().plus(Duration.ofHours(1)) + "\"}", "finishedAt");
        expectFieldError(patch(SESSION_URL, userId, sessionId),
                "{\"startedAt\":\"1999-12-31T23:59:59Z\"}", "startedAt");
    }

    @Test
    void updateSession_aFinishAFewSecondsAhead_isStoredAsTheServersNow() throws Exception {
        service.finishSession(userId, sessionId);
        Instant ahead = Instant.now().plusSeconds(90);

        send(patch(SESSION_URL, userId, sessionId), "{\"finishedAt\":\"" + ahead + "\"}")
                .andExpect(status().isOk());

        assertThat(service.getSession(userId, sessionId).finishedAt()).isBefore(ahead);
    }

    @Test
    void updateSession_notesTooLong_returns400() throws Exception {
        expectFieldError(patch(SESSION_URL, userId, sessionId),
                "{\"notes\":\"" + "x".repeat(5001) + "\"}", "notes");
    }

    @Test
    void updateSession_anotherUsersSession_returns404_andChangesNothing() throws Exception {
        long caller = userId + 1;

        mockMvc.perform(patch(SESSION_URL, caller, sessionId)
                        .with(jwt().jwt(token -> token.claim("uid", caller)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Mine now\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workout Session Not Found"));

        assertThat(service.getSession(userId, sessionId).notes()).isEqualTo("Legs");
    }

    // Trained-today reads the finish time, so moving the finish off today clears
    // it and moving it back sets it again.
    @Test
    void updateSession_movingTheFinish_movesTrainedToday() throws Exception {
        long planId = planService.createPlan(userId, new CreateWorkoutPlanRequest("Edit plan", 1, SplitType.FULL_BODY,
                List.of(new CreateWorkoutPlanDayRequest(0, "A", List.of())))).id();
        planService.activatePlan(userId, planId);
        service.finishSession(userId, sessionId);
        assertThat(nextLiftService.getNextLift(userId).alreadyTrainedToday()).isTrue();

        send(patch(SESSION_URL, userId, sessionId),
                "{\"finishedAt\":\"" + startedAt.plus(Duration.ofHours(1)) + "\"}")
                .andExpect(status().isOk());
        assertThat(nextLiftService.getNextLift(userId).alreadyTrainedToday()).isFalse();

        send(patch(SESSION_URL, userId, sessionId), "{\"finishedAt\":\"" + Instant.now() + "\"}")
                .andExpect(status().isOk());
        assertThat(nextLiftService.getNextLift(userId).alreadyTrainedToday()).isTrue();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request
                .with(jwt().jwt(token -> token.claim("uid", userId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void expectFieldError(MockHttpServletRequestBuilder request, String body, String field) throws Exception {
        send(request, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors." + field).exists());
    }
}
