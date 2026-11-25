package com.fitt.shared.schema;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import db.migration.V12__one_open_session_per_user;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// V12 on a database that already holds several open sessions per user, the state
// production can be in before it runs. Flyway stops at V11, the rows are seeded,
// then V12 runs and the cleanup is checked row by row. No Spring context: the
// test drives Flyway and plain JDBC, as a deploy would.
@Testcontainers
@Tag("integration")
class OneOpenSessionMigrationIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Test
    void v12_keepsTheNewestOpenSession_deletesEmptyOldOnes_andFinishesOldOnesWithSets() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        flyway(dataSource, "11").migrate();
        long exerciseId = Objects.requireNonNull(
                jdbc.queryForObject("select min(id) from exercises", Long.class));

        // User 1: three older open sessions, a newest open one, and a finished one.
        long empty = session(jdbc, 1, "2026-05-01T10:00:00", null);
        long entryNoSets = session(jdbc, 1, "2026-05-02T10:00:00", null);
        long entryNoSetsEntry = entry(jdbc, entryNoSets, exerciseId);
        long withSets = session(jdbc, 1, "2026-05-03T10:00:00", null);
        long withSetsEntry = entry(jdbc, withSets, exerciseId);
        set(jdbc, withSetsEntry, 1);
        set(jdbc, withSetsEntry, 2);
        long newest = session(jdbc, 1, "2026-05-04T10:00:00", null);
        long finished = session(jdbc, 1, "2026-04-30T10:00:00", "2026-04-30T11:00:00");

        // User 2: two open sessions started at the same moment. The higher id stays.
        long tieLow = session(jdbc, 2, "2026-05-05T10:00:00", null);
        long tieHigh = session(jdbc, 2, "2026-05-05T10:00:00", null);

        // User 3: one open session, nothing to clean.
        long alone = session(jdbc, 3, "2026-05-06T10:00:00", null);

        Logger v12Log = (Logger) LoggerFactory.getLogger(V12__one_open_session_per_user.class);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        v12Log.addAppender(logged);
        try {
            flyway(dataSource, "12").migrate();
        } finally {
            v12Log.detachAppender(logged);
        }

        // The deploy log gets the three counts and nothing else: user 1 has one old
        // session with sets, one entry in an empty old session, and two empty old
        // sessions; user 2 has one empty older session on the tie.
        assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
                "V12 open session cleanup: 1 sessions finished, 1 exercise entries deleted, 3 sessions deleted");

        assertThat(openIds(jdbc, 1)).containsExactly(newest);
        assertThat(exists(jdbc, "workout_sessions", empty)).isFalse();
        assertThat(exists(jdbc, "workout_sessions", entryNoSets)).isFalse();
        assertThat(exists(jdbc, "exercise_entries", entryNoSetsEntry)).isFalse();
        // No set times are stored, so the old session is finished at its start.
        assertThat(jdbc.queryForObject("select finished_at from workout_sessions where id = ?",
                Timestamp.class, withSets).toLocalDateTime())
                .isEqualTo(LocalDateTime.parse("2026-05-03T10:00:00"));
        assertThat(jdbc.queryForObject("select count(*) from set_entries where exercise_entry_id = ?",
                Integer.class, withSetsEntry)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select finished_at from workout_sessions where id = ?",
                Timestamp.class, finished).toLocalDateTime())
                .isEqualTo(LocalDateTime.parse("2026-04-30T11:00:00"));

        assertThat(openIds(jdbc, 2)).containsExactly(tieHigh);
        assertThat(exists(jdbc, "workout_sessions", tieLow)).isFalse();

        assertThat(openIds(jdbc, 3)).containsExactly(alone);

        // From now on the database itself refuses a second open session.
        assertThatThrownBy(() -> session(jdbc, 3, "2026-05-07T10:00:00", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        session(jdbc, 3, "2026-05-01T10:00:00", "2026-05-01T11:00:00");
        assertThat(jdbc.queryForObject("select generation_expression is not null and extra like '%STORED%'"
                + " from information_schema.columns where table_schema = database()"
                + " and table_name = 'workout_sessions' and column_name = 'open_owner_id'", Boolean.class))
                .isTrue();
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private static long session(JdbcTemplate jdbc, long ownerUserId, String startedAt, String finishedAt) {
        return insert(jdbc, "insert into workout_sessions (owner_user_id, started_at, finished_at) values (?, ?, ?)",
                ownerUserId, Timestamp.valueOf(LocalDateTime.parse(startedAt)),
                finishedAt == null ? null : Timestamp.valueOf(LocalDateTime.parse(finishedAt)));
    }

    private static long entry(JdbcTemplate jdbc, long sessionId, long exerciseId) {
        return insert(jdbc, "insert into exercise_entries (workout_session_id, exercise_id, order_index) values (?, ?, 0)",
                sessionId, exerciseId);
    }

    private static void set(JdbcTemplate jdbc, long entryId, int setNumber) {
        insert(jdbc, "insert into set_entries (exercise_entry_id, set_number, reps, weight, completed)"
                + " values (?, ?, 5, 100.00, 1)", entryId, setNumber);
    }

    private static long insert(JdbcTemplate jdbc, String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private static List<Long> openIds(JdbcTemplate jdbc, long ownerUserId) {
        return jdbc.queryForList("select id from workout_sessions where owner_user_id = ? and finished_at is null",
                Long.class, ownerUserId);
    }

    private static boolean exists(JdbcTemplate jdbc, String table, long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select count(*) > 0 from " + table + " where id = ?", Boolean.class, id));
    }
}
