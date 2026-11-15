package com.fitt.workouts.repository;

import com.fitt.workouts.persistence.entity.WorkoutPlanEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlanEntity, Long> {

    // All plans for one user.
    List<WorkoutPlanEntity> findByOwnerUserId(Long ownerUserId);

    // All of a user's plans, locked for update. activatePlan uses this so two
    // concurrent activations queue up instead of both leaving a plan active.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM WorkoutPlanEntity p WHERE p.ownerUserId = :ownerUserId")
    List<WorkoutPlanEntity> findByOwnerUserIdForUpdate(@Param("ownerUserId") Long ownerUserId);

    // Ownership-safe single plan fetch.
    Optional<WorkoutPlanEntity> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    // Cheaper ownership check when loading is not needed.
    boolean existsByIdAndOwnerUserId(Long id, Long ownerUserId);

    // The user's currently active plan. Optional because a user may have no active plan yet.
    Optional<WorkoutPlanEntity> findByOwnerUserIdAndIsActiveTrue(Long ownerUserId);
}