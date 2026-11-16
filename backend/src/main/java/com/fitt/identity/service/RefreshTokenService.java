package com.fitt.identity.service;

import com.fitt.identity.domain.RefreshTokenEntity;
import com.fitt.identity.repository.RefreshTokenRepository;
import com.fitt.identity.service.exception.InvalidRefreshTokenException;
import com.fitt.shared.properties.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

// Issues, rotates, and revokes refresh tokens.
//
// Rotation with reuse detection, the approach RFC 9700 section 4.14 gives for
// public clients: each refresh retires the presented token and issues a new one
// in the same family. A retired token presented again means the token was copied,
// and the server cannot tell the thief from the owner, so the whole family is
// revoked and the next refresh from either of them fails.
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    // 256 bits. Unguessable, which is why a plain SHA-256 is enough for storage
    // and no slow password hash is needed.
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties jwtProperties) {
        this(repository, jwtProperties, Clock.systemUTC());
    }

    // Package-private so a test can drive time without sleeping.
    RefreshTokenService(RefreshTokenRepository repository, JwtProperties jwtProperties, Clock clock) {
        this.repository = repository;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    // What a rotation hands back: whose token it was, and the token that replaces it.
    public record Rotation(Long userId, String refreshToken) {
    }

    // Starts a new family, for a fresh login or registration.
    @Transactional
    public String issue(Long userId) {
        Instant now = clock.instant();
        // Housekeeping at the one moment a user is known to be present.
        repository.deleteExpired(userId, now);
        return store(userId, UUID.randomUUID().toString(), now);
    }

    // noRollbackFor matters: when a replay is detected the family is revoked and
    // then the exception is thrown. A rollback would undo the revocation.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String presentedToken) {
        Instant now = clock.instant();
        RefreshTokenEntity current = repository.findByTokenHashForUpdate(hash(presentedToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (current.isRevoked()) {
            int revoked = repository.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token replay for user {}, revoked {} live token(s) in the family",
                    current.getOwnerUserId(), revoked);
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpiredAt(now)) {
            throw new InvalidRefreshTokenException();
        }

        current.revoke(now);
        String next = store(current.getOwnerUserId(), current.getFamilyId(), now);
        return new Rotation(current.getOwnerUserId(), next);
    }

    // Logout. Ends the whole family so no token from this login works again.
    // An unknown token is ignored, so logging out twice is not an error.
    @Transactional
    public void revoke(String presentedToken) {
        repository.findByTokenHashForUpdate(hash(presentedToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private String store(Long userId, String familyId, Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant expiresAt = now.plusSeconds(jwtProperties.getRefreshTtlSeconds());
        repository.save(new RefreshTokenEntity(userId, hash(token), familyId, now, expiresAt));
        return token;
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime is required to provide SHA-256.
            throw new IllegalStateException(e);
        }
    }
}
