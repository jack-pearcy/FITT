package com.fitt.workouts.repository;

import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSessionEntity, Long> {

    // All sessions for one user, used for session history.
    List<WorkoutSessionEntity> findByOwnerUserId(Long ownerUserId);

    List<WorkoutSessionEntity> findByOwnerUserIdOrderByStartedAtDesc(Long ownerUserId);

    // Ownership-safe single session fetch. Returns empty if the session exists
    // but belongs to a different user, prevents cross-user data access.
    Optional<WorkoutSessionEntity> findByIdAndOwnerUserId(Long id, Long ownerUserId);

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
}