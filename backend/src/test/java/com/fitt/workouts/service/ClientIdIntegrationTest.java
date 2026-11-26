package com.fitt.workouts.service;

import com.fitt.workouts.dto.AddExerciseEntryRequest;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.LogSetRequest;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.persistence.entity.ExerciseEntryEntity;
import com.fitt.workouts.persistence.entity.SetEntryEntity;
import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import com.fitt.workouts.repository.ExerciseRepository;
import com.fitt.workouts.repository.WorkoutSessionRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Client ids against the schema the migrations build on MySQL 8.4: the unique
// keys V13 adds, and two copies of one request arriving at the same moment.
// No test-level transaction, so each service call commits by itself.
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@ActiveProfiles("integration")
@Testcontainers
@Tag("integration")
class ClientIdIntegrationTest {

    private static final int COPIES = 2;

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private WorkoutSessionService service;

    @Autowired
    private WorkoutSessionRepository sessionRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private JdbcTemplate jdbc;

    // Several rounds, each with a fresh user. Whether the loser fails on the
    // unique key or already sees the winner's row depends on timing, and more
    // rounds make it likely that both paths run. Every round must end the same.
    @Test
    void twoCopiesOfOneStartAtOnce_bothGetTheSameSession_andOneRowIsStored() throws Exception {
        for (long userId = 7401L; userId <= 7410L; userId++) {
            long owner = userId;
            CreateWorkoutSessionRequest start = new CreateWorkoutSessionRequest(
                    Instant.now(), null, null, UUID.randomUUID().toString());

            List<WorkoutSessionResponse> answers = atOnce(() -> service.createSession(owner, start));

            assertThat(answers).hasSize(COPIES);
            assertThat(answers.get(1).id()).isEqualTo(answers.get(0).id());
            assertThat(count("select count(*) from workout_sessions where owner_user_id = ?", owner)).isEqualTo(1);
        }
    }

    @Test
    void twoCopiesOfOneAddedExerciseAtOnce_storeOneEntry() throws Exception {
        long userId = 7421L;
        ExerciseEntity bench = exerciseRepository.save(new ExerciseEntity(null, "Client Id Bench", "CHEST", false));
        long sessionId = service.createSession(userId, new CreateWorkoutSessionRequest(Instant.now(), null, null)).id();
        AddExerciseEntryRequest add = new AddExerciseEntryRequest(bench.getId(), UUID.randomUUID().toString());

        List<WorkoutSessionResponse> answers = atOnce(() -> service.addExerciseEntry(userId, sessionId, add));

        assertThat(answers).hasSize(COPIES);
        assertThat(count("select count(*) from exercise_entries where workout_session_id = ?", sessionId)).isEqualTo(1);
    }

    @Test
    void twoCopiesOfOneSetAtOnce_storeOneSet() throws Exception {
        long userId = 7422L;
        ExerciseEntity squat = exerciseRepository.save(new ExerciseEntity(null, "Client Id Squat", "LEGS", false));
        long sessionId = service.createSession(userId, new CreateWorkoutSessionRequest(Instant.now(), null, null)).id();
        long entryId = service.addExerciseEntry(userId, sessionId, new AddExerciseEntryRequest(squat.getId()))
                .exerciseEntries().get(0).id();
        LogSetRequest set = new LogSetRequest(5, new BigDecimal("100.00"), null,
                UUID.randomUUID().toString(), Instant.now());

        List<WorkoutSessionResponse> answers = atOnce(() -> service.logSet(userId, sessionId, entryId, set));

        assertThat(answers).hasSize(COPIES);
        assertThat(count("select count(*) from set_entries where exercise_entry_id = ?", entryId)).isEqualTo(1);
    }

