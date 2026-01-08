package com.fitt.workouts.service.impl;

import com.fitt.workouts.persistence.entity.WorkoutEntity;
import com.fitt.workouts.repository.WorkoutRepository;
import com.fitt.workouts.service.WorkoutService;
import com.fitt.workouts.service.model.WorkoutView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


// Service layer, business rules and transaction boundary.
// Repos are injected here because services orchestrate use-cases.
@Service
@Transactional
public class WorkoutServiceImpl implements WorkoutService {

    private final WorkoutRepository workoutRepository;

    public WorkoutServiceImpl(WorkoutRepository workoutRepository) {
        this.workoutRepository = workoutRepository;
    }

    @Override
    public void createWorkout(Long userId, String name) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("workout name is required");
        }

        WorkoutEntity workout = new WorkoutEntity(userId, name.trim());
        workoutRepository.save(workout);
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
                .orElseThrow(() -> new IllegalArgumentException("workout not found"));

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

        // No CRUD by id alone.
        boolean owned = workoutRepository.existsByIdAndOwnerUserId(workoutId, userId);
        if (!owned) {
            throw new IllegalArgumentException("workout not found");
        }

        workoutRepository.deleteById(workoutId);
    }
}
