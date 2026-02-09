package com.fitt.workouts.service.impl;

import com.fitt.workouts.persistence.entity.WorkoutEntity;
import com.fitt.workouts.repository.WorkoutRepository;
import com.fitt.workouts.service.WorkoutService;
import com.fitt.workouts.service.exception.WorkoutNotFoundException;
import com.fitt.workouts.service.model.WorkoutView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Service layer: business rules, transaction boundary.
// Controller shouldn't touch repos directly.
@Service
@Transactional
public class WorkoutServiceImpl implements WorkoutService {

    private final WorkoutRepository workoutRepository;

    public WorkoutServiceImpl(WorkoutRepository workoutRepository) {
        this.workoutRepository = workoutRepository;
    }

    @Override
    public WorkoutView createWorkout(Long userId, String name) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("workout name is required");
        }

        WorkoutEntity saved = workoutRepository.save(new WorkoutEntity(userId, name.trim()));

        return new WorkoutView(
                saved.getId(),
                saved.getName(),
                saved.getCreatedAt(),
                saved.getUpdatedAt()
        );
    }

    @Override
    public WorkoutView getWorkoutById(Long userId, Long workoutId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (workoutId == null) {
            throw new IllegalArgumentException("workoutId is required");
        }

        WorkoutEntity workout = workoutRepository
                .findByIdAndOwnerUserId(workoutId, userId)
                .orElseThrow(() -> new WorkoutNotFoundException("workout not found"));

        return new WorkoutView(
                workout.getId(),
                workout.getName(),
                workout.getCreatedAt(),
                workout.getUpdatedAt()
        );
    }

    @Override
    public void deleteWorkout(Long userId, Long workoutId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (workoutId == null) {
            throw new IllegalArgumentException("workoutId is required");
        }

        // Ownership-safe delete: load by (id, owner) and delete what we actually own.
        WorkoutEntity ownedWorkout = workoutRepository
                .findByIdAndOwnerUserId(workoutId, userId)
                .orElseThrow(() -> new WorkoutNotFoundException("workout not found"));

        workoutRepository.delete(ownedWorkout);
    }
}
