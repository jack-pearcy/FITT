package com.fitt.workouts.repository;

import com.fitt.workouts.dto.WorkoutPlanResponse;
import com.fitt.workouts.persistence.entity.WorkoutPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlanEntity, Long> {

    // All plans for one user.
    List<WorkoutPlanEntity> findByOwnerUserId(Long ownerUserId);

    // Ownership-safe single plan fetch.
    Optional<WorkoutPlanEntity> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    // Cheaper ownership check when loading is not needed.
    boolean existsByIdAndOwnerUserId(Long id, Long ownerUserId);

    // The user's currently active plan. Optional because a user may have no active plan.
    Optional<WorkoutPlanEntity> findByOwnerUserIdAndIsActiveTrue(Long ownerUserId);

    List<WorkoutPlanResponse> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

}
