package com.fitt.workouts.service;

import com.fitt.workouts.service.model.WorkoutView;

import java.util.List;

public interface WorkoutService {

    WorkoutView createWorkout(Long userId, String name);

    WorkoutView getWorkoutById(Long userId, Long workoutId);

    List<WorkoutView> getWorkouts(Long userId);

    void deleteWorkout(Long userId, Long workoutId);
}

