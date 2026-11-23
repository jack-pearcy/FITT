package com.fitt.identity.controller;

import com.fitt.identity.repository.UserRepository;
import com.fitt.identity.service.AuthTokens;
import com.fitt.identity.service.IdentityAuthService;
import com.fitt.identity.service.Totp;
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Two-step sign-in end to end: real tokens, the real security chain and a real
// database, because the point is what a password alone can and cannot do once
// it is on. The test plays the authenticator app: it reads the secret the
// server hands out and works the codes out from it. Time is a clock the test
// moves, since a code depends on the time and a lock ends with it.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MfaFlowTest {

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MovableClock testClock() {
            return new MovableClock(Instant.parse("2026-11-22T12:00:00Z"));
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

    // An account with two-step sign-in on: the authenticator's secret and the
    // recovery codes the user was shown.
    private record Protected(Account account, byte[] secret, List<String> recoveryCodes) {
    }

    @Test
    void anAccountWithoutIt_signsInExactlyAsBefore() throws Exception {
        Account account = register();

        login(account)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.mfaRequired").doesNotExist())
                .andExpect(jsonPath("$.mfaToken").doesNotExist());

        mfaStatus(account)
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.totpEnabled").value(false))
                .andExpect(jsonPath("$.recoveryCodesLeft").value(0));
    }

    @Test
    void setup_handsOutASecretAndTheLinkForAQrCode() throws Exception {
        Account account = register();

        String body = setup(account, account.password())
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();

        String secret = JsonPath.read(body, "$.secret");
        String uri = JsonPath.read(body, "$.otpauthUri");
        // 20 bytes in Base32.
        assertThat(secret).matches("[A-Z2-7]{32}");
        assertThat(uri).startsWith("otpauth://totp/FITT%3A")
                .contains("secret=" + secret)
                .contains("issuer=FITT")
                .contains("digits=6")
                .contains("period=30");
    }

    @Test
    void setupThatWasNeverConfirmed_changesNothingAboutSigningIn() throws Exception {
        Account account = register();
        setup(account, account.password()).andExpect(status().isOk());

        login(account).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mfaStatus(account).andExpect(jsonPath("$.totpEnabled").value(false));
    }

    @Test
    void confirm_turnsItOn_andHandsOverTenRecoveryCodesOnce() throws Exception {
        Protected user = protectedAccount();

        assertThat(user.recoveryCodes()).hasSize(10).doesNotHaveDuplicates()
                .allMatch(code -> code.matches("[A-HJ-NP-Z2-9]{4}(-[A-HJ-NP-Z2-9]{4}){3}"));
        mfaStatus(user.account())
                .andExpect(jsonPath("$.totpEnabled").value(true))
                .andExpect(jsonPath("$.recoveryCodesLeft").value(10));
    }

    @Test
    void confirm_withAWrongCode_leavesItOff() throws Exception {
        Account account = register();
        byte[] secret = secretFrom(setup(account, account.password()));

        confirm(account, wrongCode(secret))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.code").value("Code is incorrect"));

        mfaStatus(account).andExpect(jsonPath("$.totpEnabled").value(false));
        login(account).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void neitherTheSecretNorTheRecoveryCodes_areStoredReadable() throws Exception {
        Account account = register();
        String body = setup(account, account.password()).andReturn().getResponse().getContentAsString();
        String shownSecret = JsonPath.read(body, "$.secret");
        byte[] secret = base32Decode(shownSecret);
        List<String> codes = JsonPath.read(confirm(account, nextCode(secret))
                .andReturn().getResponse().getContentAsString(), "$.recoveryCodes");

        byte[] stored = jdbc.queryForObject(
                "select secret_encrypted from user_totp where user_id = ?", byte[].class, account.id());
        assertThat(stored).hasSize(48);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1))
                .doesNotContain(new String(secret, StandardCharsets.ISO_8859_1))
                .doesNotContain(shownSecret);

        List<String> hashes = jdbc.queryForList(
                "select code_hash from mfa_recovery_codes where user_id = ?", String.class, account.id());
        assertThat(hashes).hasSize(10).allMatch(hash -> hash.matches("[0-9a-f]{64}"));
        assertThat(hashes).doesNotContainAnyElementsOf(codes);
    }

    @Test
    void theCorrectPasswordAlone_buysNoToken() throws Exception {
        Protected user = protectedAccount();

        login(user.account())
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.mfaToken").isNotEmpty())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void theSecondStepToken_isNotAnAccessToken() throws Exception {
        Protected user = protectedAccount();
        String mfaToken = challenge(user.account());

        onboardingStatus(user.account().id(), mfaToken).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordThenCode_signsIn() throws Exception {
        Protected user = protectedAccount();
        String mfaToken = challenge(user.account());

        String body = secondStep(mfaToken, nextCode(user.secret()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        onboardingStatus(user.account().id(), JsonPath.read(body, "$.token")).andExpect(status().isOk());
    }

    @Test
    void aCodeTypedTheWayTheAppShowsIt_isAccepted() throws Exception {
        Protected user = protectedAccount();
        String code = nextCode(user.secret());
        String spaced = code.substring(0, 3) + " " + code.substring(3);

        secondStep(challenge(user.account()), spaced).andExpect(status().isOk());
    }

    @Test
    void theSameCode_doesNotWorkTwice() throws Exception {
        Protected user = protectedAccount();
        String code = nextCode(user.secret());
        secondStep(challenge(user.account()), code).andExpect(status().isOk());

        // Still inside its 30 seconds, and correct, but already used.
        secondStep(challenge(user.account()), code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.code").value("Code is incorrect"));
    }

    @Test
    void oneChallenge_buysOneSignIn() throws Exception {
        Protected user = protectedAccount();
        String mfaToken = challenge(user.account());
        secondStep(mfaToken, nextCode(user.secret())).andExpect(status().isOk());

        secondStep(mfaToken, nextCode(user.secret())).andExpect(status().isUnauthorized());
    }

    @Test
    void aChallengeOlderThanFiveMinutes_sendsTheUserBackToThePassword() throws Exception {
        Protected user = protectedAccount();
        String mfaToken = challenge(user.account());

        clock.advance(Duration.ofMinutes(5));

        secondStep(mfaToken, nextCode(user.secret()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Sign-in step expired, sign in again"));
    }

    @Test
    void anUnknownChallenge_is401() throws Exception {
        secondStep("not-a-token-the-server-ever-issued", "123456").andExpect(status().isUnauthorized());
    }

    @Test
    void aWrongCode_keepsTheChallenge_soATypoDoesNotCostThePassword() throws Exception {
        Protected user = protectedAccount();
        String mfaToken = challenge(user.account());

        secondStep(mfaToken, wrongCode(user.secret())).andExpect(status().isBadRequest());
        secondStep(mfaToken, nextCode(user.secret())).andExpect(status().isOk());
    }

    @Test
    void fiveWrongCodesInARow_lockTheSecondStep_evenForTheRightCode() throws Exception {
        Protected user = protectedAccount();
        String mfaToken = challenge(user.account());
        String wrong = wrongCode(user.secret());

        for (int i = 0; i < 5; i++) {
            secondStep(mfaToken, wrong).andExpect(status().isBadRequest());
        }

        secondStep(mfaToken, currentCode(user.secret()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        // The lock is on the account, so a new challenge does not get around it.
        secondStep(challenge(user.account()), currentCode(user.secret())).andExpect(status().isTooManyRequests());
        // Nor does a recovery code.
        secondStep(challenge(user.account()), user.recoveryCodes().get(0)).andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofMinutes(15));

        secondStep(challenge(user.account()), currentCode(user.secret())).andExpect(status().isOk());
    }

    @Test
    void aCorrectCode_clearsTheCountOfWrongOnes() throws Exception {
        Protected user = protectedAccount();
        String wrong = wrongCode(user.secret());

        // Four wrong, one right, four wrong: never five in a row, so never locked.
        String first = challenge(user.account());
        for (int i = 0; i < 4; i++) {
            secondStep(first, wrong).andExpect(status().isBadRequest());
        }
        secondStep(first, nextCode(user.secret())).andExpect(status().isOk());

        String second = challenge(user.account());
        for (int i = 0; i < 4; i++) {
            secondStep(second, wrongCode(user.secret())).andExpect(status().isBadRequest());
        }
        secondStep(second, nextCode(user.secret())).andExpect(status().isOk());
    }

    @Test
    void aRecoveryCode_signsInOnce() throws Exception {
        Protected user = protectedAccount();
        String code = user.recoveryCodes().get(3);

        secondStep(challenge(user.account()), code).andExpect(status().isOk());
        mfaStatus(user.account()).andExpect(jsonPath("$.recoveryCodesLeft").value(9));

        secondStep(challenge(user.account()), code).andExpect(status().isBadRequest());
    }

    @Test
    void aRecoveryCode_isAcceptedInLowerCaseAndWithoutDashes() throws Exception {
        Protected user = protectedAccount();
        String typed = user.recoveryCodes().get(0).replace("-", "").toLowerCase();

        secondStep(challenge(user.account()), typed).andExpect(status().isOk());
    }

    @Test
    void anotherUsersRecoveryCode_doesNotWork() throws Exception {
        Protected user = protectedAccount();
        Protected other = protectedAccount();

        secondStep(challenge(user.account()), other.recoveryCodes().get(0)).andExpect(status().isBadRequest());
    }

    @Test
    void newRecoveryCodes_replaceEveryOldOne() throws Exception {
        Protected user = protectedAccount();
        String old = user.recoveryCodes().get(0);

        String body = passwordAndCode(user.account(), "recovery-codes", user.account().password(), nextCode(user.secret()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        List<String> fresh = JsonPath.read(body, "$.recoveryCodes");

        assertThat(fresh).hasSize(10).doesNotContainAnyElementsOf(user.recoveryCodes());
        secondStep(challenge(user.account()), old).andExpect(status().isBadRequest());
        secondStep(challenge(user.account()), fresh.get(0)).andExpect(status().isOk());
    }

    @Test
    void turningItOff_needsThePasswordAndACode() throws Exception {
        Protected user = protectedAccount();
        Account account = user.account();

        passwordAndCode(account, "totp/disable", "not-" + account.password(), nextCode(user.secret()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.password").value("Password is incorrect"));
        passwordAndCode(account, "totp/disable", account.password(), wrongCode(user.secret()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.code").value("Code is incorrect"));
        mfaStatus(account).andExpect(jsonPath("$.totpEnabled").value(true));

        passwordAndCode(account, "totp/disable", account.password(), nextCode(user.secret()))
                .andExpect(status().isNoContent());

        mfaStatus(account).andExpect(jsonPath("$.totpEnabled").value(false));
        login(account).andExpect(jsonPath("$.token").isNotEmpty());
        assertThat(rowsFor(account.id())).isZero();
    }

    @Test
    void turningItOffBetweenTheTwoSteps_endsTheWaitingChallenge() throws Exception {
        Protected user = protectedAccount();
        String mfaToken = challenge(user.account());

        passwordAndCode(user.account(), "totp/disable", user.account().password(), nextCode(user.secret()))
                .andExpect(status().isNoContent());

        secondStep(mfaToken, nextCode(user.secret())).andExpect(status().isUnauthorized());
    }

    @Test
    void setup_asksForThePasswordAgain() throws Exception {
        Account account = register();

        setup(account, "not-" + account.password())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.password").value("Password is incorrect"));

        assertThat(rowsFor(account.id())).isZero();
    }

    @Test
    void setup_whenItIsAlreadyOn_is409_andKeepsTheWorkingSecret() throws Exception {
        Protected user = protectedAccount();

        setup(user.account(), user.account().password())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Two-step sign-in is already on"));

        secondStep(challenge(user.account()), nextCode(user.secret())).andExpect(status().isOk());
    }

    @Test
    void startingSetupAgain_replacesTheUnconfirmedSecret() throws Exception {
        Account account = register();
        byte[] abandoned = secretFrom(setup(account, account.password()));
        byte[] current = secretFrom(setup(account, account.password()));

        assertThat(abandoned).isNotEqualTo(current);
        confirm(account, nextCode(abandoned)).andExpect(status().isBadRequest());
        confirm(account, nextCode(current)).andExpect(status().isOk());
    }

    @Test
    void confirm_withoutSetup_is409() throws Exception {
        Account account = register();

        confirm(account, "123456")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Start setup first"));
    }

    @Test
    void anotherUsersSettings_are403() throws Exception {
        Account caller = register();
        Protected target = protectedAccount();
        long targetId = target.account().id();

        mockMvc.perform(get("/api/users/{userId}/identity/mfa", targetId)
                        .header("Authorization", "Bearer " + caller.tokens().accessToken()))
                .andExpect(status().isForbidden());
        settingsPost(caller, targetId, "totp/setup", "{\"password\":\"" + target.account().password() + "\"}")
                .andExpect(status().isForbidden());
        settingsPost(caller, targetId, "totp/disable", "{\"password\":\"" + target.account().password()
                + "\",\"code\":\"" + nextCode(target.secret()) + "\"}")
                .andExpect(status().isForbidden());

        mfaStatus(target.account()).andExpect(jsonPath("$.totpEnabled").value(true));
    }

    @Test
    void theSettings_needASignedInUser() throws Exception {
        Account account = register();

        mockMvc.perform(get("/api/users/{userId}/identity/mfa", account.id()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/users/{userId}/identity/mfa/totp/setup", account.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + account.password() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletingTheAccount_removesItsTwoStepRows_andNobodyElses() throws Exception {
        Protected user = protectedAccount();
        Protected bystander = protectedAccount();
        challenge(user.account());
        assertThat(rowsFor(user.account().id())).isEqualTo(12);

        mockMvc.perform(post("/api/users/{userId}/identity/account/delete", user.account().id())
                        .header("Authorization", "Bearer " + user.account().tokens().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + user.account().password() + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(rowsFor(user.account().id())).isZero();
        assertThat(rowsFor(bystander.account().id())).isEqualTo(11);
    }

    @Test
    void emptyInput_is400UnderTheField() throws Exception {
        Account account = register();

        settingsPost(account, account.id(), "totp/confirm", "{\"code\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.code").exists());
        mockMvc.perform(post("/api/identity/auth/mfa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.mfaToken").exists());
    }

    // ---- what the user and the authenticator app do ----

    private Protected protectedAccount() throws Exception {
        Account account = register();
        byte[] secret = secretFrom(setup(account, account.password()));
        String body = confirm(account, nextCode(secret))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        return new Protected(account, secret, JsonPath.read(body, "$.recoveryCodes"));
    }

    // A code can be used once, so every use moves the clock on by one step first,
    // the way a user waits for the app to show the next one.
    private String nextCode(byte[] secret) {
        clock.advance(Duration.ofSeconds(Totp.PERIOD_SECONDS));
        return currentCode(secret);
    }

    private String currentCode(byte[] secret) {
        return Totp.codeAt(secret, Totp.stepAt(clock.instant()));
    }

    // Six digits that are certainly not one of the codes accepted right now.
    private String wrongCode(byte[] secret) {
        long step = Totp.stepAt(clock.instant());
        Set<String> valid = new HashSet<>();
        for (long s = step - 2; s <= step + 2; s++) {
            valid.add(Totp.codeAt(secret, s));
        }
        for (int candidate = 0; ; candidate++) {
            String code = String.format("%06d", candidate);
            if (!valid.contains(code)) {
                return code;
            }
        }
    }

    private byte[] secretFrom(ResultActions setup) throws Exception {
        String body = setup.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return base32Decode(JsonPath.read(body, "$.secret"));
    }

    // What an authenticator app does with the text it is given.
    private static byte[] base32Decode(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : text.toCharArray()) {
            buffer = (buffer << 5) | "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private String challenge(Account account) throws Exception {
        String body = login(account).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.mfaToken");
    }

    // ---- requests ----

    private ResultActions login(Account account) throws Exception {
        return mockMvc.perform(post("/api/identity/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + account.email() + "\",\"password\":\"" + account.password() + "\"}"));
    }

    private ResultActions secondStep(String mfaToken, String code) throws Exception {
        return mockMvc.perform(post("/api/identity/auth/mfa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mfaToken\":\"" + mfaToken + "\",\"code\":\"" + code + "\"}"));
    }

    private ResultActions mfaStatus(Account account) throws Exception {
        return mockMvc.perform(get("/api/users/{userId}/identity/mfa", account.id())
                .header("Authorization", "Bearer " + account.tokens().accessToken()));
    }

    private ResultActions setup(Account account, String password) throws Exception {
        return settingsPost(account, account.id(), "totp/setup", "{\"password\":\"" + password + "\"}");
    }

    private ResultActions confirm(Account account, String code) throws Exception {
        return settingsPost(account, account.id(), "totp/confirm", "{\"code\":\"" + code + "\"}");
    }

    private ResultActions passwordAndCode(Account account, String path, String password, String code) throws Exception {
        return settingsPost(account, account.id(), path, "{\"password\":\"" + password + "\",\"code\":\"" + code + "\"}");
    }

    private ResultActions settingsPost(Account caller, long pathUserId, String path, String json) throws Exception {
        return mockMvc.perform(post("/api/users/{userId}/identity/mfa/" + path, pathUserId)
                .header("Authorization", "Bearer " + caller.tokens().accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // Any signed in route will do. This one answers 200 for a new account.
    private ResultActions onboardingStatus(long userId, String accessToken) throws Exception {
        return mockMvc.perform(get("/api/users/{userId}/identity/onboarding/status", userId)
                .header("Authorization", "Bearer " + accessToken));
    }

    // Every two-step row held for a user, across the three tables.
    private int rowsFor(long userId) {
        return jdbc.queryForObject("select (select count(*) from user_totp where user_id = ?)"
                        + " + (select count(*) from mfa_recovery_codes where user_id = ?)"
                        + " + (select count(*) from mfa_challenges where user_id = ?)",
                Integer.class, userId, userId, userId);
    }

    private Account register() {
        String email = "mfa-" + UUID.randomUUID() + "@example.com";
        String password = "pw-" + UUID.randomUUID();
        AuthTokens tokens = authService.register(email, password);
        return new Account(userRepository.findByEmail(email).orElseThrow().getId(), email, password, tokens);
    }
}
