package com.fitt.identity.controller;

import com.fitt.identity.repository.UserRepository;
import com.fitt.identity.service.AuthTokens;
import com.fitt.identity.service.IdentityAuthService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Connecting a second device end to end: real tokens, the real security chain
// and a real database. The test plays both devices: the phone that asks for the
// code and the watch that trades it in. Time is a clock the test moves, since
// the code lives for 60 seconds.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceLinkFlowTest {

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MovableClock linkTestClock() {
            return new MovableClock(Instant.parse("2026-11-23T12:00:00Z"));
        }
    }

    static class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityAuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MovableClock clock;

    private record Account(long id, String email, String password, AuthTokens tokens) {
    }

    @Test
    void theCode_buysTheSecondDeviceItsOwnSignIn() throws Exception {
        Account phone = register();

        ResultActions issued = requestCode(phone, phone.id(), phone.tokens().refreshToken())
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(60));

        ResultActions redeemed = redeem(codeFrom(issued))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        String watchAccess = read(redeemed, "$.token");
        String watchRefresh = read(redeemed, "$.refreshToken");
        assertThat(watchRefresh).isNotEqualTo(phone.tokens().refreshToken());

        // The watch's token is good for the same account.
        onboardingStatus(phone.id(), watchAccess).andExpect(status().isOk());
    }

    @Test
    void theTwoDevices_refreshAndSignOutIndependently() throws Exception {
        Account phone = register();
        String watchRefresh = read(redeem(codeFor(phone)).andExpect(status().isOk()), "$.refreshToken");

        // The watch refreshes, then signs out. Neither touches the phone.
        String nextWatchRefresh = read(refresh(watchRefresh).andExpect(status().isOk()), "$.refreshToken");
        logout(nextWatchRefresh).andExpect(status().isNoContent());
        refresh(nextWatchRefresh).andExpect(status().isUnauthorized());

        refresh(phone.tokens().refreshToken()).andExpect(status().isOk());
    }

    @Test
    void theCode_worksOnce() throws Exception {
        Account phone = register();
        String code = codeFor(phone);

        redeem(code).andExpect(status().isOk());
        redeem(code).andExpect(status().isUnauthorized());
    }

    @Test
    void theCode_stopsWorkingAfterSixtySeconds() throws Exception {
        Account phone = register();
        String code = codeFor(phone);

        clock.advance(Duration.ofSeconds(60));

        redeem(code).andExpect(status().isUnauthorized());
        assertThat(codeRows(phone.id())).isZero();
    }

    @Test
    void theCode_stillWorksJustBeforeItExpires() throws Exception {
        Account phone = register();
        String code = codeFor(phone);

        clock.advance(Duration.ofSeconds(59));

        redeem(code).andExpect(status().isOk());
    }

    @Test
    void unknown_used_andExpiredCodes_getTheSameAnswer() throws Exception {
        Account phone = register();

        String used = codeFor(phone);
        redeem(used).andExpect(status().isOk());

        String expired = codeFor(phone);
        clock.advance(Duration.ofSeconds(61));

        String unknownBody = body(redeem("not-a-code").andExpect(status().isUnauthorized()));
        String usedBody = body(redeem(used).andExpect(status().isUnauthorized()));
        String expiredBody = body(redeem(expired).andExpect(status().isUnauthorized()));

        assertThat(usedBody).isEqualTo(unknownBody);
        assertThat(expiredBody).isEqualTo(unknownBody);
    }

    @Test
    void aNewCode_replacesTheOneBefore() throws Exception {
        Account phone = register();
        String first = codeFor(phone);
        String second = codeFor(phone);

        assertThat(codeRows(phone.id())).isEqualTo(1);
        redeem(first).andExpect(status().isUnauthorized());
        redeem(second).andExpect(status().isOk());
    }

    @Test
    void onlyTheHashOfTheCode_isStored() throws Exception {
        Account phone = register();
        String code = codeFor(phone);

        String stored = jdbc.queryForObject(
                "select code_hash from device_link_codes where user_id = ?", String.class, phone.id());
        assertThat(stored).hasSize(64).isNotEqualTo(code);
        // 32 random bytes, base64url without padding.
        assertThat(code).hasSize(43);
    }

    @Test
    void anAccessTokenAlone_cannotGetACode() throws Exception {
        Account phone = register();

        requestCode(phone, phone.id(), "not-a-refresh-token").andExpect(status().isUnauthorized());
        assertThat(codeRows(phone.id())).isZero();
    }

    @Test
    void anotherAccountsRefreshToken_cannotGetACode() throws Exception {
        Account phone = register();
        Account other = register();

        requestCode(phone, phone.id(), other.tokens().refreshToken()).andExpect(status().isUnauthorized());
        assertThat(codeRows(phone.id())).isZero();
        assertThat(codeRows(other.id())).isZero();
    }

    @Test
    void aSignedOutRefreshToken_cannotGetACode() throws Exception {
        Account phone = register();
        logout(phone.tokens().refreshToken()).andExpect(status().isNoContent());

        requestCode(phone, phone.id(), phone.tokens().refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void askingForACode_doesNotUseUpTheRefreshToken() throws Exception {
        Account phone = register();
        codeFor(phone);

        refresh(phone.tokens().refreshToken()).andExpect(status().isOk());
    }

    @Test
    void aCodeCannotBeAskedFor_onAnotherUsersPath() throws Exception {
        Account phone = register();
        Account other = register();

        requestCode(phone, other.id(), phone.tokens().refreshToken()).andExpect(status().isForbidden());
        assertThat(codeRows(other.id())).isZero();
    }

    @Test
    void askingForACode_needsToBeSignedIn() throws Exception {
        Account phone = register();

        mockMvc.perform(post("/api/users/{userId}/identity/device-link", phone.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + phone.tokens().refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aMissingOrOversizedCode_isRefusedBeforeAnyLookup() throws Exception {
        redeem("").andExpect(status().isBadRequest());
        redeem("x".repeat(129)).andExpect(status().isBadRequest());
    }

    @Test
    void aPasswordChange_endsACodeThatWasWaiting() throws Exception {
        Account phone = register();
        String code = codeFor(phone);

        authService.changePassword(phone.id(), phone.password(), "pw-" + UUID.randomUUID());

        assertThat(codeRows(phone.id())).isZero();
        redeem(code).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingTheAccount_removesItsCodes() throws Exception {
        Account phone = register();
        String code = codeFor(phone);

        mockMvc.perform(post("/api/users/{userId}/identity/account/delete", phone.id())
                        .header("Authorization", "Bearer " + phone.tokens().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + phone.password() + "\"}"))
                .andExpect(status().is2xxSuccessful());

        assertThat(codeRows(phone.id())).isZero();
        redeem(code).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String codeFor(Account phone) throws Exception {
        return codeFrom(requestCode(phone, phone.id(), phone.tokens().refreshToken()).andExpect(status().isOk()));
    }

    private String codeFrom(ResultActions issued) throws Exception {
        return read(issued, "$.code");
    }

    private ResultActions requestCode(Account caller, long pathUserId, String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/users/{userId}/identity/device-link", pathUserId)
                .header("Authorization", "Bearer " + caller.tokens().accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private ResultActions redeem(String code) throws Exception {
        return mockMvc.perform(post("/api/identity/auth/device-link")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/identity/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/identity/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private ResultActions onboardingStatus(long userId, String accessToken) throws Exception {
        return mockMvc.perform(get("/api/users/{userId}/identity/onboarding/status", userId)
                .header("Authorization", "Bearer " + accessToken));
    }

    private static String read(ResultActions result, String path) throws Exception {
        return JsonPath.read(body(result), path);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private int codeRows(long userId) {
        Integer rows = jdbc.queryForObject(
                "select count(*) from device_link_codes where user_id = ?", Integer.class, userId);
        return rows == null ? 0 : rows;
    }

    private Account register() {
        String email = "link-" + UUID.randomUUID() + "@example.com";
        String password = "pw-" + UUID.randomUUID();
        AuthTokens tokens = authService.register(email, password);
        return new Account(userRepository.findByEmail(email).orElseThrow().getId(), email, password, tokens);
    }
}
