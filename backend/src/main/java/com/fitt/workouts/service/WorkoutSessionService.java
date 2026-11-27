package com.fitt.workouts.service;

import com.fitt.workouts.dto.AddExerciseEntryRequest;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.FinishSessionRequest;
import com.fitt.workouts.dto.LogSetRequest;
import com.fitt.workouts.dto.UpdateSessionNotesRequest;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import java.util.List;
import java.util.Optional;

public interface WorkoutSessionService {

    WorkoutSessionResponse createSession(Long ownerUserId, CreateWorkoutSessionRequest request);

    List<WorkoutSessionResponse> listSessions(Long ownerUserId);

    WorkoutSessionResponse getSession(Long ownerUserId, Long sessionId);

    // The session the user started and has not finished, if there is one.
    Optional<WorkoutSessionResponse> getOpenSession(Long ownerUserId);

    // The request may be null: a finish with no body uses the server's clock.
    WorkoutSessionResponse finishSession(Long ownerUserId, Long sessionId, FinishSessionRequest request);

    // A finish with no body.
    WorkoutSessionResponse finishSession(Long ownerUserId, Long sessionId);

    WorkoutSessionResponse updateNotes(Long ownerUserId, Long sessionId, UpdateSessionNotesRequest request);

    void deleteSession(Long ownerUserId, Long sessionId);

    WorkoutSessionResponse addExerciseEntry(Long ownerUserId, Long sessionId, AddExerciseEntryRequest request);

    WorkoutSessionResponse removeExerciseEntry(Long ownerUserId, Long sessionId, Long entryId);

    WorkoutSessionResponse logSet(Long ownerUserId, Long sessionId, Long entryId, LogSetRequest request);

    WorkoutSessionResponse deleteSet(Long ownerUserId, Long sessionId, Long entryId, Long setId);

    long countCompletedSessions(Long ownerUserId, Long planId);
}
