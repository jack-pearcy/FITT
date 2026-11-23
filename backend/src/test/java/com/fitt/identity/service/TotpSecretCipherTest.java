package com.fitt.identity.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpSecretCipherTest {

    private static final String KEY =
            Base64.getEncoder().encodeToString("a-test-key-of-exactly-32-bytes!!".getBytes(StandardCharsets.US_ASCII));
    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void whatIsEncrypted_decryptsToTheSameBytes() {
        TotpSecretCipher cipher = new TotpSecretCipher(KEY);

        assertThat(cipher.decrypt(7L, cipher.encrypt(7L, SECRET))).isEqualTo(SECRET);
    }

    @Test
    void theStoredBytes_doNotContainTheSecret_andDifferEachTime() {
        TotpSecretCipher cipher = new TotpSecretCipher(KEY);

        byte[] first = cipher.encrypt(7L, SECRET);
        byte[] second = cipher.encrypt(7L, SECRET);

        assertThat(new String(first, StandardCharsets.ISO_8859_1)).doesNotContain("12345678901234567890");
        // A fresh nonce each time, so equal secrets do not look equal in the table.
        assertThat(first).isNotEqualTo(second);
        // 12 bytes of nonce, the 20 of the secret, 16 of tag. Fits the 128 byte column.
        assertThat(first).hasSize(48);
    }

    @Test
    void aRowMovedToAnotherUser_doesNotOpen() {
        TotpSecretCipher cipher = new TotpSecretCipher(KEY);
        byte[] stored = cipher.encrypt(7L, SECRET);

        assertThatThrownBy(() -> cipher.decrypt(8L, stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aChangedByte_doesNotOpen() {
        TotpSecretCipher cipher = new TotpSecretCipher(KEY);
        byte[] stored = cipher.encrypt(7L, SECRET);
        stored[stored.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(7L, stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anotherKey_doesNotOpen() {
        byte[] stored = new TotpSecretCipher(KEY).encrypt(7L, SECRET);
        String otherKey = Base64.getEncoder()
                .encodeToString("b-test-key-of-exactly-32-bytes!!".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> new TotpSecretCipher(otherKey).decrypt(7L, stored))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void noKey_meansSwitchedOff_notAStartupFailure() {
        assertThat(new TotpSecretCipher("").isConfigured()).isFalse();
        assertThat(new TotpSecretCipher(null).isConfigured()).isFalse();
        assertThat(new TotpSecretCipher(KEY).isConfigured()).isTrue();
    }

    @Test
    void aKeyOfTheWrongSize_stopsStartup_withoutEchoingTheKey() {
        String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> new TotpSecretCipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(shortKey);
        assertThatThrownBy(() -> new TotpSecretCipher("not base64 at all !!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("not base64 at all");
    }
}
