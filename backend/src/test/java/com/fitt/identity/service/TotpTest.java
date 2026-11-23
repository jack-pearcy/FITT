package com.fitt.identity.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// The code algorithm against the values the RFCs publish, so it is checked
// against the same numbers every authenticator app was checked against.
class TotpTest {

    // The SHA-1 test secret of RFC 6238 appendix B.
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    // RFC 6238 appendix B lists eight digits. The last six are what a six-digit
    // code is, because the code is the same number taken modulo a smaller power of ten.
    @ParameterizedTest
    @CsvSource({
            "59, 287082",
            "1111111109, 081804",
            "1111111111, 050471",
            "1234567890, 005924",
            "2000000000, 279037",
            "20000000000, 353130"
    })
    void codeAt_matchesTheRfc6238TestValues(long epochSecond, String expected) {
        long step = Totp.stepAt(Instant.ofEpochSecond(epochSecond));

        assertThat(Totp.codeAt(RFC_SECRET, step)).isEqualTo(expected);
    }

    @Test
    void matchingStep_acceptsTheStepBeforeAndAfterNow_andNothingFurther() {
        Instant now = Instant.ofEpochSecond(1_234_567_890);
        long current = Totp.stepAt(now);

        assertThat(Totp.matchingStep(RFC_SECRET, Totp.codeAt(RFC_SECRET, current - 1), now, 0)).isEqualTo(current - 1);
        assertThat(Totp.matchingStep(RFC_SECRET, Totp.codeAt(RFC_SECRET, current), now, 0)).isEqualTo(current);
        assertThat(Totp.matchingStep(RFC_SECRET, Totp.codeAt(RFC_SECRET, current + 1), now, 0)).isEqualTo(current + 1);
        assertThat(Totp.matchingStep(RFC_SECRET, Totp.codeAt(RFC_SECRET, current - 2), now, 0)).isEqualTo(-1);
        assertThat(Totp.matchingStep(RFC_SECRET, Totp.codeAt(RFC_SECRET, current + 2), now, 0)).isEqualTo(-1);
    }

    @Test
    void matchingStep_refusesACodeWhoseStepWasAlreadyUsed() {
        Instant now = Instant.ofEpochSecond(1_234_567_890);
        long current = Totp.stepAt(now);
        String code = Totp.codeAt(RFC_SECRET, current);

        assertThat(Totp.matchingStep(RFC_SECRET, code, now, current)).isEqualTo(-1);
        assertThat(Totp.matchingStep(RFC_SECRET, code, now, current - 1)).isEqualTo(current);
    }

    @Test
    void matchingStep_refusesInputThatIsNotSixDigits() {
        Instant now = Instant.ofEpochSecond(1_234_567_890);

        assertThat(Totp.matchingStep(RFC_SECRET, "", now, 0)).isEqualTo(-1);
        assertThat(Totp.matchingStep(RFC_SECRET, "12345", now, 0)).isEqualTo(-1);
        assertThat(Totp.matchingStep(RFC_SECRET, "0059240", now, 0)).isEqualTo(-1);
    }

    // RFC 4648 section 10, with the padding left off.
    @ParameterizedTest
    @CsvSource({
            "f, MY",
            "fo, MZXQ",
            "foo, MZXW6",
            "foob, MZXW6YQ",
            "fooba, MZXW6YTB",
            "foobar, MZXW6YTBOI"
    })
    void base32_matchesTheRfc4648TestValues(String text, String expected) {
        assertThat(Totp.base32(text.getBytes(StandardCharsets.US_ASCII))).isEqualTo(expected);
    }
}
