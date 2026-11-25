package com.fitt.workouts.service;

import com.fitt.workouts.domain.MuscleGroup;
import com.fitt.workouts.dto.AddExerciseEntryRequest;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.ExerciseEntryResponse;
import com.fitt.workouts.dto.ExerciseResponse;
import com.fitt.workouts.dto.LogSetRequest;
import com.fitt.workouts.dto.SetEntryResponse;
import com.fitt.workouts.dto.UpdateSessionNotesRequest;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import com.fitt.workouts.persistence.entity.ExerciseEntryEntity;
import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.persistence.entity.SetEntryEntity;
import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import com.fitt.workouts.repository.ExerciseRepository;
import com.fitt.workouts.repository.WorkoutPlanRepository;
import com.fitt.workouts.repository.WorkoutSessionRepository;
import com.fitt.workouts.service.exception.ExerciseNotFoundException;
import com.fitt.workouts.service.exception.OpenSessionExistsException;
import com.fitt.workouts.service.exception.WorkoutPlanNotFoundException;
import com.fitt.workouts.service.exception.WorkoutSessionNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.fitt.workouts.service.exception.ExerciseEntryNotFoundException;
import com.fitt.workouts.service.exception.SetEntryNotFoundException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class WorkoutSessionServiceImpl implements WorkoutSessionService {

    private final WorkoutSessionRepository sessionRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutPlanRepository planRepository;
    private final TransactionTemplate writeTransaction;

    public WorkoutSessionServiceImpl(WorkoutSessionRepository sessionRepository,
                                     ExerciseRepository exerciseRepository,
                                     WorkoutPlanRepository planRepository,
                                     PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.exerciseRepository = exerciseRepository;
        this.planRepository = planRepository;
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    // Not one transaction around the whole call. The insert commits or rolls back
    // inside the template, so when the unique key on open_owner_id refuses it, the
    // read that follows runs in a fresh transaction and sees the session that won.
    // Inside the failed transaction MySQL would still show the snapshot from
    // before the other insert committed.
    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public WorkoutSessionResponse createSession(Long ownerUserId, CreateWorkoutSessionRequest request) {
        try {
            return writeTransaction.execute(status -> insertSession(ownerUserId, request));
        } catch (DataIntegrityViolationException ex) {
            Optional<WorkoutSessionEntity> winner = sessionRepository
                    .findFirstByOwnerUserIdAndFinishedAtIsNullOrderByStartedAtDescIdDesc(ownerUserId);
            if (winner.isPresent()) {
                throw new OpenSessionExistsException(winner.get().getId());
            }
            throw ex;
        }
    }

    private WorkoutSessionResponse insertSession(Long ownerUserId, CreateWorkoutSessionRequest request) {
        // One unfinished session per user. The client resumes the one named in the
        // refusal. V12's unique key backs this check when two starts race.
        sessionRepository.findFirstByOwnerUserIdAndFinishedAtIsNullOrderByStartedAtDescIdDesc(ownerUserId)
                .ifPresent(open -> {
                    throw new OpenSessionExistsException(open.getId());
                });

        // A session may only point at one of the caller's own plans. Reading as not
        // found, not forbidden, so another user's plan ids are not confirmable.
        Integer planDayIndex = null;
        if (request.planId() != null) {
            int dayCount = planRepository.findByIdAndOwnerUserId(request.planId(), ownerUserId)
                    .orElseThrow(() -> new WorkoutPlanNotFoundException(request.planId()))
                    .getDays().size();
            // Record which day this session trains: the day the rotation points at now.
            if (dayCount > 0) {
                planDayIndex = PlanRotation.nextDayIndex(sessionRepository, ownerUserId, request.planId(), dayCount);
            }
        }

        // entity constructor requires ownerUserId and startedAt
        WorkoutSessionEntity session = new WorkoutSessionEntity(ownerUserId, request.startedAt());
        session.setNotes(request.notes());
        session.setPlanId(request.planId());
        session.setPlanDayIndex(planDayIndex);
        return toResponse(sessionRepository.save(session));
    }

    @Override
    public Optional<WorkoutSessionResponse> getOpenSession(Long ownerUserId) {
        return sessionRepository.findFirstByOwnerUserIdAndFinishedAtIsNullOrderByStartedAtDescIdDesc(ownerUserId)
                .map(this::toResponse);
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

        // Idempotent, an already finished session keeps its original finish time
        // rather than being stamped again on a repeat call.
        if (session.getFinishedAt() == null) {
            session.setFinishedAt(Instant.now());
        }

        return toResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public WorkoutSessionResponse updateNotes(Long ownerUserId, Long sessionId, UpdateSessionNotesRequest request) {
        WorkoutSessionEntity session = loadAndVerify(ownerUserId, sessionId);

        // Blank text is stored as no notes, so a cleared field and a field that
        // was never filled in read back the same way.
        String notes = request.notes() == null ? null : request.notes().strip();
        session.setNotes(notes == null || notes.isEmpty() ? null : notes);

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
        WorkoutSessionEntity session = loadForUpdate(ownerUserId, sessionId);

        ExerciseEntity exercise = exerciseRepository
                .findAccessibleById(request.exerciseId(), ownerUserId)
                .orElseThrow(() -> new ExerciseNotFoundException(request.exerciseId()));

        // One past the highest existing index, not size(), so removing an entry
        // and adding another cannot produce two entries with the same index.
        int nextIndex = session.getExerciseEntries().stream()
                .mapToInt(ExerciseEntryEntity::getOrderIndex)
                .max()
                .orElse(-1) + 1;

        // entity constructor takes exercise and orderIndex
        ExerciseEntryEntity entry = new ExerciseEntryEntity(exercise, nextIndex);

        // addExerciseEntry syncs both sides
        session.addExerciseEntry(entry);

        return toResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public WorkoutSessionResponse removeExerciseEntry(Long ownerUserId, Long sessionId, Long entryId) {
        WorkoutSessionEntity session = loadForUpdate(ownerUserId, sessionId);

        ExerciseEntryEntity target = session.getExerciseEntries().stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .orElseThrow(() -> new ExerciseEntryNotFoundException(entryId));

        // orphanRemoval handles the DELETE
        session.removeExerciseEntry(target);

        return toResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public WorkoutSessionResponse logSet(Long ownerUserId, Long sessionId, Long entryId, LogSetRequest request) {
        WorkoutSessionEntity session = loadForUpdate(ownerUserId, sessionId);

        ExerciseEntryEntity entry = session.getExerciseEntries().stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .orElseThrow(() -> new ExerciseEntryNotFoundException(entryId));

        // One past the highest existing number, not size() + 1, so deleting a middle
        // set and logging again cannot produce two sets with the same number.
        int nextSetNumber = entry.getSetEntries().stream()
                .mapToInt(SetEntryEntity::getSetNumber)
                .max()
                .orElse(0) + 1;

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
        WorkoutSessionEntity session = loadForUpdate(ownerUserId, sessionId);

        ExerciseEntryEntity entry = session.getExerciseEntries().stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .orElseThrow(() -> new ExerciseEntryNotFoundException(entryId));

        SetEntryEntity target = entry.getSetEntries().stream()
                .filter(s -> s.getId().equals(setId))
                .findFirst()
                .orElseThrow(() -> new SetEntryNotFoundException(setId));

        entry.removeSetEntry(target);

        return toResponse(sessionRepository.save(session));
    }

    private WorkoutSessionEntity loadAndVerify(Long ownerUserId, Long sessionId) {
        return verifyOwner(ownerUserId, sessionRepository.findById(sessionId)
                .orElseThrow(() -> new WorkoutSessionNotFoundException(sessionId)));
    }

    // For a change to the session's entries or sets. Holds the session row until the
    // transaction ends, so the next number is read after any earlier change is saved.
    private WorkoutSessionEntity loadForUpdate(Long ownerUserId, Long sessionId) {
        return verifyOwner(ownerUserId, sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new WorkoutSessionNotFoundException(sessionId)));
    }

    private WorkoutSessionEntity verifyOwner(Long ownerUserId, WorkoutSessionEntity session) {
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