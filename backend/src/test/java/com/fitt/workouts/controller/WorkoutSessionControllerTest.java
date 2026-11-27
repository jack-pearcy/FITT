package com.fitt.workouts.controller;

import com.fitt.workouts.domain.MuscleGroup;
import com.fitt.workouts.dto.AddExerciseEntryRequest;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.ExerciseEntryResponse;
import com.fitt.workouts.dto.ExerciseResponse;
import com.fitt.workouts.dto.FinishSessionRequest;
import com.fitt.workouts.dto.LogSetRequest;
import com.fitt.workouts.dto.SetEntryResponse;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import com.fitt.workouts.service.WorkoutSessionService;
import com.fitt.workouts.service.exception.OpenSessionExistsException;
import com.fitt.workouts.service.exception.WorkoutSessionNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controller test for the workout session endpoints. Pins the four distinct
// status-code shapes on this controller: 201 on create, 204 on top-level
// delete, 200 with body on actions, and 200 with body on nested deletes (which
// is intentionally non-204 so the UI can re-render after the call).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkoutSessionControllerTest {

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long SESSION_ID = 300L;
    private static final long ENTRY_ID = 301L;
    private static final long PLAN_ID = 999L;
    private static final String CLIENT_ID = "0b9f6c1e-5a1d-4a57-9f0e-3d2c1b4a5e6f";
    // The form Swift's UUID().uuidString writes, so the form the iPhone and the
    // watch send.
    private static final String UPPER_CASE_CLIENT_ID = "0B9F6C1E-5A1D-4A57-9F0E-3D2C1B4A5E6F";

    private static final String CREATE_BODY = """
            {
              "startedAt": "2026-05-25T10:00:00Z",
              "notes": "Test session"
            }
            """;

    private static final String ADD_ENTRY_BODY = """
            {
              "exerciseId": 50
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkoutSessionService workoutSessionService;

    @Test
    void createSession_validOwnership_returns201WithBody() throws Exception {
        when(workoutSessionService.createSession(eq(USER_ID), any()))
                .thenReturn(sampleSession());

        mockMvc.perform(post("/api/users/{userId}/workout-sessions", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(SESSION_ID))
                .andExpect(jsonPath("$.planId").value(PLAN_ID))
                .andExpect(jsonPath("$.notes").value("Test session"));
    }

    @Test
    void createSession_withAnOpenSession_returns409WithItsId() throws Exception {
        when(workoutSessionService.createSession(eq(USER_ID), any()))
                .thenThrow(new OpenSessionExistsException(SESSION_ID));

        mockMvc.perform(post("/api/users/{userId}/workout-sessions", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Open Session Exists"))
                // Problem properties arrive under "properties", as the "errors" map
                // of a validation failure does.
                .andExpect(jsonPath("$.properties.openSessionId").value(SESSION_ID));
    }

    @Test
    void getOpenSession_withOneOpen_returns200WithIt() throws Exception {
        when(workoutSessionService.getOpenSession(USER_ID)).thenReturn(Optional.of(sampleSession()));

        mockMvc.perform(get("/api/users/{userId}/workout-sessions/open", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID))
                .andExpect(jsonPath("$.finishedAt").doesNotExist());
    }

    @Test
    void getOpenSession_withNoneOpen_returns204() throws Exception {
        when(workoutSessionService.getOpenSession(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/{userId}/workout-sessions/open", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    // The stage 0 contract test row says 404 for a foreign user. This route answers
    // 403, as every other route under /api/users/{userId}/workout-sessions does on
    // a path user mismatch; one route alone answering 404 would be inconsistent.
    @Test
    void getOpenSession_ofAnotherUser_returns403AndReadsNothing() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/workout-sessions/open", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", OTHER_USER_ID))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(workoutSessionService);
    }

    @Test
    void listSessions_validOwnership_returns200WithList() throws Exception {
        when(workoutSessionService.listSessions(USER_ID))
                .thenReturn(List.of(sampleSession()));

        mockMvc.perform(get("/api/users/{userId}/workout-sessions", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(SESSION_ID));
    }

    @Test
    void getSession_validOwnership_returns200WithBody() throws Exception {
        when(workoutSessionService.getSession(USER_ID, SESSION_ID))
                .thenReturn(sampleSession());

        mockMvc.perform(get("/api/users/{userId}/workout-sessions/{sessionId}",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID))
                .andExpect(jsonPath("$.ownerUserId").value(USER_ID));
    }

    @Test
    void finishSession_validOwnership_returns200WithFinishedSession() throws Exception {
        // finishSession is a state-transition POST to a sub-resource. Returns
        // 200 with the updated session body, not 204, so the client can show
        // finishedAt without a second request.
        WorkoutSessionResponse finished = new WorkoutSessionResponse(
                SESSION_ID, USER_ID, PLAN_ID,
                Instant.parse("2026-05-25T10:00:00Z"),
                Instant.parse("2026-05-25T11:00:00Z"),
                "Test session", List.of());
        when(workoutSessionService.finishSession(USER_ID, SESSION_ID, null))
                .thenReturn(finished);

        mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/finish",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finishedAt").value("2026-05-25T11:00:00Z"));
    }

    // The web sends {} and older apps send nothing at all. Both must keep working.
    @Test
    void finishSession_withAnEmptyJsonBody_returns200() throws Exception {
        when(workoutSessionService.finishSession(USER_ID, SESSION_ID, new FinishSessionRequest(null, null)))
                .thenReturn(sampleSession());

        mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/finish",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID));
    }

    @Test
    void finishSession_withAJsonContentTypeAndNoBody_returns200() throws Exception {
        when(workoutSessionService.finishSession(USER_ID, SESSION_ID, null))
                .thenReturn(sampleSession());

        mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/finish",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID));
    }

    @Test
    void finishSession_withClientIdAndFinishTime_passesBothToTheService() throws Exception {
        FinishSessionRequest expected = new FinishSessionRequest(
                CLIENT_ID, Instant.parse("2026-05-25T11:00:00Z"));
        when(workoutSessionService.finishSession(USER_ID, SESSION_ID, expected))
                .thenReturn(sampleSession());

        mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/finish",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + CLIENT_ID + "\",\"finishedAt\":\"2026-05-25T11:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID));
    }

    // A wrong device clock is not a 400. The retry would carry the same time, so
    // the session could never be finished. The service picks the time to store.
    @Test
    void finishSession_withAFinishTimeOutsideTheBounds_isNotRefused() throws Exception {
        for (String outside : List.of("1999-12-31T23:59:59Z", Instant.now().plus(Duration.ofHours(73)).toString())) {
            when(workoutSessionService.finishSession(USER_ID, SESSION_ID,
                    new FinishSessionRequest(null, Instant.parse(outside))))
                    .thenReturn(sampleSession());

            mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/finish",
                            USER_ID, SESSION_ID)
                            .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"finishedAt\":\"" + outside + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(SESSION_ID));
        }
    }

    @Test
    void finishSession_anotherUsersPath_returns403() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/finish",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", OTHER_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(workoutSessionService);
    }

    @Test
    void createSession_withAClientIdThatIsNotAUuid_returns400UnderTheField() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/workout-sessions", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"2026-05-25T10:00:00Z\",\"clientId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.clientId").value("Client id must be a UUID"));

        verifyNoInteractions(workoutSessionService);
    }

    @Test
    void createSession_withAClientId_passesItToTheService() throws Exception {
        when(workoutSessionService.createSession(USER_ID, new CreateWorkoutSessionRequest(
                Instant.parse("2026-05-25T10:00:00Z"), null, null, CLIENT_ID)))
                .thenReturn(sampleSession());

        mockMvc.perform(post("/api/users/{userId}/workout-sessions", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"2026-05-25T10:00:00Z\",\"clientId\":\"" + CLIENT_ID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(SESSION_ID));
    }

    @Test
    void addExerciseEntry_withAClientIdThatIsNotAUuid_returns400UnderTheField() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/entries",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":50,\"clientId\":\"" + CLIENT_ID + "0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.clientId").exists());

        verifyNoInteractions(workoutSessionService);
    }

    @Test
    void logSet_withClientIdAndTime_passesBoth_andTheSetInTheAnswerCarriesThem() throws Exception {
        Instant performedAt = Instant.parse("2026-05-25T10:05:30Z");
        WorkoutSessionResponse withSet = new WorkoutSessionResponse(
                SESSION_ID, USER_ID, PLAN_ID, Instant.parse("2026-05-25T10:00:00Z"), null, null,
                List.of(new ExerciseEntryResponse(ENTRY_ID, 0,
                        new ExerciseResponse(50L, "Bench Press", MuscleGroup.CHEST, false),
                        List.of(new SetEntryResponse(1L, 1, 5, new BigDecimal("100.00"), null, true,
                                CLIENT_ID, performedAt)))));
        when(workoutSessionService.logSet(USER_ID, SESSION_ID, ENTRY_ID,
                new LogSetRequest(5, new BigDecimal("100.00"), null, CLIENT_ID, performedAt)))
                .thenReturn(withSet);

        mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/entries/{entryId}/sets",
                        USER_ID, SESSION_ID, ENTRY_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reps\":5,\"weight\":100.00,\"clientId\":\"" + CLIENT_ID
                                + "\",\"performedAt\":\"2026-05-25T10:05:30Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseEntries[0].sets[0].clientId").value(CLIENT_ID))
                .andExpect(jsonPath("$.exerciseEntries[0].sets[0].performedAt").value("2026-05-25T10:05:30Z"));
    }

    // The set is what matters. A time from a wrong device clock does not keep
    // it from being logged.
    @Test
    void logSet_withATimeOutsideTheBounds_isNotRefused() throws Exception {
        for (String outside : List.of("1999-12-31T23:59:59Z", Instant.now().plus(Duration.ofHours(73)).toString())) {
            when(workoutSessionService.logSet(USER_ID, SESSION_ID, ENTRY_ID,
                    new LogSetRequest(5, null, null, null, Instant.parse(outside))))
                    .thenReturn(sampleSession());

            mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/entries/{entryId}/sets",
                            USER_ID, SESSION_ID, ENTRY_ID)
                            .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reps\":5,\"performedAt\":\"" + outside + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(SESSION_ID));
        }
    }

    @Test
    void logSet_withATimeAFewHoursAhead_isAccepted() throws Exception {
        when(workoutSessionService.logSet(eq(USER_ID), eq(SESSION_ID), eq(ENTRY_ID), any()))
                .thenReturn(sampleSession());

        mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/entries/{entryId}/sets",
                        USER_ID, SESSION_ID, ENTRY_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reps\":5,\"performedAt\":\"" + Instant.now().plus(Duration.ofHours(23)) + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void logSet_withAClientIdThatIsNotAUuid_returns400UnderTheField() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/entries/{entryId}/sets",
                        USER_ID, SESSION_ID, ENTRY_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reps\":5,\"clientId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.clientId").exists());

        verifyNoInteractions(workoutSessionService);
    }

    // The four tests below send the client id in upper case through request
    // validation. A pattern that took lower case only would turn away every
    // request from the Apple apps.
    @Test
    void createSession_withAnUpperCaseClientId_isAccepted() throws Exception {
        when(workoutSessionService.createSession(USER_ID, new CreateWorkoutSessionRequest(
                Instant.parse("2026-05-25T10:00:00Z"), null, null, UPPER_CASE_CLIENT_ID)))
                .thenReturn(sampleSession());

        mockMvc.perform(post("/api/users/{userId}/workout-sessions", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"2026-05-25T10:00:00Z\",\"clientId\":\""
                                + UPPER_CASE_CLIENT_ID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(SESSION_ID));
    }

    @Test
    void addExerciseEntry_withAnUpperCaseClientId_isAccepted() throws Exception {
        when(workoutSessionService.addExerciseEntry(USER_ID, SESSION_ID,
                new AddExerciseEntryRequest(50L, UPPER_CASE_CLIENT_ID)))
                .thenReturn(sampleSession());

        mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/entries",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":50,\"clientId\":\"" + UPPER_CASE_CLIENT_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID));
    }

    @Test
    void logSet_withAnUpperCaseClientId_isAccepted() throws Exception {
        when(workoutSessionService.logSet(USER_ID, SESSION_ID, ENTRY_ID,
                new LogSetRequest(5, null, null, UPPER_CASE_CLIENT_ID, null)))
                .thenReturn(sampleSession());

        mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/entries/{entryId}/sets",
                        USER_ID, SESSION_ID, ENTRY_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reps\":5,\"clientId\":\"" + UPPER_CASE_CLIENT_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID));
    }

    @Test
    void finishSession_withAnUpperCaseClientId_isAccepted() throws Exception {
        when(workoutSessionService.finishSession(USER_ID, SESSION_ID,
                new FinishSessionRequest(UPPER_CASE_CLIENT_ID, null)))
                .thenReturn(sampleSession());

        mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/finish",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + UPPER_CASE_CLIENT_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID));
    }

    @Test
    void updateNotes_validOwnership_returns200WithUpdatedSession() throws Exception {
        WorkoutSessionResponse updated = new WorkoutSessionResponse(
                SESSION_ID, USER_ID, PLAN_ID,
                Instant.parse("2026-05-25T10:00:00Z"), null,
                "Left shoulder felt tight", List.of());
        when(workoutSessionService.updateNotes(eq(USER_ID), eq(SESSION_ID), any()))
                .thenReturn(updated);

        mockMvc.perform(put("/api/users/{userId}/workout-sessions/{sessionId}/notes",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Left shoulder felt tight\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Left shoulder felt tight"));
    }

    @Test
    void updateNotes_longerThan5000Characters_returns400AndSavesNothing() throws Exception {
        String tooLong = "a".repeat(5001);

        mockMvc.perform(put("/api/users/{userId}/workout-sessions/{sessionId}/notes",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(workoutSessionService);
    }

    @Test
    void updateNotes_anotherUsersPath_returns403AndSavesNothing() throws Exception {
        mockMvc.perform(put("/api/users/{userId}/workout-sessions/{sessionId}/notes",
                        USER_ID + 1, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"x\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(workoutSessionService);
    }

    @Test
    void deleteSession_validOwnership_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/{userId}/workout-sessions/{sessionId}",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isNoContent());

        verify(workoutSessionService).deleteSession(USER_ID, SESSION_ID);
    }

    @Test
    void addExerciseEntry_validOwnership_returns200WithUpdatedSession() throws Exception {
        when(workoutSessionService.addExerciseEntry(eq(USER_ID), eq(SESSION_ID), any()))
                .thenReturn(sampleSession());

        mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/entries",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADD_ENTRY_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID));
    }

    @Test
    void removeExerciseEntry_validOwnership_returns200WithUpdatedSession() throws Exception {
        // Nested DELETE intentionally returns 200 with body (not 204). The
        // controller returns the updated session so the UI can re-render the
        // entry list without a second fetch.
        when(workoutSessionService.removeExerciseEntry(USER_ID, SESSION_ID, ENTRY_ID))
                .thenReturn(sampleSession());

        mockMvc.perform(delete(
                        "/api/users/{userId}/workout-sessions/{sessionId}/entries/{entryId}",
                        USER_ID, SESSION_ID, ENTRY_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID));
    }

    @Test
    void getSession_ownershipMismatch_returns403() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/workout-sessions/{sessionId}",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", OTHER_USER_ID))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(workoutSessionService);
    }

    @Test
    void getSession_notFound_returns404() throws Exception {
        when(workoutSessionService.getSession(USER_ID, SESSION_ID))
                .thenThrow(new WorkoutSessionNotFoundException(SESSION_ID));

        mockMvc.perform(get("/api/users/{userId}/workout-sessions/{sessionId}",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workout Session Not Found"));
    }

    private WorkoutSessionResponse sampleSession() {
        return new WorkoutSessionResponse(
                SESSION_ID, USER_ID, PLAN_ID,
                Instant.parse("2026-05-25T10:00:00Z"),
                null,
                "Test session",
                List.of());
    }
}