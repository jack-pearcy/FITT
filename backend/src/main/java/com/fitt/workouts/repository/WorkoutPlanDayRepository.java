package com.fitt.workouts.repository;

import com.fitt.workouts.persistence.entity.WorkoutPlanDayEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkoutPlanDayRepository extends JpaRepository<WorkoutPlanDayEntity, Long> {

    // All days for a plan in display order.
    List<WorkoutPlanDayEntity> findByWorkoutPlan_IdOrderByOrderIndex(Long workoutPlanId);
}