package com.fitt.identity.service;

import com.fitt.identity.domain.RefreshTokenEntity;
import com.fitt.identity.repository.RefreshTokenRepository;
import com.fitt.identity.service.exception.InvalidRefreshTokenException;
import com.fitt.shared.properties.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The rotation rules, with the repository mocked and the clock fixed.
// RefreshTokenRotationTest runs the same rules against a real database.
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final long TTL_SECONDS = 600;

    @Mock private RefreshTokenRepository repository;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setRefreshTtlSeconds(TTL_SECONDS);
        service = new RefreshTokenService(repository, props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private RefreshTokenEntity stored(String token, String family, Instant expiresAt) {
        RefreshTokenEntity entity = new RefreshTokenEntity(
                7L, RefreshTokenService.hash(token), family, NOW.minusSeconds(60), expiresAt);
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash(token))).thenReturn(Optional.of(entity));
        return entity;
    }

    private RefreshTokenEntity savedEntity() {
        ArgumentCaptor<RefreshTokenEntity> saved = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void issue_storesOnlyTheHashWithTheConfiguredLifetime() {
        String token = service.issue(7L);

        RefreshTokenEntity saved = savedEntity();
        assertThat(token).hasSize(43); // 32 bytes, base64url, no padding
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.hash(token)).isNotEqualTo(token).hasSize(64);
        assertThat(saved.getOwnerUserId()).isEqualTo(7L);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusSeconds(TTL_SECONDS));
        assertThat(saved.isRevoked()).isFalse();
    }

    @Test
    void issue_clearsTheUsersExpiredTokens() {
        service.issue(7L);

        verify(repository).deleteExpired(7L, NOW);
    }

    @Test
    void issue_twice_givesDifferentTokensInDifferentFamilies() {
        String first = service.issue(7L);
        String second = service.issue(7L);

        ArgumentCaptor<RefreshTokenEntity> saved = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(first).isNotEqualTo(second);
        assertThat(saved.getAllValues().get(0).getFamilyId())
                .isNotEqualTo(saved.getAllValues().get(1).getFamilyId());
    }

    @Test
    void rotate_liveToken_retiresItAndIssuesTheNextInTheSameFamily() {
        RefreshTokenEntity current = stored("presented", "family-1", NOW.plusSeconds(60));

        RefreshTokenService.Rotation rotation = service.rotate("presented");

        RefreshTokenEntity next = savedEntity();
        assertThat(current.isRevoked()).isTrue();
        assertThat(rotation.userId()).isEqualTo(7L);
        assertThat(rotation.refreshToken()).isNotEqualTo("presented");
        assertThat(next.getTokenHash()).isEqualTo(RefreshTokenService.hash(rotation.refreshToken()));
        assertThat(next.getFamilyId()).isEqualTo("family-1");
        assertThat(next.getExpiresAt()).isEqualTo(NOW.plusSeconds(TTL_SECONDS));
        verify(repository, never()).revokeFamily(anyString(), any());
    }

    @Test
    void rotate_unknownToken_isRejected() {
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("never-issued")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_alreadyUsedToken_revokesTheWholeFamily() {
        RefreshTokenEntity used = stored("presented", "family-1", NOW.plusSeconds(60));
        used.revoke(NOW.minusSeconds(30));

        assertThatThrownBy(() -> service.rotate("presented")).isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository).revokeFamily("family-1", NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_expiredToken_isRejectedWithoutTouchingTheFamily() {
        RefreshTokenEntity expired = stored("presented", "family-1", NOW);

        assertThatThrownBy(() -> service.rotate("presented")).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(expired.isRevoked()).isFalse();
        verify(repository, never()).revokeFamily(anyString(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void revoke_knownToken_endsItsFamily() {
        stored("presented", "family-1", NOW.plusSeconds(60));

        service.revoke("presented");

        verify(repository).revokeFamily("family-1", NOW);
    }

    @Test
    void revoke_unknownToken_doesNothing() {
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        service.revoke("never-issued");

        verify(repository, never()).revokeFamily(anyString(), any());
    }
}
