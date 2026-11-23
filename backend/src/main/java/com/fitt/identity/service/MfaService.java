package com.fitt.identity.service;

import com.fitt.identity.domain.MfaChallengeEntity;
import com.fitt.identity.domain.RecoveryCodeEntity;
import com.fitt.identity.domain.TotpCredentialEntity;
import com.fitt.identity.domain.UserEntity;
import com.fitt.identity.repository.MfaChallengeRepository;
import com.fitt.identity.repository.RecoveryCodeRepository;
import com.fitt.identity.repository.TotpCredentialRepository;
import com.fitt.identity.repository.UserRepository;
import com.fitt.identity.service.exception.IncorrectPasswordException;
import com.fitt.identity.service.exception.InvalidMfaCodeException;
import com.fitt.identity.service.exception.MfaChallengeException;
import com.fitt.identity.service.exception.MfaLockedException;
import com.fitt.identity.service.exception.MfaStateException;
import com.fitt.identity.service.exception.MfaUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

// Two-step sign-in: an authenticator app as the second step, and one-time
// recovery codes for when the phone is gone.
//
// The moving parts, in the order a user meets them:
//   1. Setup starts: the password is asked again, a secret is made and shown once.
//   2. Setup is confirmed: the user types a code from the app. Only now is the
//      feature on, so a half-finished setup can never lock anyone out. The
//      recovery codes are handed over at this moment, once.
//   3. Sign-in: a correct password no longer yields tokens. It yields a
//      challenge, and the challenge plus a code yields the tokens.
//
// Wrong codes are counted on the account, not per address, so guessing from
// many addresses at once gains nothing.
@Service
public class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    // 160 bits, the size RFC 4226 recommends for the shared secret.
    private static final int SECRET_BYTES = 20;

    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_LENGTH = 16;
    // 32 symbols, so each is five random bits and a code is 80. No 0, O, 1 or I:
    // they are misread when a code is copied from paper.
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int CHALLENGE_TOKEN_BYTES = 32;
    private static final Duration CHALLENGE_LIFETIME = Duration.ofMinutes(5);

    // Five wrong codes in a row, then a quarter of an hour of nothing. At that
    // pace a six-digit code survives guessing for years.
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_FOR = Duration.ofMinutes(15);

    private static final String ISSUER = "FITT";

    private final TotpCredentialRepository totpRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final MfaChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpSecretCipher cipher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public MfaService(
            TotpCredentialRepository totpRepository,
            RecoveryCodeRepository recoveryCodeRepository,
            MfaChallengeRepository challengeRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TotpSecretCipher cipher,
            Clock clock
    ) {
        this.totpRepository = totpRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.cipher = cipher;
        this.clock = clock;
    }

    // available: this server can do two-step sign-in at all.
    public record Status(boolean available, boolean totpEnabled, long recoveryCodesLeft) {
    }

    // The secret for typing in by hand, and the same thing as the link a QR code holds.
    public record Setup(String secret, String otpauthUri) {
    }

    @Transactional(readOnly = true)
    public Status status(Long userId) {
        boolean enabled = totpRepository.isConfirmedFor(userId);
        long codesLeft = enabled ? recoveryCodeRepository.countByUserIdAndUsedAtIsNull(userId) : 0;
        return new Status(cipher.isConfigured(), enabled, codesLeft);
    }

    // Starting again before confirming replaces the secret, so an abandoned
    // attempt costs nothing. Once confirmed, it has to be turned off first.
    @Transactional
    public Setup beginTotpSetup(Long userId, String password) {
        if (!cipher.isConfigured()) {
            throw new MfaUnavailableException();
        }
        UserEntity user = requirePassword(userId, password);

        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        byte[] sealed = cipher.encrypt(userId, secret);
        Instant now = clock.instant();

        TotpCredentialEntity credential = totpRepository.findByUserIdForUpdate(userId).orElse(null);
        if (credential == null) {
            totpRepository.save(new TotpCredentialEntity(userId, sealed, now));
        } else if (credential.isConfirmed()) {
            throw new MfaStateException("Two-step sign-in is already on");
        } else {
            credential.restart(sealed, now);
        }

        String encoded = Totp.base32(secret);
        return new Setup(encoded, otpauthUri(user.getUsername(), encoded));
    }

    // Turns the feature on and hands back the recovery codes. This is the only
    // time they exist in readable form.
    @Transactional
    public List<String> confirmTotp(Long userId, String code) {
        Instant now = clock.instant();
        TotpCredentialEntity credential = totpRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new MfaStateException("Start setup first"));
        if (credential.isConfirmed()) {
            throw new MfaStateException("Two-step sign-in is already on");
        }
        // Not counted towards a lock: the caller is signed in and the feature is
        // not on yet, so there is nothing here to guess a way into.
        if (!totpMatches(credential, normalize(code), now)) {
            throw new InvalidMfaCodeException();
        }

        credential.confirm(now);
        log.info("Two-step sign-in turned on for user {}", userId);
        return replaceRecoveryCodes(userId, now);
    }

    // Password and a code, because either one alone is exactly what the feature
    // is there to not trust.
    @Transactional(noRollbackFor = {InvalidMfaCodeException.class, MfaLockedException.class})
    public void disable(Long userId, String password, String code) {
        requirePassword(userId, password);
        TotpCredentialEntity credential = requireConfirmed(userId);
        verifySecondStep(credential, code, clock.instant());

        recoveryCodeRepository.deleteAllForUser(userId);
        challengeRepository.deleteAllForUser(userId);
        totpRepository.delete(credential);
        log.info("Two-step sign-in turned off for user {}", userId);
    }

    // Every old code stops working, used or not.
    @Transactional(noRollbackFor = {InvalidMfaCodeException.class, MfaLockedException.class})
    public List<String> regenerateRecoveryCodes(Long userId, String password, String code) {
        requirePassword(userId, password);
        TotpCredentialEntity credential = requireConfirmed(userId);
        Instant now = clock.instant();
        verifySecondStep(credential, code, now);
        return replaceRecoveryCodes(userId, now);
    }

    @Transactional(readOnly = true)
    public boolean isRequiredAtLogin(Long userId) {
        return totpRepository.isConfirmedFor(userId);
    }

    // Called once the password has been checked. The token returned is the only
    // thing that connects the second request to the first.
    @Transactional
    public String startChallenge(Long userId) {
        Instant now = clock.instant();
        // Housekeeping at the one moment a user is known to be present.
        challengeRepository.deleteExpired(userId, now);

        byte[] bytes = new byte[CHALLENGE_TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        challengeRepository.save(new MfaChallengeEntity(
                userId, RefreshTokenService.hash(token), now, now.plus(CHALLENGE_LIFETIME)));
        return token;
    }

    // The second step of a sign-in. Answers with the user the challenge belongs
    // to, and uses the challenge up. A wrong code leaves the challenge alive so
    // a typo does not send the user back to the password, but it is counted.
    // noRollbackFor matters: the count has to be saved while the error is thrown.
    @Transactional(noRollbackFor = {InvalidMfaCodeException.class, MfaLockedException.class})
    public Long redeemChallenge(String mfaToken, String code) {
        Instant now = clock.instant();
        MfaChallengeEntity challenge = challengeRepository
                .findByTokenHashForUpdate(RefreshTokenService.hash(mfaToken))
                .orElseThrow(MfaChallengeException::new);
        if (challenge.isExpiredAt(now)) {
            throw new MfaChallengeException();
        }

        Long userId = challenge.getUserId();
        TotpCredentialEntity credential = totpRepository.findByUserIdForUpdate(userId)
                .filter(TotpCredentialEntity::isConfirmed)
                // Turned off between the two steps. Signing in again will not ask for a code.
                .orElseThrow(MfaChallengeException::new);

        verifySecondStep(credential, code, now);
        challengeRepository.delete(challenge);
        return userId;
    }

    // Account deletion. Called inside the transaction that removes the account.
    @Transactional
    public void eraseAll(Long userId) {
        recoveryCodeRepository.deleteAllForUser(userId);
        challengeRepository.deleteAllForUser(userId);
        totpRepository.deleteAllForUser(userId);
    }

    // Six digits is a code from the app. Anything else is tried as a recovery
    // code. Either way a miss is counted and a hit clears the count.
    private void verifySecondStep(TotpCredentialEntity credential, String rawCode, Instant now) {
        if (credential.isLockedAt(now)) {
            throw new MfaLockedException(credential.secondsUntilUnlocked(now));
        }

        String code = normalize(rawCode);
        boolean accepted = code.matches("\\d{" + Totp.DIGITS + "}")
                ? totpMatches(credential, code, now)
                : recoveryCodeMatches(credential.getUserId(), code, now);

        if (!accepted) {
            credential.recordFailure(now, MAX_FAILURES, LOCK_FOR);
            if (credential.isLockedAt(now)) {
                log.warn("Two-step sign-in locked for user {} after repeated wrong codes", credential.getUserId());
            }
            throw new InvalidMfaCodeException();
        }
        credential.recordSuccess();
    }

    private boolean totpMatches(TotpCredentialEntity credential, String code, Instant now) {
        if (!cipher.isConfigured()) {
            // The key was removed after people had set this up. Recovery codes
            // still work, because they need no key.
            throw new MfaUnavailableException();
        }
        byte[] secret = cipher.decrypt(credential.getUserId(), credential.getSecretEncrypted());
        long step = Totp.matchingStep(secret, code, now, credential.getLastUsedStep());
        if (step < 0) {
            return false;
        }
        credential.acceptStep(step);
        return true;
    }

    private boolean recoveryCodeMatches(Long userId, String code, Instant now) {
        return recoveryCodeRepository
                .findByUserIdAndCodeHashAndUsedAtIsNull(userId, RefreshTokenService.hash(code))
                .map(found -> {
                    found.markUsed(now);
                    log.info("Recovery code used for user {}", userId);
                    return true;
                })
                .orElse(false);
    }

    private List<String> replaceRecoveryCodes(Long userId, Instant now) {
        recoveryCodeRepository.deleteAllForUser(userId);

        List<String> shown = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<RecoveryCodeEntity> stored = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = newRecoveryCode();
            stored.add(new RecoveryCodeEntity(userId, RefreshTokenService.hash(code), now));
            shown.add(grouped(code));
        }
        recoveryCodeRepository.saveAll(stored);
        return shown;
    }

    private String newRecoveryCode() {
        StringBuilder code = new StringBuilder(RECOVERY_CODE_LENGTH);
        for (int i = 0; i < RECOVERY_CODE_LENGTH; i++) {
            code.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
        }
        return code.toString();
    }

    // ABCD-EFGH-JKLM-NPQR. The dashes are for the reader and are dropped again
    // on the way in.
    private static String grouped(String code) {
        return String.join("-", code.split("(?<=\\G.{4})"));
    }

    // Spaces and dashes out, upper case, so a code typed the way it is printed,
    // or the way an app displays it ("123 456"), is read as the same code.
    private static String normalize(String code) {
        return code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private UserEntity requirePassword(Long userId, String password) {
        // The token check has already seen this user, so a miss means the account
        // was deleted between the two reads.
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("access denied"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IncorrectPasswordException("password");
        }
        return user;
    }

    private TotpCredentialEntity requireConfirmed(Long userId) {
        return totpRepository.findByUserIdForUpdate(userId)
                .filter(TotpCredentialEntity::isConfirmed)
                .orElseThrow(() -> new MfaStateException("Two-step sign-in is not on"));
    }

    // The Key Uri Format every authenticator app reads from a QR code. The
    // algorithm, digits and period are spelled out even though they are the
    // defaults, so an app never has to assume.
    private static String otpauthUri(String email, String base32Secret) {
        String label = UriUtils.encode(ISSUER + ":" + email, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + ISSUER
                + "&algorithm=SHA1&digits=" + Totp.DIGITS
                + "&period=" + Totp.PERIOD_SECONDS;
    }
}
