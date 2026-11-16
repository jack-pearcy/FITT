package com.fitt.identity.service;

import com.fitt.identity.service.exception.InvalidRefreshTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Rotation against a real database and real transactions. The point of this
// class is the one thing a mocked repository cannot show: that revoking a
// family on a replay is committed even though the same call then throws.
// No test-level transaction on purpose, so each service call commits by itself.
@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenRotationTest {

    @Autowired
    private RefreshTokenService service;

    @Test
    void eachRotationRetiresThePreviousToken() {
        String first = service.issue(101L);

        RefreshTokenService.Rotation second = service.rotate(first);
        RefreshTokenService.Rotation third = service.rotate(second.refreshToken());

        assertThat(second.userId()).isEqualTo(101L);
        assertThat(third.userId()).isEqualTo(101L);
        assertThat(third.refreshToken()).isNotEqualTo(second.refreshToken()).isNotEqualTo(first);
    }

    @Test
    void replayingAUsedTokenRevokesTheNewestTokenToo() {
        String first = service.issue(102L);
        String second = service.rotate(first).refreshToken();

        // The copy of the first token is presented again.
        assertThatThrownBy(() -> service.rotate(first)).isInstanceOf(InvalidRefreshTokenException.class);

        // The legitimate holder of the newest token is signed out as well.
        assertThatThrownBy(() -> service.rotate(second)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void aReplayInOneLoginLeavesAnotherLoginAlone() {
        String phone = service.issue(103L);
        String laptop = service.issue(103L);
        String phoneNext = service.rotate(phone).refreshToken();

        assertThatThrownBy(() -> service.rotate(phone)).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.rotate(phoneNext)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(service.rotate(laptop).userId()).isEqualTo(103L);
    }

    @Test
    void logoutEndsTheLogin() {
        String first = service.issue(104L);
        String second = service.rotate(first).refreshToken();

        service.revoke(second);

        assertThatThrownBy(() -> service.rotate(second)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutTwiceIsHarmless() {
        String token = service.issue(105L);

        service.revoke(token);
        service.revoke(token);
        service.revoke("never-issued");
    }
}
