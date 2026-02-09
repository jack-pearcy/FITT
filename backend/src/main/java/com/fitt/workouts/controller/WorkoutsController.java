package com.fitt.workouts.controller;

import com.fitt.workouts.dto.CreateWorkoutRequest;
import com.fitt.workouts.dto.WorkoutResponse;
import com.fitt.workouts.service.WorkoutService;
import com.fitt.workouts.service.model.WorkoutView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/users/{userId}/workouts")
public class WorkoutsController {

    private final WorkoutService workoutService;

    public WorkoutsController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    @PostMapping
    public ResponseEntity<WorkoutResponse> create(
            @PathVariable Long userId,
            @RequestBody CreateWorkoutRequest request
    ) {
        WorkoutView created = workoutService.createWorkout(userId, request.getName());

        WorkoutResponse body = new WorkoutResponse(
                created.id(),
                created.name(),
                created.createdAt(),
                created.updatedAt()
        );

        URI location = URI.create("/api/users/" + userId + "/workouts/" + created.id());
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{workoutId}")
    public WorkoutResponse getById(
            @PathVariable Long userId,
            @PathVariable Long workoutId
    ) {
        WorkoutView workout = workoutService.getWorkoutById(userId, workoutId);

        return new WorkoutResponse(
                workout.id(),
                workout.name(),
                workout.createdAt(),
                workout.updatedAt()
        );
    }

    @DeleteMapping("/{workoutId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long userId,
            @PathVariable Long workoutId
    ) {
        workoutService.deleteWorkout(userId, workoutId);
        return ResponseEntity.noContent().build();
    }
}

