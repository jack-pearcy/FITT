package com.fitt.workouts.repository;

import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.persistence.entity.ExerciseEntryEntity;
import com.fitt.workouts.persistence.entity.SetEntryEntity;
import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.MySQLContainer;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for the @Query method against a real MySQL container.
// H2 with MySQL mode is close to MySQL but not identical, especially around
// DATETIME(6) precision. Pinning the query against real MySQL is the point.
@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers
@Transactional
class WorkoutSessionRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private WorkoutSessionRepository sessionRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;

    @Test
    void findCompletedSessionsByExercise_returnsOnlyCompletedMatchingSessions() {
        ExerciseEntity squat = exercise("Back Squat");
        ExerciseEntity bench = exercise("Bench Press");

        completedSession(USER_ID, squat, Instant.parse("2026-05-01T10:00:00Z"), Instant.parse("2026-05-01T11:00:00Z"));
        completedSession(USER_ID, bench, Instant.parse("2026-05-02T10:00:00Z"), Instant.parse("2026-05-02T11:00:00Z"));
        inProgressSession(USER_ID, squat, Instant.parse("2026-05-03T10:00:00Z"));
        flush();

        List<WorkoutSessionEntity> results = sessionRepository.findCompletedSessionsByExercise(
                USER_ID, squat.getId(), PageRequest.of(0, 10));

        // Only the completed squat session matches. Bench is the wrong exercise,
        // the third session has finishedAt = null.
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getStartedAt()).isEqualTo(Instant.parse("2026-05-01T10:00:00Z"));
    }

    @Test
    void findCompletedSessionsByExercise_filtersOutOtherUsers() {
        ExerciseEntity squat = exercise("Back Squat");

        completedSession(USER_ID, squat, Instant.parse("2026-05-01T10:00:00Z"), Instant.parse("2026-05-01T11:00:00Z"));
        completedSession(OTHER_USER_ID, squat, Instant.parse("2026-05-01T12:00:00Z"), Instant.parse("2026-05-01T13:00:00Z"));
        flush();

        List<WorkoutSessionEntity> results = sessionRepository.findCompletedSessionsByExercise(
                USER_ID, squat.getId(), PageRequest.of(0, 10));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getOwnerUserId()).isEqualTo(USER_ID);
    }

    @Test
    void findCompletedSessionsByExercise_ordersByStartedAtDescending() {
        ExerciseEntity squat = exercise("Back Squat");

        completedSession(USER_ID, squat, Instant.parse("2026-05-01T10:00:00Z"), Instant.parse("2026-05-01T11:00:00Z"));
        completedSession(USER_ID, squat, Instant.parse("2026-05-03T10:00:00Z"), Instant.parse("2026-05-03T11:00:00Z"));
        completedSession(USER_ID, squat, Instant.parse("2026-05-02T10:00:00Z"), Instant.parse("2026-05-02T11:00:00Z"));
        flush();

        List<WorkoutSessionEntity> results = sessionRepository.findCompletedSessionsByExercise(
                USER_ID, squat.getId(), PageRequest.of(0, 10));

        // Most recent startedAt first, regardless of insertion order.
        assertThat(results).extracting(WorkoutSessionEntity::getStartedAt).containsExactly(
                Instant.parse("2026-05-03T10:00:00Z"),
                Instant.parse("2026-05-02T10:00:00Z"),
                Instant.parse("2026-05-01T10:00:00Z"));
    }

    private ExerciseEntity exercise(String name) {
        return exerciseRepository.saveAndFlush(new ExerciseEntity(USER_ID, name, "Legs", false));
    }

    private void completedSession(long userId, ExerciseEntity exercise, Instant startedAt, Instant finishedAt) {
        WorkoutSessionEntity session = new WorkoutSessionEntity(userId, startedAt);
        session.setFinishedAt(finishedAt);
        ExerciseEntryEntity entry = new ExerciseEntryEntity(exercise, 0);
        entry.addSetEntry(new SetEntryEntity(1, new BigDecimal("100"), 10, null));
        session.addExerciseEntry(entry);
        sessionRepository.saveAndFlush(session);
    }

    private void inProgressSession(long userId, ExerciseEntity exercise, Instant startedAt) {
        WorkoutSessionEntity session = new WorkoutSessionEntity(userId, startedAt);
        ExerciseEntryEntity entry = new ExerciseEntryEntity(exercise, 0);
        entry.addSetEntry(new SetEntryEntity(1, new BigDecimal("100"), 10, null));
        session.addExerciseEntry(entry);
        sessionRepository.saveAndFlush(session);
    }

    // JPQL queries bypass the persistence context, so flush before querying
    // to make sure the writes above are visible to the query below.
    private void flush() {
        entityManager.flush();
    }
}