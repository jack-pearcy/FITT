package com.fitt.workouts.service;

import com.fitt.workouts.dto.AddExerciseEntryRequest;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.LogSetRequest;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import java.util.List;

public interface WorkoutSessionService {

    WorkoutSessionResponse createSession(Long ownerUserId, CreateWorkoutSessionRequest request);

    List<WorkoutSessionResponse> listSessions(Long ownerUserId);

    WorkoutSessionResponse getSession(Long ownerUserId, Long sessionId);

    WorkoutSessionResponse finishSession(Long ownerUserId, Long sessionId);

    void deleteSession(Long ownerUserId, Long sessionId);

    WorkoutSessionResponse addExerciseEntry(Long ownerUserId, Long sessionId, AddExerciseEntryRequest request);

    WorkoutSessionResponse removeExerciseEntry(Long ownerUserId, Long sessionId, Long entryId);

    WorkoutSessionResponse logSet(Long ownerUserId, Long sessionId, Long entryId, LogSetRequest request);

    WorkoutSessionResponse deleteSet(Long ownerUserId, Long sessionId, Long entryId, Long setId);

    long countCompletedSessions(Long ownerUserId, Long planId);
}
