package com.fitt.workouts.service;

import com.fitt.workouts.dto.AddExerciseEntryRequest;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.ExerciseEntryResponse;
import com.fitt.workouts.dto.LogSetRequest;
import com.fitt.workouts.dto.SetEntryResponse;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.persistence.entity.ExerciseEntryEntity;
import com.fitt.workouts.persistence.entity.SetEntryEntity;
import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import com.fitt.workouts.repository.ExerciseRepository;
import com.fitt.workouts.repository.WorkoutSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Numbering sets and entries against a real database, with real transactions. A
// mocked repository cannot show either thing this class is about: that requests
// for one session wait for each other, and that the database refuses a duplicate.
// No test-level transaction on purpose, so each service call commits by itself.
@SpringBootTest
@ActiveProfiles("test")
class WorkoutSessionNumberingTest {

    private static final long USER_ID = 301L;

    @Autowired
    private WorkoutSessionService service;

    @Autowired
    private WorkoutSessionRepository sessionRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Test
    void logSet_manyRequestsAtOnce_giveEverySetItsOwnNumber() throws Exception {
        ExerciseEntity bench = exerciseRepository.save(new ExerciseEntity(null, "Numbering Bench", "CHEST", false));
        WorkoutSessionResponse session = service.createSession(
                USER_ID, new CreateWorkoutSessionRequest(Instant.now(), null, null));
        long entryId = service.addExerciseEntry(USER_ID, session.id(), new AddExerciseEntryRequest(bench.getId()))
                .exerciseEntries().get(0).id();

        int requests = 8;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            Callable<WorkoutSessionResponse> logOne = () -> service.logSet(
                    USER_ID, session.id(), entryId, new LogSetRequest(5, new BigDecimal("100.00"), null));
            List<Future<WorkoutSessionResponse>> results = pool.invokeAll(java.util.Collections.nCopies(requests, logOne));
            for (Future<WorkoutSessionResponse> result : results) {
                result.get();
            }
        } finally {
            pool.shutdown();
        }

        ExerciseEntryResponse entry = service.getSession(USER_ID, session.id()).exerciseEntries().get(0);
        assertThat(entry.sets()).extracting(SetEntryResponse::setNumber)
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void addExerciseEntry_manyRequestsAtOnce_giveEveryEntryItsOwnIndex() throws Exception {
        ExerciseEntity row = exerciseRepository.save(new ExerciseEntity(null, "Numbering Row", "BACK", false));
        WorkoutSessionResponse session = service.createSession(
                USER_ID, new CreateWorkoutSessionRequest(Instant.now(), null, null));

        int requests = 6;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            Callable<WorkoutSessionResponse> addOne = () -> service.addExerciseEntry(
                    USER_ID, session.id(), new AddExerciseEntryRequest(row.getId()));
            for (Future<WorkoutSessionResponse> result : pool.invokeAll(java.util.Collections.nCopies(requests, addOne))) {
                result.get();
            }
        } finally {
            pool.shutdown();
        }

        assertThat(service.getSession(USER_ID, session.id()).exerciseEntries())
                .extracting(ExerciseEntryResponse::orderIndex)
                .containsExactlyInAnyOrder(0, 1, 2, 3, 4, 5);
    }

    @Test
    void twoSetsWithTheSameNumberInOneEntry_areRefusedByTheDatabase() {
        ExerciseEntity press = exerciseRepository.save(new ExerciseEntity(null, "Numbering Press", "SHOULDERS", false));
        WorkoutSessionEntity session = new WorkoutSessionEntity(USER_ID, Instant.now());
        ExerciseEntryEntity entry = new ExerciseEntryEntity(press, 0);
        session.addExerciseEntry(entry);
        entry.addSetEntry(new SetEntryEntity(1, new BigDecimal("50.00"), 8, null));
        entry.addSetEntry(new SetEntryEntity(1, new BigDecimal("50.00"), 8, null));

        assertThatThrownBy(() -> sessionRepository.saveAndFlush(session))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoEntriesWithTheSameIndexInOneSession_areRefusedByTheDatabase() {
        ExerciseEntity curl = exerciseRepository.save(new ExerciseEntity(null, "Numbering Curl", "ARMS", false));
        WorkoutSessionEntity session = new WorkoutSessionEntity(USER_ID, Instant.now());
        session.addExerciseEntry(new ExerciseEntryEntity(curl, 0));
        session.addExerciseEntry(new ExerciseEntryEntity(curl, 0));

        assertThatThrownBy(() -> sessionRepository.saveAndFlush(session))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
