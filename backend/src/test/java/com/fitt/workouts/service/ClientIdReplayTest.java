package com.fitt.workouts.service;

import com.fitt.workouts.dto.AddExerciseEntryRequest;
import com.fitt.workouts.dto.CreateWorkoutSessionRequest;
import com.fitt.workouts.dto.FinishSessionRequest;
import com.fitt.workouts.dto.LogSetRequest;
import com.fitt.workouts.dto.SetEntryResponse;
import com.fitt.workouts.dto.WorkoutSessionResponse;
import com.fitt.workouts.persistence.entity.ExerciseEntity;
import com.fitt.workouts.repository.ExerciseRepository;
import com.fitt.workouts.service.exception.OpenSessionExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// A request sent twice with the same client id is stored once. Against a real
// database with real transactions, counting rows, because "inserts nothing" is a
// statement about the table and a mocked repository cannot show it.
// No test-level transaction, so each service call commits by itself.
// Each test has its own user, because a user may have only one open session.
@SpringBootTest
@ActiveProfiles("test")
class ClientIdReplayTest {

    private static final Instant STARTED = Instant.parse("2026-11-20T10:00:00Z");

    @Autowired
    private WorkoutSessionService service;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theSameStartTwice_givesOneSession() {
        long userId = 7301L;
        String clientId = UUID.randomUUID().toString();

        WorkoutSessionResponse first = service.createSession(userId, start(clientId));
        WorkoutSessionResponse second = service.createSession(userId, start(clientId));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(sessionCount(userId)).isEqualTo(1);
    }

    @Test
    void theSameStart_inAnotherLetterCase_isTheSameStart() {
        long userId = 7302L;
        String clientId = UUID.randomUUID().toString();

        WorkoutSessionResponse first = service.createSession(userId, start(clientId.toUpperCase(Locale.ROOT)));
        WorkoutSessionResponse second = service.createSession(userId, start(clientId));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(sessionCount(userId)).isEqualTo(1);
    }

    @Test
    void aStartRepeatedAfterItsSessionWasFinished_returnsThatSession_andStartsNothing() {
        long userId = 7303L;
        String clientId = UUID.randomUUID().toString();
        WorkoutSessionResponse first = service.createSession(userId, start(clientId));
        service.finishSession(userId, first.id(), null);

        WorkoutSessionResponse repeat = service.createSession(userId, start(clientId));

        assertThat(repeat.id()).isEqualTo(first.id());
        assertThat(repeat.finishedAt()).isNotNull();
        assertThat(sessionCount(userId)).isEqualTo(1);
    }

    @Test
    void aStartWithAnotherId_whileOneIsOpen_isStillRefused() {
        long userId = 7304L;
        WorkoutSessionResponse open = service.createSession(userId, start(UUID.randomUUID().toString()));

        assertThatThrownBy(() -> service.createSession(userId, start(UUID.randomUUID().toString())))
                .isInstanceOf(OpenSessionExistsException.class)
                .extracting(ex -> ((OpenSessionExistsException) ex).getOpenSessionId())
                .isEqualTo(open.id());
        assertThat(sessionCount(userId)).isEqualTo(1);
    }

    @Test
    void twoUsers_mayUseTheSameClientId() {
        String clientId = UUID.randomUUID().toString();

        WorkoutSessionResponse mine = service.createSession(7305L, start(clientId));
        WorkoutSessionResponse theirs = service.createSession(7306L, start(clientId));

        assertThat(theirs.id()).isNotEqualTo(mine.id());
        assertThat(theirs.ownerUserId()).isEqualTo(7306L);
    }

    @Test
    void theSameAddedExerciseTwice_givesOneEntry() {
        long userId = 7307L;
        ExerciseEntity bench = exerciseRepository.save(new ExerciseEntity(null, "Replay Bench", "CHEST", false));
        WorkoutSessionResponse session = service.createSession(userId, start(null));
        AddExerciseEntryRequest add = new AddExerciseEntryRequest(bench.getId(), UUID.randomUUID().toString());

        service.addExerciseEntry(userId, session.id(), add);
        WorkoutSessionResponse afterRepeat = service.addExerciseEntry(userId, session.id(), add);

        assertThat(afterRepeat.exerciseEntries()).hasSize(1);
        assertThat(entryCount(session.id())).isEqualTo(1);
    }

