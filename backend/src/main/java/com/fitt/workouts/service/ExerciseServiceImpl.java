package com.fitt.workouts.service;

import com.fitt.workouts.domain.MuscleGroup;
import com.fitt.workouts.dto.CreateExerciseRequest;
import com.fitt.workouts.dto.ExerciseResponse;
import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.repository.ExerciseRepository;
import com.fitt.workouts.repository.ExerciseEntryRepository;
import com.fitt.workouts.repository.PlanDayExerciseRepository;
import com.fitt.workouts.service.exception.ExerciseInUseException;
import com.fitt.workouts.service.exception.ExerciseNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ExerciseServiceImpl implements ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final PlanDayExerciseRepository planDayExerciseRepository;
    private final ExerciseEntryRepository exerciseEntryRepository;

    public ExerciseServiceImpl(ExerciseRepository exerciseRepository,
                               PlanDayExerciseRepository planDayExerciseRepository,
                               ExerciseEntryRepository exerciseEntryRepository) {
        this.exerciseRepository = exerciseRepository;
        this.planDayExerciseRepository = planDayExerciseRepository;
        this.exerciseEntryRepository = exerciseEntryRepository;
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

        // A referenced exercise cannot be removed without breaking the plan or session
        // rows that point at it, and the foreign keys would reject the delete anyway.
        // Fail with a clear conflict instead of a database error.
        if (planDayExerciseRepository.existsByExercise_Id(exerciseId)
                || exerciseEntryRepository.existsByExercise_Id(exerciseId)) {
            throw new ExerciseInUseException(
                    "This exercise is used in a plan or a logged session and cannot be deleted");
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