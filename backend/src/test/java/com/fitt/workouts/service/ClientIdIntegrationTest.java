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
import com.fitt.workouts.service.exception.OpenSessionExistsException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
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

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    // A phone and a watch start at the same moment, each with its own client id.
    // The first start is held inside its transaction until the second one waits
    // on the one-open-session key. So the second start always passes its own
    // check, is refused by the database, and finds no session under its own id.
    // It must be told which session is open. It must not get the other device's
    // session back as if it had made it, and it must not fail with a 500.
    @Test
    void aStartWithItsOwnId_refusedByTheOpenSessionKey_isToldWhichSessionIsOpen() throws Exception {
        long userId = 7441L;
        String phoneId = UUID.randomUUID().toString();
        String watchId = UUID.randomUUID().toString();
        List<Future<WorkoutSessionResponse>> watchStart = new ArrayList<>();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Long phoneSessionId = new TransactionTemplate(transactionManager).execute(status -> {
                WorkoutSessionEntity phoneSession = new WorkoutSessionEntity(userId, Instant.now());
                phoneSession.setClientId(phoneId);
                Long id = sessionRepository.saveAndFlush(phoneSession).getId();
                watchStart.add(pool.submit(() -> service.createSession(userId,
                        new CreateWorkoutSessionRequest(Instant.now(), null, null, watchId))));
                waitUntilAnInsertWaitsForALock();
                return id;
            });

            assertThatThrownBy(() -> watchStart.getFirst().get(30, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOf(OpenSessionExistsException.class)
                    .extracting(ex -> ((OpenSessionExistsException) ex).getOpenSessionId())
                    .isEqualTo(phoneSessionId);
            assertThat(jdbc.queryForList("select client_id from workout_sessions where owner_user_id = ?",
                    String.class, userId)).containsExactly(phoneId);
        } finally {
            pool.shutdown();
        }
    }

    // The same two starts with nothing held back. Which of them wins, and whether
    // the other is stopped by its own check or by the database, depends on
    // timing. Every round must end the same: one session, and the other start
    // told its id.
    @Test
    void twoStartsAtOnceWithDifferentIds_leaveOneSession_andTheOtherIsToldItsId() throws Exception {
        for (long userId = 7451L; userId <= 7460L; userId++) {
            long owner = userId;
            CyclicBarrier together = new CyclicBarrier(COPIES);
            Callable<WorkoutSessionResponse> start = () -> {
                together.await(10, TimeUnit.SECONDS);
                return service.createSession(owner, new CreateWorkoutSessionRequest(
                        Instant.now(), null, null, UUID.randomUUID().toString()));
            };

            List<WorkoutSessionResponse> created = new ArrayList<>();
            List<Throwable> refused = new ArrayList<>();
            ExecutorService pool = Executors.newFixedThreadPool(COPIES);
            try {
                for (Future<WorkoutSessionResponse> result : pool.invokeAll(List.of(start, start))) {
                    try {
                        created.add(result.get());
                    } catch (ExecutionException ex) {
                        refused.add(ex.getCause());
                    }
                }
            } finally {
                pool.shutdown();
            }

            assertThat(created).hasSize(1);
            assertThat(refused).singleElement()
                    .isInstanceOf(OpenSessionExistsException.class)
                    .extracting(ex -> ((OpenSessionExistsException) ex).getOpenSessionId())
                    .isEqualTo(created.getFirst().id());
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

    // Returns once some statement on the server waits for a row lock, which here
    // can only be the second start's insert waiting on the first one's row. Reads
    // the server's transaction list as root, because the test user may not see
    // other connections. The container sets root's password to the test user's.
    private void waitUntilAnInsertWaitsForALock() {
        Properties root = new Properties();
        root.setProperty("user", "root");
        root.setProperty("password", mysql.getPassword());
        root.setProperty("allowPublicKeyRetrieval", "true");
        try (Connection connection = DriverManager.getConnection(mysql.getJdbcUrl(), root);
             PreparedStatement waiting = connection.prepareStatement(
                     "select count(*) from information_schema.innodb_trx where trx_state = 'LOCK WAIT'")) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < deadline) {
                try (ResultSet row = waiting.executeQuery()) {
                    row.next();
                    if (row.getInt(1) > 0) {
                        return;
                    }
                }
                Thread.sleep(20);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
        throw new AssertionError("The second start never waited on the first one's row");
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
