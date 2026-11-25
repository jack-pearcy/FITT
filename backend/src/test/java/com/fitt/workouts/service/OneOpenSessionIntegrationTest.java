package com.fitt.workouts.service;

import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// One open session per user, against the schema the migrations build on MySQL 8.4.
// The other tests let Hibernate create the tables, and those tables lack the
// unique key on open_owner_id, so only this class shows the database guard.
// No test-level transaction, so each service call commits by itself.
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@ActiveProfiles("integration")
@Testcontainers
@Tag("integration")
class OneOpenSessionIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private WorkoutSessionService service;

    @Autowired
    private WorkoutSessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoStartsAtOnce_leaveOneOpenSession_andTheOtherIsToldItsId() throws Exception {
        long userId = 501L;
        int starts = 2;
        CyclicBarrier together = new CyclicBarrier(starts);
        Callable<WorkoutSessionResponse> start = () -> {
            together.await(10, TimeUnit.SECONDS);
            return service.createSession(userId, new CreateWorkoutSessionRequest(Instant.now(), null, null));
        };

        List<WorkoutSessionResponse> created = new ArrayList<>();
        List<OpenSessionExistsException> refused = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(starts);
        try {
            for (Future<WorkoutSessionResponse> result : pool.invokeAll(List.of(start, start))) {
                try {
                    created.add(result.get());
                } catch (ExecutionException ex) {
                    assertThat(ex.getCause()).isInstanceOf(OpenSessionExistsException.class);
                    refused.add((OpenSessionExistsException) ex.getCause());
                }
            }
        } finally {
            pool.shutdown();
        }

        assertThat(created).hasSize(1);
        assertThat(refused).hasSize(1);
        assertThat(refused.getFirst().getOpenSessionId()).isEqualTo(created.getFirst().id());
        assertThat(openSessionCount(userId)).isEqualTo(1);
    }

    @Test
    void theDatabase_refusesASecondOpenSession_butKeepsAnyNumberOfFinishedOnes() {
        long userId = 502L;
        WorkoutSessionEntity first = new WorkoutSessionEntity(userId, Instant.parse("2026-05-01T10:00:00Z"));
        first.setFinishedAt(Instant.parse("2026-05-01T11:00:00Z"));
        sessionRepository.saveAndFlush(first);
        WorkoutSessionEntity second = new WorkoutSessionEntity(userId, Instant.parse("2026-05-02T10:00:00Z"));
        second.setFinishedAt(Instant.parse("2026-05-02T11:00:00Z"));
        sessionRepository.saveAndFlush(second);
        sessionRepository.saveAndFlush(new WorkoutSessionEntity(userId, Instant.parse("2026-05-03T10:00:00Z")));

        assertThatThrownBy(() -> sessionRepository.saveAndFlush(
                new WorkoutSessionEntity(userId, Instant.parse("2026-05-04T10:00:00Z"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(openSessionCount(userId)).isEqualTo(1);
    }

    @Test
    void afterFinishing_theUserCanStartAgain() {
        long userId = 503L;
        WorkoutSessionResponse first = service.createSession(
                userId, new CreateWorkoutSessionRequest(Instant.now().minusSeconds(3600), null, null));
        service.finishSession(userId, first.id());

        WorkoutSessionResponse second = service.createSession(
                userId, new CreateWorkoutSessionRequest(Instant.now(), null, null));

        assertThat(service.getOpenSession(userId)).map(WorkoutSessionResponse::id).contains(second.id());
    }

    private Integer openSessionCount(long userId) {
        return jdbc.queryForObject("select count(*) from workout_sessions"
                + " where owner_user_id = ? and finished_at is null", Integer.class, userId);
    }
}
