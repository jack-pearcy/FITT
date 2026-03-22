package com.fitt.workouts.service;

import com.fitt.workouts.domain.MuscleGroup;
import com.fitt.workouts.dto.CreateExerciseRequest;
import com.fitt.workouts.dto.ExerciseResponse;
import java.util.List;

public interface ExerciseService {

    List<ExerciseResponse> listExercises(Long ownerUserId, MuscleGroup muscleGroup);

    ExerciseResponse createCustomExercise(Long ownerUserId, CreateExerciseRequest request);

    void deleteCustomExercise(Long ownerUserId, Long exerciseId);
}