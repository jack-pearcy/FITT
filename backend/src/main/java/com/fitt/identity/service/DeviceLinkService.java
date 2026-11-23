package com.fitt.identity.service;

import com.fitt.identity.domain.DeviceLinkCodeEntity;
import com.fitt.identity.repository.DeviceLinkCodeRepository;
import com.fitt.identity.service.exception.DeviceLinkException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

// Connects a second device to an account without sharing a sign-in.
//
// Refresh tokens rotate and a second use counts as theft, so two devices on one
// token would sign each other out. The second device gets its own sign-in:
// the first asks for a code, passes it across, and the second trades it in.
// The shape follows RFC 8628 (a device that is hard to type on is approved
// from one that is not), without the polling, because the code is pushed.
@Service
public class DeviceLinkService {

    // 256 bits. The code travels from device to device and is never typed, so
    // it can be as long as a refresh token and cannot be guessed.
    private static final int CODE_BYTES = 32;
    static final Duration CODE_LIFETIME = Duration.ofSeconds(60);

    private final DeviceLinkCodeRepository repository;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public DeviceLinkService(
            DeviceLinkCodeRepository repository,
            RefreshTokenService refreshTokenService,
            Clock clock
    ) {
        this.repository = repository;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    public record IssuedCode(String code, long expiresInSeconds) {
    }

    // The access token got the caller this far, but it lasts an hour and a
    // stolen one must not buy a month-long sign-in. The refresh token is asked
    // for as well: it is looked at, not rotated, so the caller keeps using it.
    // Any earlier code of the user stops working, so one is live at a time.
    @Transactional
    public IssuedCode issue(Long userId, String refreshToken) {
        refreshTokenService.requireLive(userId, refreshToken);

        Instant now = clock.instant();
        repository.deleteAllForUser(userId);

        byte[] bytes = new byte[CODE_BYTES];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new DeviceLinkCodeEntity(
                userId, RefreshTokenService.hash(code), now, now.plus(CODE_LIFETIME)));
        return new IssuedCode(code, CODE_LIFETIME.toSeconds());
    }

    // Returns whose code it was and uses it up. An expired code is deleted too,
    // which is why the refusal must not roll the transaction back.
    @Transactional(noRollbackFor = DeviceLinkException.class)
    public Long redeem(String code) {
        DeviceLinkCodeEntity found = repository
                .findByCodeHashForUpdate(RefreshTokenService.hash(code))
                .orElseThrow(DeviceLinkException::new);

        repository.delete(found);
        if (found.isExpiredAt(clock.instant())) {
            throw new DeviceLinkException();
        }
        return found.getUserId();
    }

    // Account deletion and password change.
    @Transactional
    public void eraseAll(Long userId) {
        repository.deleteAllForUser(userId);
    }
}
