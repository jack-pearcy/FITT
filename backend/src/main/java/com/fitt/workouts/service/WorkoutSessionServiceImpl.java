package com.fitt.workouts.service;

import com.fitt.workouts.domain.MuscleGroup;
import com.fitt.workouts.dto.AddExerciseEntryRequest;
import com.fitt.workouts.dto.ClientIds;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.DeviceTimes;
import com.fitt.workouts.dto.ExerciseEntryResponse;
import com.fitt.workouts.dto.ExerciseResponse;
import com.fitt.workouts.dto.FinishSessionRequest;
import com.fitt.workouts.dto.LogSetRequest;
import com.fitt.workouts.dto.SetEntryResponse;
import com.fitt.workouts.dto.UpdateSessionNotesRequest;
import com.fitt.workouts.dto.UpdateSessionRequest;
import com.fitt.workouts.dto.UpdateSetRequest;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import com.fitt.workouts.persistence.entity.ExerciseEntryEntity;
import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.persistence.entity.SetEntryEntity;
import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import com.fitt.workouts.repository.ExerciseRepository;
import com.fitt.workouts.repository.WorkoutPlanRepository;
import com.fitt.workouts.repository.WorkoutSessionRepository;
import com.fitt.workouts.service.exception.ExerciseNotFoundException;
import com.fitt.workouts.service.exception.InvalidSessionEditException;
import com.fitt.workouts.service.exception.OpenSessionExistsException;
import com.fitt.workouts.service.exception.WorkoutPlanNotFoundException;
import com.fitt.workouts.service.exception.WorkoutSessionNotFoundException;
import com.fitt.workouts.service.exception.ExerciseEntryNotFoundException;
import com.fitt.workouts.service.exception.SetEntryNotFoundException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
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
    //
    // A start that carries a client id and loses to its own twin (the same request
    // sent twice at the same moment) is not refused: it returns the session the
    // twin made, as a later retry would.
    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public WorkoutSessionResponse createSession(Long ownerUserId, CreateWorkoutSessionRequest request) {
        String clientId = ClientIds.normalize(request.clientId());
        try {
            return writeTransaction.execute(status -> insertSession(ownerUserId, request, clientId));
        } catch (DataIntegrityViolationException ex) {
            if (clientId != null) {
                // In a transaction, because the response reads the session's entries.
                WorkoutSessionResponse twin = writeTransaction.execute(status -> sessionRepository
                        .findByOwnerUserIdAndClientId(ownerUserId, clientId)
                        .map(this::toResponse)
                        .orElse(null));
                if (twin != null) {
                    return twin;
                }
            }
            Optional<WorkoutSessionEntity> winner = sessionRepository
                    .findFirstByOwnerUserIdAndFinishedAtIsNullOrderByStartedAtDescIdDesc(ownerUserId);
            if (winner.isPresent()) {
                throw new OpenSessionExistsException(winner.get().getId());
            }
            throw ex;
        }
    }

    private WorkoutSessionResponse insertSession(Long ownerUserId, CreateWorkoutSessionRequest request,
                                                 String clientId) {
        // A repeat of a start already stored returns that session, open or
        // finished, and inserts nothing. Checked before the open-session rule, so
        // a retry is never told its own session is in the way.
        if (clientId != null) {
            Optional<WorkoutSessionEntity> same = sessionRepository.findByOwnerUserIdAndClientId(ownerUserId, clientId);
            if (same.isPresent()) {
                return toResponse(same.get());
            }
        }

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
        session.setClientId(clientId);
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
        return finish(ownerUserId, sessionId, null);
    }

    @Override
    @Transactional
    public WorkoutSessionResponse finishSession(Long ownerUserId, Long sessionId, FinishSessionRequest request) {
        return finish(ownerUserId, sessionId, request);
    }

    private WorkoutSessionResponse finish(Long ownerUserId, Long sessionId, FinishSessionRequest request) {
        WorkoutSessionEntity session = loadAndVerify(ownerUserId, sessionId);

        // Idempotent, an already finished session keeps its original finish time
        // rather than being stamped again on a repeat call. That is also why the
        // client id in the body is not stored: a repeat changes nothing either way.
        if (session.getFinishedAt() == null) {
            session.setFinishedAt(finishTime(session.getStartedAt(),
                    request == null ? null : request.finishedAt()));
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

        // A repeat of an add already stored changes nothing. The row lock above
        // makes two copies sent at the same moment take turns, so the second one
        // finds the first one's entry here. The unique key is the last line of defence.
        String clientId = ClientIds.normalize(request.clientId());
        if (clientId != null && session.getExerciseEntries().stream()
                .anyMatch(e -> clientId.equals(e.getClientId()))) {
            return toResponse(session);
        }

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
        entry.setClientId(clientId);

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

        // A repeat of a set already stored logs nothing, for the same reason as
        // a repeated add above.
        String clientId = ClientIds.normalize(request.clientId());
        if (clientId != null && entry.getSetEntries().stream()
                .anyMatch(s -> clientId.equals(s.getClientId()))) {
            return toResponse(session);
        }

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
        set.setClientId(clientId);
        set.setPerformedAt(DeviceTimes.orNullWhenOutOfBounds(request.performedAt(), Instant.now()));

        return toResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public WorkoutSessionResponse deleteSet(Long ownerUserId, Long sessionId, Long entryId, Long setId) {
        WorkoutSessionEntity session = loadForUpdate(ownerUserId, sessionId);
        SetEntryEntity target = findSet(session, entryId, setId);

        target.getExerciseEntry().removeSetEntry(target);

        return toResponse(sessionRepository.save(session));
    }

    // Works on open and finished sessions alike. The set keeps its number, its
    // client id and the time it was done; only what the user typed changes.
    @Override
    @Transactional
    public WorkoutSessionResponse updateSet(Long ownerUserId, Long sessionId, Long entryId, Long setId,
                                            UpdateSetRequest request) {
        WorkoutSessionEntity session = loadForUpdate(ownerUserId, sessionId);
        SetEntryEntity set = findSet(session, entryId, setId);

        set.setReps(request.reps());
        set.setWeight(request.weight());
        set.setRpe(request.rpe());

        return toResponse(sessionRepository.save(session));
    }

    // A person typing a correction, not a device replaying a request, so a bad
    // time is refused with a message rather than dropped as P5 does for sets.
    @Override
    @Transactional
    public WorkoutSessionResponse updateSession(Long ownerUserId, Long sessionId, UpdateSessionRequest request) {
        WorkoutSessionEntity session = loadForUpdate(ownerUserId, sessionId);
        Instant now = Instant.now();

        if (request.finishedAt() != null && session.getFinishedAt() == null) {
            throw new InvalidSessionEditException("finishedAt",
                    "Finish the workout before changing its finish time");
        }
        Instant startedAt = request.startedAt() == null
                ? session.getStartedAt()
                : editedTime("startedAt", request.startedAt(), now);
        Instant finishedAt = request.finishedAt() == null
                ? session.getFinishedAt()
                : editedTime("finishedAt", request.finishedAt(), now);
        if (finishedAt != null && !finishedAt.isAfter(startedAt)) {
            throw new InvalidSessionEditException(request.finishedAt() != null ? "finishedAt" : "startedAt",
                    "The finish must be after the start");
        }

        session.setStartedAt(startedAt);
        session.setFinishedAt(finishedAt);
        if (request.notes() != null) {
            String notes = request.notes().strip();
            session.setNotes(notes.isEmpty() ? null : notes);
        }

        return toResponse(sessionRepository.save(session));
    }

    // A few minutes of slack for a phone whose clock runs a little ahead, so
    // "now" picked on the device is not refused. The time stored is never after
    // the server's clock, for the reason given at finishTime below.
    private static final Duration EDIT_CLOCK_SLACK = Duration.ofMinutes(5);

    private static Instant editedTime(String field, Instant time, Instant now) {
        if (time.isBefore(DeviceTimes.EARLIEST)) {
            throw new InvalidSessionEditException(field, "The time must be in the year 2000 or later");
        }
        if (time.isAfter(now.plus(EDIT_CLOCK_SLACK))) {
            throw new InvalidSessionEditException(field, "The time cannot be in the future");
        }
        return time.isAfter(now) ? now : time;
    }

    // The set through its entry inside this session, so a set id from another
    // entry or another session reads as not found.
    private static SetEntryEntity findSet(WorkoutSessionEntity session, Long entryId, Long setId) {
        ExerciseEntryEntity entry = session.getExerciseEntries().stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .orElseThrow(() -> new ExerciseEntryNotFoundException(entryId));

        return entry.getSetEntries().stream()
                .filter(s -> s.getId().equals(setId))
                .findFirst()
                .orElseThrow(() -> new SetEntryNotFoundException(setId));
    }

    // The device's finish time when it sent one, so a workout finished offline is
    // not stretched to the moment the phone got a connection. Kept only when it
    // is not before the start, not before the lower bound in DeviceTimes, and
    // not after the server's clock. In every other case the server's clock is
    // stored.
    //
    // Never after the server's clock, because a finish cannot be later than the
    // moment the server hears of it, and because finished_at is read as real
    // time: the plan rotation takes the newest finish as the last day trained,
    // and the trained-today check looks for a finish inside today. A finish
    // stored ahead of the clock would stay the newest one until real time passed
    // it, and would not count as today.
    //
    // Never refused, because both times come from a clock the server does not
    // control. A refusal would repeat on every retry and leave the user with an
    // open session that blocks every new start.
    //
    // A start is stored as the device sent it, as it was before finish took a
    // time. When that start is ahead of the server's clock, the finish is stored
    // before it. The pair is wrong either way, and the finish is the half the
    // rotation and the trained-today check read.
    private static Instant finishTime(Instant startedAt, Instant fromDevice) {
        Instant now = Instant.now();
        Instant usable = DeviceTimes.orNullWhenOutOfBounds(fromDevice, now);
        if (usable != null && !usable.isBefore(startedAt) && !usable.isAfter(now)) {
            return usable;
        }
        return now;
    }

    // Every session is read through the owner. Another user's session reads as
    // not found, the same answer as an id that does not exist, so a caller cannot
    // learn which session ids are in use.
    private WorkoutSessionEntity loadAndVerify(Long ownerUserId, Long sessionId) {
        return sessionRepository.findByIdAndOwnerUserId(sessionId, ownerUserId)
                .orElseThrow(() -> new WorkoutSessionNotFoundException(sessionId));
    }

    // For a change to the session's entries or sets. Holds the session row until the
    // transaction ends, so the next number is read after any earlier change is saved.
    private WorkoutSessionEntity loadForUpdate(Long ownerUserId, Long sessionId) {
        return sessionRepository.findByIdAndOwnerUserIdForUpdate(sessionId, ownerUserId)
                .orElseThrow(() -> new WorkoutSessionNotFoundException(sessionId));
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
                entity.isCompleted(),
                entity.getClientId(),
                entity.getPerformedAt()
        );
    }
}