    @Test
    void theDatabase_refusesTwoSessionsOfOneUserWithTheSameClientId() {
        long userId = 7431L;
        String clientId = UUID.randomUUID().toString();
        sessionRepository.saveAndFlush(finishedSession(userId, clientId));

        assertThatThrownBy(() -> sessionRepository.saveAndFlush(finishedSession(userId, clientId)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Another user may hold the same id, and rows with no id never collide.
        sessionRepository.saveAndFlush(finishedSession(7432L, clientId));
        sessionRepository.saveAndFlush(finishedSession(userId, null));
        sessionRepository.saveAndFlush(finishedSession(userId, null));
        assertThat(count("select count(*) from workout_sessions where owner_user_id = ?", userId)).isEqualTo(3);
    }

    @Test
    void theDatabase_refusesTwoEntriesOfOneSessionWithTheSameClientId() {
        ExerciseEntity curl = exerciseRepository.save(new ExerciseEntity(null, "Client Id Curl", "ARMS", false));
        String clientId = UUID.randomUUID().toString();
        WorkoutSessionEntity session = finishedSession(7433L, null);
        session.addExerciseEntry(entry(curl, 0, clientId));
        session.addExerciseEntry(entry(curl, 1, clientId));

        assertThatThrownBy(() -> sessionRepository.saveAndFlush(session))
                .isInstanceOf(DataIntegrityViolationException.class);

        WorkoutSessionEntity withoutIds = finishedSession(7433L, null);
        withoutIds.addExerciseEntry(entry(curl, 0, null));
        withoutIds.addExerciseEntry(entry(curl, 1, null));
        sessionRepository.saveAndFlush(withoutIds);
    }

    @Test
    void theDatabase_refusesTwoSetsOfOneEntryWithTheSameClientId() {
        ExerciseEntity press = exerciseRepository.save(new ExerciseEntity(null, "Client Id Press", "SHOULDERS", false));
        String clientId = UUID.randomUUID().toString();
        WorkoutSessionEntity session = finishedSession(7434L, null);
        ExerciseEntryEntity entry = entry(press, 0, null);
        session.addExerciseEntry(entry);
        entry.addSetEntry(set(1, clientId));
        entry.addSetEntry(set(2, clientId));

        assertThatThrownBy(() -> sessionRepository.saveAndFlush(session))
                .isInstanceOf(DataIntegrityViolationException.class);

        WorkoutSessionEntity withoutIds = finishedSession(7434L, null);
        ExerciseEntryEntity plain = entry(press, 0, null);
        withoutIds.addExerciseEntry(plain);
        plain.addSetEntry(set(1, null));
        plain.addSetEntry(set(2, null));
        sessionRepository.saveAndFlush(withoutIds);
    }

    // Runs the same call on two threads that start together, and returns both
    // answers. A call that throws fails the test through Future.get.
    private List<WorkoutSessionResponse> atOnce(Callable<WorkoutSessionResponse> call) throws Exception {
        CyclicBarrier together = new CyclicBarrier(COPIES);
        Callable<WorkoutSessionResponse> gated = () -> {
            together.await(10, TimeUnit.SECONDS);
            return call.call();
        };
        List<WorkoutSessionResponse> answers = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(COPIES);
        try {
            for (Future<WorkoutSessionResponse> result : pool.invokeAll(List.of(gated, gated))) {
                answers.add(result.get());
            }
        } finally {
            pool.shutdown();
        }
        return answers;
    }

    // Finished, so the one-open-session key from V12 stays out of these tests.
    private static WorkoutSessionEntity finishedSession(long userId, String clientId) {
        WorkoutSessionEntity session = new WorkoutSessionEntity(userId, Instant.parse("2026-11-20T10:00:00Z"));
        session.setFinishedAt(Instant.parse("2026-11-20T11:00:00Z"));
        session.setClientId(clientId);
        return session;
    }

    private static ExerciseEntryEntity entry(ExerciseEntity exercise, int orderIndex, String clientId) {
        ExerciseEntryEntity entry = new ExerciseEntryEntity(exercise, orderIndex);
        entry.setClientId(clientId);
        return entry;
    }

    private static SetEntryEntity set(int number, String clientId) {
        SetEntryEntity set = new SetEntryEntity(number, new BigDecimal("50.00"), 8, null);
        set.setClientId(clientId);
        return set;
    }

    private Integer count(String sql, long id) {
        return jdbc.queryForObject(sql, Integer.class, id);
    }
}
