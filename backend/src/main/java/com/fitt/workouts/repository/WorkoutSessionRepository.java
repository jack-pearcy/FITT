package com.fitt.workouts.repository;

import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSessionEntity, Long> {

    // All sessions for one user, used for session history.
    List<WorkoutSessionEntity> findByOwnerUserId(Long ownerUserId);

    List<WorkoutSessionEntity> findByOwnerUserIdOrderByStartedAtDesc(Long ownerUserId);

    // Counts sessions for a specific plan that have been finished.
    // Used to determine which day is next in the plan rotation.
    long countByOwnerUserIdAndPlanIdAndFinishedAtIsNotNull(Long ownerUserId, Long planId);

    // The most recently finished session of a plan. Its plan day says where the
    // rotation stands.
    Optional<WorkoutSessionEntity> findFirstByOwnerUserIdAndPlanIdAndFinishedAtIsNotNullOrderByFinishedAtDesc(
            Long ownerUserId, Long planId);

    // The user's unfinished session. V12 allows only one, so the order only picks
    // a stable row if that rule is ever broken.
    Optional<WorkoutSessionEntity> findFirstByOwnerUserIdAndFinishedAtIsNullOrderByStartedAtDescIdDesc(
            Long ownerUserId);

    // The session a start with this client id already made, finished or not.
    Optional<WorkoutSessionEntity> findByOwnerUserIdAndClientId(Long ownerUserId, String clientId);

    // Ownership-safe single session fetch. Returns empty if the session exists
    // but belongs to a different user, prevents cross-user data access.
    Optional<WorkoutSessionEntity> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    // One session, locked for update. Every change to a session's entries and sets
    // reads it this way first, so two requests for the same session queue up and the
    // second numbers its new row after the first has committed. Without the lock
    // both read the same highest number and both insert the next one.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM WorkoutSessionEntity s WHERE s.id = :id")
    Optional<WorkoutSessionEntity> findByIdForUpdate(@Param("id") Long id);

    // Cheaper ownership check when we only need to verify access, not load the entity.
    boolean existsByIdAndOwnerUserId(Long id, Long ownerUserId);

    // Completed sessions where a specific exercise was performed, most recent first.
    // Used by the progression algorithm to analyze recent performance.
    // DISTINCT because a session could contain the same exercise twice.
    @Query("""
            SELECT DISTINCT s FROM WorkoutSessionEntity s
            JOIN s.exerciseEntries e
            WHERE s.ownerUserId = :ownerUserId
              AND e.exercise.id = :exerciseId
              AND s.finishedAt IS NOT NULL
            ORDER BY s.startedAt DESC
            """)
    List<WorkoutSessionEntity> findCompletedSessionsByExercise(
            @Param("ownerUserId") Long ownerUserId,
            @Param("exerciseId") Long exerciseId,
            Pageable pageable);

    // True when the user finished a session in the given half-open window, used for
    // the already-trained-today check. The range is start inclusive, end exclusive.
    boolean existsByOwnerUserIdAndFinishedAtGreaterThanEqualAndFinishedAtLessThan(
            Long ownerUserId, Instant start, Instant end);
}