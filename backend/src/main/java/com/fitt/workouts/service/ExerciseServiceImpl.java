package com.fitt.workouts.service;

import com.fitt.workouts.domain.MuscleGroup;
import com.fitt.workouts.dto.CreateExerciseRequest;
import com.fitt.workouts.dto.ExerciseResponse;
import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.repository.ExerciseRepository;
import com.fitt.workouts.service.exception.ExerciseNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ExerciseServiceImpl implements ExerciseService {

    private final ExerciseRepository exerciseRepository;

    public ExerciseServiceImpl(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }

    @Override
    public List<ExerciseResponse> listExercises(Long ownerUserId, MuscleGroup muscleGroup) {
        // pull global library (ownerUserId null) + this user's custom exercises
        List<ExerciseEntity> all = exerciseRepository.findByOwnerUserIdIsNullOrOwnerUserId(ownerUserId);

        return all.stream()
                .filter(e -> muscleGroup == null || e.getMuscleGroup().equals(muscleGroup.name()))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ExerciseResponse createCustomExercise(Long ownerUserId, CreateExerciseRequest request) {
        // entity requires all fields at construction time, no public setters for these
        ExerciseEntity exercise = new ExerciseEntity(
                ownerUserId,
                request.name(),
                request.muscleGroup().name(),
                true
        );
        return toResponse(exerciseRepository.save(exercise));
    }

    @Override
    @Transactional
    public void deleteCustomExercise(Long ownerUserId, Long exerciseId) {
        ExerciseEntity exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new ExerciseNotFoundException(exerciseId));

        if (!exercise.isCustom()) {
            throw new IllegalArgumentException("Fixed library exercises cannot be deleted");
        }

        if (!ownerUserId.equals(exercise.getOwnerUserId())) {
            throw new AccessDeniedException("You do not own this exercise");
        }

        exerciseRepository.delete(exercise);
    }

    private ExerciseResponse toResponse(ExerciseEntity entity) {
        return new ExerciseResponse(
                entity.getId(),
                entity.getName(),
                MuscleGroup.valueOf(entity.getMuscleGroup()),
                entity.isCustom()
        );
    }
}