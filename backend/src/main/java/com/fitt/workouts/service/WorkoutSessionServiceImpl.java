package com.fitt.workouts.service;

import com.fitt.workouts.domain.MuscleGroup;
import com.fitt.workouts.dto.AddExerciseEntryRequest;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.ExerciseEntryResponse;
import com.fitt.workouts.dto.ExerciseResponse;
import com.fitt.workouts.dto.LogSetRequest;
import com.fitt.workouts.dto.SetEntryResponse;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import com.fitt.workouts.persistence.entity.ExerciseEntryEntity;
import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.persistence.entity.SetEntryEntity;
import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import com.fitt.workouts.repository.ExerciseRepository;
import com.fitt.workouts.repository.WorkoutSessionRepository;
import com.fitt.workouts.service.exception.ExerciseNotFoundException;
import com.fitt.workouts.service.exception.WorkoutSessionNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class WorkoutSessionServiceImpl implements WorkoutSessionService {

    private final WorkoutSessionRepository sessionRepository;
    private final ExerciseRepository exerciseRepository;

    public WorkoutSessionServiceImpl(WorkoutSessionRepository sessionRepository,
                                     ExerciseRepository exerciseRepository) {
        this.sessionRepository = sessionRepository;
        this.exerciseRepository = exerciseRepository;
    }

    @Override
    @Transactional
    public WorkoutSessionResponse createSession(Long ownerUserId, CreateWorkoutSessionRequest request) {
        // entity constructor requires ownerUserId and startedAt
        WorkoutSessionEntity session = new WorkoutSessionEntity(ownerUserId, request.startedAt());
        session.setNotes(request.notes());
        session.setPlanId(request.planId());
        return toResponse(sessionRepository.save(session));
    }

    @Override
    public List<WorkoutSessionResponse> listSessions(Long ownerUserId) {
        return sessionRepository.findByOwnerUserIdOrderByStartedAtDesc(ownerUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public long countCompletedSessions(Long ownerUserId, Long planId) {
        return sessionRepository.countByOwnerUserIdAndPlanIdAndFinishedAtIsNotNull(ownerUserId, planId);
    }

    @Override
    public WorkoutSessionResponse getSession(Long ownerUserId, Long sessionId) {
        return toResponse(loadAndVerify(ownerUserId, sessionId));
    }

    @Override
    @Transactional
    public WorkoutSessionResponse finishSession(Long ownerUserId, Long sessionId) {
        WorkoutSessionEntity session = loadAndVerify(ownerUserId, sessionId);
        session.setFinishedAt(Instant.now());
        return toResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public void deleteSession(Long ownerUserId, Long sessionId) {
        sessionRepository.delete(loadAndVerify(ownerUserId, sessionId));
    }

    @Override
    @Transactional
    public WorkoutSessionResponse addExerciseEntry(Long ownerUserId, Long sessionId, AddExerciseEntryRequest request) {
        WorkoutSessionEntity session = loadAndVerify(ownerUserId, sessionId);

        ExerciseEntity exercise = exerciseRepository.findById(request.exerciseId())
                .orElseThrow(() -> new ExerciseNotFoundException(request.exerciseId()));

        // append to end of existing entries
        int nextIndex = session.getExerciseEntries().size();

        // entity constructor takes exercise and orderIndex
        ExerciseEntryEntity entry = new ExerciseEntryEntity(exercise, nextIndex);

        // addExerciseEntry syncs both sides
        session.addExerciseEntry(entry);

        return toResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public WorkoutSessionResponse removeExerciseEntry(Long ownerUserId, Long sessionId, Long entryId) {
        WorkoutSessionEntity session = loadAndVerify(ownerUserId, sessionId);

        ExerciseEntryEntity target = session.getExerciseEntries().stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Exercise entry not found in this session"));

        // orphanRemoval handles the DELETE
        session.removeExerciseEntry(target);

        return toResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public WorkoutSessionResponse logSet(Long ownerUserId, Long sessionId, Long entryId, LogSetRequest request) {
        WorkoutSessionEntity session = loadAndVerify(ownerUserId, sessionId);

        ExerciseEntryEntity entry = session.getExerciseEntries().stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Exercise entry not found in this session"));

        int nextSetNumber = entry.getSetEntries().size() + 1;

        // entity constructor: setNumber, weight, reps, rpe
        SetEntryEntity set = new SetEntryEntity(nextSetNumber, request.weight(), request.reps(), request.rpe());

        // addSetEntry syncs both sides
        entry.addSetEntry(set);

        set.setCompleted(true);

        return toResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public WorkoutSessionResponse deleteSet(Long ownerUserId, Long sessionId, Long entryId, Long setId) {
        WorkoutSessionEntity session = loadAndVerify(ownerUserId, sessionId);

        ExerciseEntryEntity entry = session.getExerciseEntries().stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Exercise entry not found in this session"));

        SetEntryEntity target = entry.getSetEntries().stream()
                .filter(s -> s.getId().equals(setId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Set not found in this exercise entry"));

        entry.removeSetEntry(target);

        return toResponse(sessionRepository.save(session));
    }

    private WorkoutSessionEntity loadAndVerify(Long ownerUserId, Long sessionId) {
        WorkoutSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new WorkoutSessionNotFoundException(sessionId));

        if (!ownerUserId.equals(session.getOwnerUserId())) {
            throw new AccessDeniedException("You do not own this session");
        }

        return session;
    }

    private WorkoutSessionResponse toResponse(WorkoutSessionEntity entity) {
        List<ExerciseEntryResponse> entries = entity.getExerciseEntries().stream()
                .map(this::toEntryResponse)
                .toList();

        return new WorkoutSessionResponse(
                entity.getId(),
                entity.getOwnerUserId(),
                entity.getPlanId(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getNotes(),
                entries
        );
    }

    private ExerciseEntryResponse toEntryResponse(ExerciseEntryEntity entity) {
        ExerciseResponse exercise = new ExerciseResponse(
                entity.getExercise().getId(),
                entity.getExercise().getName(),
                MuscleGroup.valueOf(entity.getExercise().getMuscleGroup()),
                entity.getExercise().isCustom()
        );

        List<SetEntryResponse> sets = entity.getSetEntries().stream()
                .map(this::toSetResponse)
                .toList();

        return new ExerciseEntryResponse(
                entity.getId(),
                entity.getOrderIndex(),
                exercise,
                sets
        );
    }

    private SetEntryResponse toSetResponse(SetEntryEntity entity) {
        return new SetEntryResponse(
                entity.getId(),
                entity.getSetNumber(),
                entity.getReps(),
                entity.getWeight(),
                entity.getRpe(),
                entity.isCompleted()
        );
    }
}