    @Test
    void theSameSetTwice_givesOneSet_andTheResponseCarriesItsIdAndTime() {
        long userId = 7308L;
        ExerciseEntity squat = exerciseRepository.save(new ExerciseEntity(null, "Replay Squat", "LEGS", false));
        WorkoutSessionResponse session = service.createSession(userId, start(null));
        long entryId = service.addExerciseEntry(userId, session.id(), new AddExerciseEntryRequest(squat.getId()))
                .exerciseEntries().get(0).id();
        String clientId = UUID.randomUUID().toString();
        Instant performedAt = Instant.parse("2026-11-20T10:05:30.250Z");
        LogSetRequest set = new LogSetRequest(5, new BigDecimal("100.00"), null, clientId, performedAt);

        service.logSet(userId, session.id(), entryId, set);
        WorkoutSessionResponse afterRepeat = service.logSet(userId, session.id(), entryId, set);

        assertThat(afterRepeat.exerciseEntries().get(0).sets()).hasSize(1);
        assertThat(setCount(entryId)).isEqualTo(1);
        SetEntryResponse stored = service.getSession(userId, session.id()).exerciseEntries().get(0).sets().get(0);
        assertThat(stored.clientId()).isEqualTo(clientId);
        assertThat(stored.performedAt()).isEqualTo(performedAt);
    }

    // The Apple apps write the id in upper case. A retry in the other letter case
    // is the same action, and the stored id is lower case either way.
    @Test
    void theSameAddedExercise_inAnotherLetterCase_givesOneEntry_storedInLowerCase() {
        long userId = 7313L;
        ExerciseEntity press = exerciseRepository.save(new ExerciseEntity(null, "Replay Press", "SHOULDERS", false));
        WorkoutSessionResponse session = service.createSession(userId, start(null));
        String clientId = UUID.randomUUID().toString();

        service.addExerciseEntry(userId, session.id(),
                new AddExerciseEntryRequest(press.getId(), clientId.toUpperCase(Locale.ROOT)));
        WorkoutSessionResponse afterRepeat = service.addExerciseEntry(userId, session.id(),
                new AddExerciseEntryRequest(press.getId(), clientId));

        assertThat(afterRepeat.exerciseEntries()).hasSize(1);
        assertThat(entryCount(session.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select client_id from exercise_entries where workout_session_id = ?",
                String.class, session.id())).isEqualTo(clientId);
    }

    @Test
    void theSameSet_inAnotherLetterCase_givesOneSet_storedInLowerCase() {
        long userId = 7314L;
        ExerciseEntity curl = exerciseRepository.save(new ExerciseEntity(null, "Replay Curl", "ARMS", false));
        WorkoutSessionResponse session = service.createSession(userId, start(null));
        long entryId = service.addExerciseEntry(userId, session.id(), new AddExerciseEntryRequest(curl.getId()))
                .exerciseEntries().get(0).id();
        String clientId = UUID.randomUUID().toString();

        WorkoutSessionResponse first = service.logSet(userId, session.id(), entryId,
                new LogSetRequest(10, new BigDecimal("20.00"), null, clientId.toUpperCase(Locale.ROOT), null));
        WorkoutSessionResponse afterRepeat = service.logSet(userId, session.id(), entryId,
                new LogSetRequest(10, new BigDecimal("20.00"), null, clientId, null));

        assertThat(first.exerciseEntries().get(0).sets().get(0).clientId()).isEqualTo(clientId);
        assertThat(afterRepeat.exerciseEntries().get(0).sets()).hasSize(1);
        assertThat(setCount(entryId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select client_id from set_entries where exercise_entry_id = ?",
                String.class, entryId)).isEqualTo(clientId);
    }

    @Test
    void setsWithNoClientId_areAllStored_andHaveNoIdOrTime() {
        long userId = 7309L;
        ExerciseEntity row = exerciseRepository.save(new ExerciseEntity(null, "Replay Row", "BACK", false));
        WorkoutSessionResponse session = service.createSession(userId, start(null));
        long entryId = service.addExerciseEntry(userId, session.id(), new AddExerciseEntryRequest(row.getId()))
                .exerciseEntries().get(0).id();
        LogSetRequest set = new LogSetRequest(8, new BigDecimal("60.00"), null);

        service.logSet(userId, session.id(), entryId, set);
        WorkoutSessionResponse after = service.logSet(userId, session.id(), entryId, set);

        assertThat(after.exerciseEntries().get(0).sets()).hasSize(2)
                .allSatisfy(stored -> {
                    assertThat(stored.clientId()).isNull();
                    assertThat(stored.performedAt()).isNull();
                });
    }

