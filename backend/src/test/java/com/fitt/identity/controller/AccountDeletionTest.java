package com.fitt.identity.controller;

import com.fitt.identity.repository.UserRepository;
import com.fitt.identity.service.AuthTokens;
import com.fitt.identity.service.IdentityAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Account deletion end to end: real tokens, the real security chain and a real
// database, because the whole point is which rows are left afterwards.
// No test-level transaction on purpose, so the deletion commits like it does live.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountDeletionTest {

    // The tables whose rows hang off another table and carry no owner column.
    private static final Map<String, String> CHILD_COUNTS = Map.of(
            "workout_plan_days", "select count(*) from workout_plan_days d "
                    + "join workout_plans p on p.id = d.workout_plan_id where p.owner_user_id = ?",
            "plan_day_exercises", "select count(*) from plan_day_exercises x "
                    + "join workout_plan_days d on d.id = x.workout_plan_day_id "
                    + "join workout_plans p on p.id = d.workout_plan_id where p.owner_user_id = ?",
            "exercise_entries", "select count(*) from exercise_entries e "
                    + "join workout_sessions s on s.id = e.workout_session_id where s.owner_user_id = ?",
            "set_entries", "select count(*) from set_entries t "
                    + "join exercise_entries e on e.id = t.exercise_entry_id "
                    + "join workout_sessions s on s.id = e.workout_session_id where s.owner_user_id = ?");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityAuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private record Account(long id, String email, String password, AuthTokens tokens) {
    }

    @Test
    void deleteAccount_removesEveryRowOfThatUser_andNoneOfAnyoneElses() throws Exception {
        Account leaving = registerWithData();
        Account staying = registerWithData();
        Map<String, Integer> stayingBefore = rowCounts(staying.id());
        int libraryBefore = libraryExercises();

        // Every table is filled, so an empty table afterwards means something.
        assertThat(rowCounts(leaving.id()).values()).allMatch(count -> count > 0);

        delete(leaving, leaving.id(), leaving.password()).andExpect(status().isNoContent());

        assertThat(rowCounts(leaving.id()).values()).containsOnly(0);
        assertThat(userRepository.existsById(leaving.id())).isFalse();
        assertThat(rowCounts(staying.id())).isEqualTo(stayingBefore);
        assertThat(userRepository.existsById(staying.id())).isTrue();
        assertThat(libraryExercises()).isEqualTo(libraryBefore);
    }

    @Test
    void afterDeletion_everyCredentialOfTheAccountStopsWorking() throws Exception {
        Account account = registerWithData();
        String goals = "/api/users/" + account.id() + "/identity/goals";
        mockMvc.perform(get(goals).header("Authorization", bearer(account)))
                .andExpect(status().isOk());

        delete(account, account.id(), account.password()).andExpect(status().isNoContent());

        // The access token is still inside its hour and still correctly signed.
        mockMvc.perform(get(goals).header("Authorization", bearer(account)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/identity/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + account.tokens().refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/identity/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(account.email(), account.password())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void afterDeletion_theEmailCanBeRegisteredAgain_andStartsEmpty() throws Exception {
        Account account = registerWithData();
        delete(account, account.id(), account.password()).andExpect(status().isNoContent());

        authService.register(account.email(), account.password());

        long newId = userRepository.findByEmail(account.email()).orElseThrow().getId();
        assertThat(newId).isNotEqualTo(account.id());
        Map<String, Integer> counts = rowCounts(newId);
        // Registering signs the user in, so one refresh token is expected.
        assertThat(counts.remove("refresh_tokens")).isEqualTo(1);
        assertThat(counts.values()).containsOnly(0);
    }

    @Test
    void wrongPassword_is400UnderThePasswordField_andDeletesNothing() throws Exception {
        Account account = registerWithData();
        Map<String, Integer> before = rowCounts(account.id());

        delete(account, account.id(), "not-" + account.password())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.password").value("Password is incorrect"));

        assertThat(rowCounts(account.id())).isEqualTo(before);
        assertThat(userRepository.existsById(account.id())).isTrue();
    }

    @Test
    void missingPassword_is400() throws Exception {
        Account account = registerWithData();

        delete(account, account.id(), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.password").value("Password is required"));

        assertThat(userRepository.existsById(account.id())).isTrue();
    }

    @Test
    void anotherUsersAccount_is403_evenWithThatUsersPassword() throws Exception {
        Account caller = registerWithData();
        Account target = registerWithData();
        Map<String, Integer> before = rowCounts(target.id());

        delete(caller, target.id(), target.password()).andExpect(status().isForbidden());

        assertThat(rowCounts(target.id())).isEqualTo(before);
    }

    @Test
    void noToken_is401() throws Exception {
        Account account = registerWithData();

        mockMvc.perform(post("/api/users/{userId}/identity/account/delete", account.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + account.password() + "\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.existsById(account.id())).isTrue();
    }

    private ResultActions delete(Account caller, long pathUserId, String password) throws Exception {
        return mockMvc.perform(post("/api/users/{userId}/identity/account/delete", pathUserId)
                .header("Authorization", bearer(caller))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}"));
    }

    private static String bearer(Account account) {
        return "Bearer " + account.tokens().accessToken();
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    // A new account with at least one row in every table that can hold a user's data.
    // Rows are written with SQL so the test does not depend on how any service builds them.
    private Account registerWithData() {
        String email = "delete-" + UUID.randomUUID() + "@example.com";
        String password = "pw-" + UUID.randomUUID();
        AuthTokens tokens = authService.register(email, password);
        long id = userRepository.findByEmail(email).orElseThrow().getId();

        jdbc.update("insert into user_biometrics (owner_user_id, height_cm, weight_kg, date_of_birth, "
                + "biological_sex, activity_level, units_preference, created_at, updated_at) "
                + "values (?, 180, 80, '1990-05-01', 'MALE', 'ACTIVE', 'METRIC', now(), now())", id);
        jdbc.update("insert into user_goals (owner_user_id, goal_direction, calorie_goal_mode, calorie_goal, "
                + "created_at, updated_at) values (?, 'CUT', 'MANUAL', 2200, now(), now())", id);
        jdbc.update("insert into user_progression_preferences (owner_user_id, progression_model) "
                + "values (?, 'DOUBLE_PROGRESSION')", id);
        jdbc.update("insert into body_weight_entries (owner_user_id, logged_at, weight, created_at) "
                + "values (?, '2026-11-01', 80.5, now())", id);

        jdbc.update("insert into exercises (owner_user_id, name, muscle_group, is_custom) "
                + "values (?, 'Own Curl', 'BICEPS', true)", id);
        long exercise = newest("exercises", "owner_user_id", id);

        jdbc.update("insert into workout_plans (owner_user_id, name, split_type, days_per_week, is_active) "
                + "values (?, 'Plan', 'CUSTOM', 1, true)", id);
        long plan = newest("workout_plans", "owner_user_id", id);
        jdbc.update("insert into workout_plan_days (workout_plan_id, day_label, order_index) values (?, 'Day', 0)",
                plan);
        long day = newest("workout_plan_days", "workout_plan_id", plan);
        jdbc.update("insert into plan_day_exercises (workout_plan_day_id, exercise_id, sets, reps, order_index) "
                + "values (?, ?, 3, 8, 0)", day, exercise);

        jdbc.update("insert into workout_sessions (owner_user_id, plan_id, started_at) values (?, ?, now())",
                id, plan);
        long session = newest("workout_sessions", "owner_user_id", id);
        jdbc.update("insert into exercise_entries (workout_session_id, exercise_id, order_index) values (?, ?, 0)",
                session, exercise);
        long entry = newest("exercise_entries", "workout_session_id", session);
        jdbc.update("insert into set_entries (exercise_entry_id, set_number, reps, weight, completed) "
                + "values (?, 1, 8, 60, true)", entry);

        return new Account(id, email, password, tokens);
    }

    private long newest(String table, String column, long value) {
        return jdbc.queryForObject("select max(id) from " + table + " where " + column + " = ?", Long.class, value);
    }

    private int libraryExercises() {
        return jdbc.queryForObject("select count(*) from exercises where owner_user_id is null", Integer.class);
    }

    // Rows held for one user, by table. The tables with an owner column are read
    // from the schema itself, so a table added later is counted here without anyone
    // remembering to add it, and this test fails until its rows are deleted too.
    private Map<String, Integer> rowCounts(long userId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> ownedTables = jdbc.queryForList(
                "select table_name from information_schema.columns "
                        + "where upper(column_name) = 'OWNER_USER_ID' order by table_name", String.class);
        assertThat(ownedTables).hasSizeGreaterThanOrEqualTo(8);
        for (String table : ownedTables) {
            counts.put(table.toLowerCase(), jdbc.queryForObject(
                    "select count(*) from " + table + " where owner_user_id = ?", Integer.class, userId));
        }
        CHILD_COUNTS.forEach((table, sql) -> counts.put(table, jdbc.queryForObject(sql, Integer.class, userId)));
        return counts;
    }
}
