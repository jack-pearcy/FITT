package com.fitt.identity.controller;

import com.fitt.identity.repository.UserRepository;
import com.fitt.identity.service.AuthTokens;
import com.fitt.identity.service.IdentityAuthService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Changing the password end to end: real tokens, the real security chain and a
// real database, because the point is which tokens still work afterwards.
// No test-level transaction on purpose, so the change commits like it does live.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordChangeTest {

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
    void changePassword_swapsWhichPasswordSignsIn() throws Exception {
        Account account = register();
        String next = newPassword();

        change(account, account.id(), account.password(), next).andExpect(status().isOk());

        login(account.email(), account.password()).andExpect(status().isUnauthorized());
        login(account.email(), next).andExpect(status().isOk());
    }

    @Test
    void changePassword_endsEverySessionThatCameBeforeIt() throws Exception {
        Account account = register();
        // A second device, signed in on its own.
        AuthTokens otherDevice = authService.login(account.email(), account.password());

        change(account, account.id(), account.password(), newPassword()).andExpect(status().isOk());

        // Both access tokens are inside their hour and correctly signed.
        onboardingStatus(account.id(), account.tokens().accessToken()).andExpect(status().isUnauthorized());
        onboardingStatus(account.id(), otherDevice.accessToken()).andExpect(status().isUnauthorized());
        refresh(account.tokens().refreshToken()).andExpect(status().isUnauthorized());
        refresh(otherDevice.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_answersWithTokensThatKeepTheCallerSignedIn() throws Exception {
        Account account = register();

        String body = change(account, account.id(), account.password(), newPassword())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        onboardingStatus(account.id(), JsonPath.read(body, "$.token")).andExpect(status().isOk());
        refresh(JsonPath.read(body, "$.refreshToken")).andExpect(status().isOk());
        // Exactly one login is left: the one just handed back, rotated once by the refresh above.
        assertThat(jdbc.queryForObject(
                "select count(distinct family_id) from refresh_tokens where owner_user_id = ?",
                Integer.class, account.id())).isEqualTo(1);
    }

    @Test
    void changePassword_leavesOtherAccountsAlone() throws Exception {
        Account account = register();
        Account bystander = register();

        change(account, account.id(), account.password(), newPassword()).andExpect(status().isOk());

        onboardingStatus(bystander.id(), bystander.tokens().accessToken()).andExpect(status().isOk());
        refresh(bystander.tokens().refreshToken()).andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPassword_is400UnderThatField_andChangesNothing() throws Exception {
        Account account = register();

        change(account, account.id(), "not-" + account.password(), newPassword())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.currentPassword").value("Password is incorrect"));

        login(account.email(), account.password()).andExpect(status().isOk());
        onboardingStatus(account.id(), account.tokens().accessToken()).andExpect(status().isOk());
    }

    @Test
    void newPasswordTooShort_is400UnderThatField_andChangesNothing() throws Exception {
        Account account = register();

        change(account, account.id(), account.password(), "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.newPassword").exists());

        login(account.email(), account.password()).andExpect(status().isOk());
    }

    @Test
    void newPasswordSameAsCurrent_is400_andKeepsTheSessions() throws Exception {
        Account account = register();

        change(account, account.id(), account.password(), account.password())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("New password must be different from the current one"));

        onboardingStatus(account.id(), account.tokens().accessToken()).andExpect(status().isOk());
    }

    @Test
    void newPasswordOver72Bytes_is400() throws Exception {
        Account account = register();
        // 30 characters, 90 bytes: inside the character limit, past what BCrypt reads.
        String multibyte = "€".repeat(30);

        change(account, account.id(), account.password(), multibyte)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Password must be 72 bytes or fewer"));

        login(account.email(), account.password()).andExpect(status().isOk());
    }

    @Test
    void anotherUsersAccount_is403_evenWithThatUsersPassword() throws Exception {
        Account caller = register();
        Account target = register();

        change(caller, target.id(), target.password(), newPassword()).andExpect(status().isForbidden());

        login(target.email(), target.password()).andExpect(status().isOk());
    }

    @Test
    void aTokenWithNoVersionClaim_countsAsVersionZero() throws Exception {
        // What a token issued before this change looks like to the check: the account
        // is at version 0 and so is the token, so nobody is signed out by the deploy.
        Account account = register();

        assertThat(jdbc.queryForObject("select token_version from users where id = ?",
                Integer.class, account.id())).isZero();
        onboardingStatus(account.id(), account.tokens().accessToken()).andExpect(status().isOk());
    }

    private ResultActions change(Account caller, long pathUserId, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/users/{userId}/identity/account/password", pathUserId)
                .header("Authorization", "Bearer " + caller.tokens().accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/identity/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/identity/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    // Any signed in route will do. This one answers 200 for a new account.
    private ResultActions onboardingStatus(long userId, String accessToken) throws Exception {
        return mockMvc.perform(get("/api/users/{userId}/identity/onboarding/status", userId)
                .header("Authorization", "Bearer " + accessToken));
    }

    private Account register() {
        String email = "password-" + UUID.randomUUID() + "@example.com";
        String password = newPassword();
        AuthTokens tokens = authService.register(email, password);
        return new Account(userRepository.findByEmail(email).orElseThrow().getId(), email, password, tokens);
    }

    private static String newPassword() {
        return "pw-" + UUID.randomUUID();
    }
}
