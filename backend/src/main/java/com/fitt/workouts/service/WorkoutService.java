package com.fitt.workouts.service;

import com.fitt.workouts.service.model.WorkoutView;


public interface WorkoutService {

    WorkoutView createWorkout(Long userId, String name);

    WorkoutView getWorkoutById(Long userId, Long workoutId);

    void deleteWorkout(Long userId, Long workoutId);
}


