package com.fitt.workouts.repository;

import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
