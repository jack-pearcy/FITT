package com.fitt.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

// Encrypts the authenticator secrets before they reach the database.
//
// A password can be stored as a hash because the server only ever compares. An
// authenticator secret has to be read back to work out the expected code, so it
// is encrypted with a key the database does not hold. A copy of the tables
// alone then gives nobody a working second step.
//
// AES-256-GCM with a fresh random nonce per secret. The user id is bound in as
// associated data, so a ciphertext moved onto another user's row fails to open.
//
// The key comes from the environment and is blank by default, which leaves the
// feature off: nobody can start setup, and no sign-in changes.
@Component
public class TotpSecretCipher {

    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TotpSecretCipher(@Value("${fitt.mfa.encryption-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            return;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            // The value is left out of the message on purpose.
            throw new IllegalStateException("fitt.mfa.encryption-key is not valid Base64");
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException("fitt.mfa.encryption-key must be 32 bytes, Base64 encoded");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public boolean isConfigured() {
        return key != null;
    }

    // Nonce followed by ciphertext and tag.
    public byte[] encrypt(Long userId, byte[] secret) {
        requireKey();
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData(userId));
            byte[] sealed = cipher.doFinal(secret);
            return ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("could not encrypt the authenticator secret", e);
        }
    }

    public byte[] decrypt(Long userId, byte[] stored) {
        requireKey();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, NONCE_BYTES));
            cipher.updateAAD(associatedData(userId));
            return cipher.doFinal(Arrays.copyOfRange(stored, NONCE_BYTES, stored.length));
        } catch (GeneralSecurityException e) {
            // A different key, a damaged row, or a row moved between users.
            throw new IllegalStateException("could not decrypt the authenticator secret", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("fitt.mfa.encryption-key is not set");
        }
    }

    private static byte[] associatedData(Long userId) {
        return ByteBuffer.allocate(Long.BYTES).putLong(userId).array();
    }
}
