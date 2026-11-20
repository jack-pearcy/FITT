package com.fitt.coach.controller;

import com.fitt.coach.service.CoachModel;
import com.fitt.coach.service.exception.CoachFailedException;
import com.fitt.identity.repository.UserRepository;
import com.fitt.identity.service.AuthTokens;
import com.fitt.identity.service.IdentityAuthService;
import com.fitt.workouts.dto.AddExerciseEntryRequest;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.LogSetRequest;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import com.fitt.workouts.domain.MuscleGroup;
import com.fitt.workouts.dto.CreateExerciseRequest;
import com.fitt.workouts.service.ExerciseService;
import com.fitt.workouts.service.WorkoutSessionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The coach end to end: real tokens, the real security chain and a real database.
// Only the model is replaced, so no test ever calls Anthropic or needs a key.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoachReviewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityAuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkoutSessionService sessionService;

    @Autowired
    private ExerciseService exerciseService;

    @MockitoBean
    private CoachModel coachModel;

    private record Account(long id, String email, AuthTokens tokens) {
    }

    @Test
    void review_sendsTheTrainingToTheModelAndReturnsItsAnswer() throws Exception {
        Account account = register();
        finishSession(account, "private note about a sore knee");
        when(coachModel.isAvailable()).thenReturn(true);
        when(coachModel.review(anyString(), anyString())).thenReturn("Solid week. Add 2.5 kg next time.");

        review(account, account.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review").value("Solid week. Add 2.5 kg next time."))
                .andExpect(jsonPath("$.sessionsReviewed").value(1))
                .andExpect(jsonPath("$.generatedAt").isNotEmpty());

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(coachModel).review(anyString(), sent.capture());
        assertThat(sent.getValue()).contains("Test Squat (legs): 100 kg x 5 @ RPE 8");
        // Nothing that says who this is, and not the note the user wrote.
        assertThat(sent.getValue()).doesNotContain(account.email()).doesNotContain("knee");
    }

    @Test
    void review_withNoKeyConfigured_returns503AndAsksTheModelNothing() throws Exception {
        Account account = register();
        finishSession(account, null);
        when(coachModel.isAvailable()).thenReturn(false);

        review(account, account.id())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("The coach is not set up on this server"));

        verify(coachModel, never()).review(anyString(), anyString());
    }

    @Test
    void review_withNoFinishedWorkout_returns400AndAsksTheModelNothing() throws Exception {
        Account account = register();
        // Started but never finished, so there is nothing to review.
        sessionService.createSession(account.id(), new CreateWorkoutSessionRequest(Instant.now(), null, null));
        when(coachModel.isAvailable()).thenReturn(true);

        review(account, account.id())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Finish at least one workout before asking the coach"));

        verify(coachModel, never()).review(anyString(), anyString());
    }

    @Test
    void review_whenTheModelFails_returns502() throws Exception {
        Account account = register();
        finishSession(account, null);
        when(coachModel.isAvailable()).thenReturn(true);
        when(coachModel.review(anyString(), anyString()))
                .thenThrow(new CoachFailedException("The coach could not answer right now"));

        review(account, account.id())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("The coach could not answer right now"));
    }

    @Test
    void review_pastTheDailyLimit_returns429WithoutAskingTheModelAgain() throws Exception {
        Account account = register();
        finishSession(account, null);
        when(coachModel.isAvailable()).thenReturn(true);
        when(coachModel.review(anyString(), anyString())).thenReturn("ok");

        for (int i = 0; i < 5; i++) {
            review(account, account.id()).andExpect(status().isOk());
        }

        review(account, account.id())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        verify(coachModel, times(5)).review(anyString(), anyString());
    }

    @Test
    void review_ofAnotherUser_returns403() throws Exception {
        Account account = register();
        Account other = register();
        finishSession(other, null);
        when(coachModel.isAvailable()).thenReturn(true);

        review(account, other.id()).andExpect(status().isForbidden());

        verify(coachModel, never()).review(anyString(), anyString());
    }

    @Test
    void review_withoutAToken_returns401() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/coach/review", 1)).andExpect(status().isUnauthorized());
    }

    private ResultActions review(Account caller, long userId) throws Exception {
        return mockMvc.perform(post("/api/users/{userId}/coach/review", userId)
                .header("Authorization", "Bearer " + caller.tokens().accessToken()));
    }

    private void finishSession(Account account, String notes) {
        Long exerciseId = exerciseService.createCustomExercise(account.id(),
                new CreateExerciseRequest("Test Squat", MuscleGroup.LEGS)).id();
        WorkoutSessionResponse session = sessionService.createSession(account.id(),
                new CreateWorkoutSessionRequest(Instant.now().minusSeconds(3600), null, notes));
        WorkoutSessionResponse withEntry = sessionService.addExerciseEntry(account.id(), session.id(),
                new AddExerciseEntryRequest(exerciseId));
        sessionService.logSet(account.id(), session.id(), withEntry.exerciseEntries().get(0).id(),
                new LogSetRequest(5, new BigDecimal("100"), new BigDecimal("8")));
        sessionService.finishSession(account.id(), session.id());
    }

    private Account register() {
        String email = "coach-" + UUID.randomUUID() + "@example.com";
        AuthTokens tokens = authService.register(email, "pw-" + UUID.randomUUID());
        return new Account(userRepository.findByEmail(email).orElseThrow().getId(), email, tokens);
    }
}
