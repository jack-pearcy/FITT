package com.fitt.identity.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;

// The time-based one-time password of RFC 6238, the scheme every authenticator
// app speaks: HMAC-SHA1 over the number of 30-second steps since 1970, cut down
// to six digits. SHA-1 and six digits are not a choice made here. They are the
// only settings all the common apps support, and HMAC-SHA1 is not affected by
// the collision attacks on plain SHA-1.
//
// Written out rather than pulled from a library: it is about thirty lines, the
// RFC publishes test values for it, and a sign-in check is a poor place for a
// dependency nobody here has read.
public final class Totp {

    public static final int DIGITS = 6;
    public static final long PERIOD_SECONDS = 30;

    // How many steps either side of now are accepted, for a phone whose clock
    // is a little off and for the seconds it takes to type the code.
    private static final long WINDOW_STEPS = 1;

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {}

    public static long stepAt(Instant time) {
        return time.getEpochSecond() / PERIOD_SECONDS;
    }

    public static String codeAt(byte[] secret, long step) {
        byte[] hmac;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            hmac = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
        } catch (GeneralSecurityException e) {
            // Every Java runtime is required to provide HmacSHA1.
            throw new IllegalStateException(e);
        }

        // Dynamic truncation, RFC 4226 section 5.3: the low four bits of the last
        // byte pick where to read 31 bits from.
        int offset = hmac[hmac.length - 1] & 0x0f;
        int binary = ((hmac[offset] & 0x7f) << 24)
                | ((hmac[offset + 1] & 0xff) << 16)
                | ((hmac[offset + 2] & 0xff) << 8)
                | (hmac[offset + 3] & 0xff);

        return String.format("%0" + DIGITS + "d", binary % 1_000_000);
    }

    // The step whose code this is, looking one step either side of now and only
    // at steps later than afterStep. -1 when there is none. Every candidate is
    // compared in constant time, and all of them are always checked, so the time
    // taken says nothing about how close a guess was.
    public static long matchingStep(byte[] secret, String code, Instant now, long afterStep) {
        long current = stepAt(now);
        byte[] presented = code.getBytes(StandardCharsets.US_ASCII);
        long matched = -1;
        for (long step = current - WINDOW_STEPS; step <= current + WINDOW_STEPS; step++) {
            byte[] expected = codeAt(secret, step).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, presented) && step > afterStep && matched < 0) {
                matched = step;
            }
        }
        return matched;
    }

    // RFC 4648 Base32 without padding, the form an authenticator app expects the
    // secret in.
    public static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return out.toString();
    }
}
