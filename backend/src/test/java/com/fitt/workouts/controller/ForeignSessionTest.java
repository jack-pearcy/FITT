package com.fitt.workouts.controller;

import com.fitt.workouts.dto.AddExerciseEntryRequest;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.repository.ExerciseRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A signed-in user who names another user's session under their own path gets
// 404, the same answer as for an id that does not exist, and nothing changes.
// The whole stack runs, real service and real database, because the answer
// depends on the query the service uses and on how the error is mapped.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ForeignSessionTest {

    private static final long CALLER_ID = 7401L;
    private static final long OWNER_ID = 7402L;
    private static final long NO_SUCH_SESSION = 987_654_321L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkoutSessionService service;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long foreignSessionId;
    private long foreignEntryId;
    private long exerciseId;

    @BeforeEach
    void theOtherUserHasAnOpenSessionWithOneExercise() {
        // One open session per user, so a session left by an earlier test is reused.
        ExerciseEntity exercise = exerciseRepository.save(
                new ExerciseEntity(null, "Foreign Bench " + UUID.randomUUID(), "CHEST", false));
        exerciseId = exercise.getId();
        WorkoutSessionResponse session = service.getOpenSession(OWNER_ID)
                .orElseGet(() -> service.createSession(OWNER_ID, new CreateWorkoutSessionRequest(
                        Instant.parse("2026-11-20T10:00:00Z"), null, "Theirs")));
        foreignSessionId = session.id();
        foreignEntryId = service.addExerciseEntry(OWNER_ID, foreignSessionId, new AddExerciseEntryRequest(exerciseId))
                .exerciseEntries().get(0).id();
    }

    @Test
    void finish_onAnotherUsersSession_returns404_andLeavesItOpen() throws Exception {
        expectNotFound(post("/api/users/{userId}/workout-sessions/{sessionId}/finish", CALLER_ID, foreignSessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
        expectNotFound(post("/api/users/{userId}/workout-sessions/{sessionId}/finish", CALLER_ID, foreignSessionId));

        assertThat(service.getSession(OWNER_ID, foreignSessionId).finishedAt()).isNull();
    }

    @Test
    void addExercise_toAnotherUsersSession_returns404_andAddsNothing() throws Exception {
        int before = entryCount();

        expectNotFound(post("/api/users/{userId}/workout-sessions/{sessionId}/entries", CALLER_ID, foreignSessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"exerciseId\":" + exerciseId + ",\"clientId\":\"" + UUID.randomUUID() + "\"}"));

        assertThat(entryCount()).isEqualTo(before);
    }

    @Test
    void logSet_inAnotherUsersSession_returns404_andLogsNothing() throws Exception {
        expectNotFound(post("/api/users/{userId}/workout-sessions/{sessionId}/entries/{entryId}/sets",
                CALLER_ID, foreignSessionId, foreignEntryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reps\":5,\"clientId\":\"" + UUID.randomUUID() + "\"}"));

        assertThat(jdbc.queryForObject("select count(*) from set_entries where exercise_entry_id = ?",
                Integer.class, foreignEntryId)).isZero();
    }

    // The other session routes share the same lookup, so they answer the same way.
    @Test
    void read_notes_andDeletes_onAnotherUsersSession_return404_andChangeNothing() throws Exception {
        int before = entryCount();

        expectNotFound(get("/api/users/{userId}/workout-sessions/{sessionId}", CALLER_ID, foreignSessionId));
        expectNotFound(put("/api/users/{userId}/workout-sessions/{sessionId}/notes", CALLER_ID, foreignSessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"Mine now\"}"));
        expectNotFound(delete("/api/users/{userId}/workout-sessions/{sessionId}/entries/{entryId}",
                CALLER_ID, foreignSessionId, foreignEntryId));
        expectNotFound(delete("/api/users/{userId}/workout-sessions/{sessionId}/entries/{entryId}/sets/{setId}",
                CALLER_ID, foreignSessionId, foreignEntryId, 1L));
        expectNotFound(delete("/api/users/{userId}/workout-sessions/{sessionId}", CALLER_ID, foreignSessionId));

        WorkoutSessionResponse theirs = service.getSession(OWNER_ID, foreignSessionId);
        assertThat(theirs.notes()).isEqualTo("Theirs");
        assertThat(entryCount()).isEqualTo(before);
    }

    // The answer for a session id nobody has, to show the two cases read alike.
    @Test
    void aSessionIdThatDoesNotExist_returnsTheSame404() throws Exception {
        expectNotFound(post("/api/users/{userId}/workout-sessions/{sessionId}/finish", CALLER_ID, NO_SUCH_SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    private void expectNotFound(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(jwt().jwt(token -> token.claim("uid", CALLER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workout Session Not Found"));
    }

    private int entryCount() {
        return jdbc.queryForObject("select count(*) from exercise_entries where workout_session_id = ?",
                Integer.class, foreignSessionId);
    }
}