    @Test
    void finish_usesTheTimeTheDeviceSent() {
        long userId = 7310L;
        WorkoutSessionResponse session = service.createSession(userId, start(null));
        Instant finishedAt = Instant.parse("2026-11-20T11:15:00Z");

        service.finishSession(userId, session.id(), new FinishSessionRequest(null, finishedAt));

        assertThat(service.getSession(userId, session.id()).finishedAt()).isEqualTo(finishedAt);
    }

    @Test
    void finish_withABodyThatNamesNothing_usesTheServerClock() {
        long userId = 7311L;
        WorkoutSessionResponse session = service.createSession(userId, start(null));
        Instant before = Instant.now().minusSeconds(1);

        service.finishSession(userId, session.id(), new FinishSessionRequest(null, null));

        assertThat(service.getSession(userId, session.id()).finishedAt()).isAfter(before);
    }

    // A phone whose clock ran fast at the start and was corrected before the
    // finish sends a finish time before the start time. Refusing it would repeat
    // on every retry and leave the session open for good, so the finish goes
    // through on the server's clock.
    @Test
    void finish_withADeviceTimeBeforeTheStart_finishesOnTheServerClock() {
        long userId = 7312L;
        WorkoutSessionResponse session = service.createSession(userId, start(null));
        Instant before = Instant.now().minusSeconds(1);

        service.finishSession(userId, session.id(), new FinishSessionRequest(null, STARTED.minusSeconds(60)));

        assertThat(service.getSession(userId, session.id()).finishedAt()).isAfter(before);
        assertThat(service.getOpenSession(userId)).isEmpty();
    }

    // The start time is ahead of the server's clock too, so neither the device's
    // finish time nor the server's is usable. The session still finishes, at its
    // start time, and a new start is possible afterwards.
    @Test
    void finish_whenTheStartIsAheadOfEveryClock_finishesAtTheStartTime() {
        long userId = 7315L;
        Instant startedAhead = Instant.now().plus(Duration.ofHours(2)).truncatedTo(ChronoUnit.SECONDS);
        WorkoutSessionResponse session = service.createSession(userId,
                new CreateWorkoutSessionRequest(startedAhead, null, null, null));

        service.finishSession(userId, session.id(),
                new FinishSessionRequest(null, startedAhead.minus(Duration.ofHours(1))));

        assertThat(service.getSession(userId, session.id()).finishedAt()).isEqualTo(startedAhead);
        WorkoutSessionResponse next = service.createSession(userId, start(null));
        assertThat(next.id()).isNotEqualTo(session.id());
    }

    @Test
    void finish_withNoBody_whenTheStartIsAheadOfTheServerClock_finishesAtTheStartTime() {
        long userId = 7316L;
        Instant startedAhead = Instant.now().plus(Duration.ofHours(2)).truncatedTo(ChronoUnit.SECONDS);
        WorkoutSessionResponse session = service.createSession(userId,
                new CreateWorkoutSessionRequest(startedAhead, null, null, null));

        service.finishSession(userId, session.id(), null);

        assertThat(service.getSession(userId, session.id()).finishedAt()).isEqualTo(startedAhead);
    }

    private static CreateWorkoutSessionRequest start(String clientId) {
        return new CreateWorkoutSessionRequest(STARTED, null, null, clientId);
    }

    private Integer sessionCount(long userId) {
        return jdbc.queryForObject("select count(*) from workout_sessions where owner_user_id = ?",
                Integer.class, userId);
    }

    private Integer entryCount(long sessionId) {
        return jdbc.queryForObject("select count(*) from exercise_entries where workout_session_id = ?",
                Integer.class, sessionId);
    }

    private Integer setCount(long entryId) {
        return jdbc.queryForObject("select count(*) from set_entries where exercise_entry_id = ?",
                Integer.class, entryId);
    }
}
