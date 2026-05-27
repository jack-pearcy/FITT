package com.fitt.workouts.controller;

import com.fitt.workouts.dto.WorkoutSessionResponse;
import com.fitt.workouts.service.WorkoutSessionService;
import com.fitt.workouts.service.exception.WorkoutSessionNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        when(workoutSessionService.finishSession(USER_ID, SESSION_ID))
                .thenReturn(finished);

        mockMvc.perform(post("/api/users/{userId}/workout-sessions/{sessionId}/finish",
                        USER_ID, SESSION_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finishedAt").value("2026-05-25T11:00:00Z"));
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