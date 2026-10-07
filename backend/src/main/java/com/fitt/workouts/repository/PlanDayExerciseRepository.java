package com.fitt.workouts.repository;

import com.fitt.workouts.persistence.entity.PlanDayExerciseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanDayExerciseRepository extends JpaRepository<PlanDayExerciseEntity, Long> {

    // All exercises for a plan day in display order.
    List<PlanDayExerciseEntity> findByWorkoutPlanDay_IdOrderByOrderIndex(Long workoutPlanDayId);

    // True when any plan day references this exercise. Blocks deleting one in use.
    boolean existsByExercise_Id(Long exerciseId);
}
