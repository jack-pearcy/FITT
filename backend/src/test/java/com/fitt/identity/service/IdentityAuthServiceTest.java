package com.fitt.identity.service;

import com.fitt.identity.domain.UserEntity;
import com.fitt.identity.repository.UserRepository;
import com.fitt.identity.service.exception.EmailAlreadyExistsException;
import com.fitt.identity.service.exception.InvalidRefreshTokenException;
import com.fitt.shared.properties.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Covers the normalization and password-limit rules on register and login, and
// how the access and refresh tokens are paired.
@ExtendWith(MockitoExtension.class)
class IdentityAuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtEncoder jwtEncoder;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private MfaService mfaService;

    private IdentityAuthService service;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecretValue("irrelevant-for-this-test-but-long-enough");
        service = new IdentityAuthService(
                authenticationManager, jwtEncoder, props, userRepository, passwordEncoder, refreshTokenService,
                mfaService);
    }

    private void stubTokenEncoding() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").claim("sub", "x").build();
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);
    }

    @Test
    void register_mixedCaseEmailWithSpaces_isStoredTrimmedAndLowercased() {
        stubTokenEncoding();
        when(userRepository.existsByEmail("jack@example.com")).thenReturn(false);
        when(passwordEncoder.encode("a-long-enough-password")).thenReturn("hash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 1L);
            return u;
        });

        service.register("  Jack@Example.COM ", "a-long-enough-password");

        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("jack@example.com");
    }

    @Test
    void register_differentCaseOfExistingEmail_isRejectedAsDuplicate() {
        when(userRepository.existsByEmail("jack@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("JACK@example.com", "a-long-enough-password"))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void register_passwordOver72Bytes_isRejectedBeforeAnyLookup() {
        // 40 two-byte characters: 40 chars passes the DTO's character limit, 80 bytes does not.
        String multibyte = "é".repeat(40);

        assertThatThrownBy(() -> service.register("a@b.com", multibyte))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 bytes");
        verifyNoInteractions(userRepository, passwordEncoder, refreshTokenService);
    }

    @Test
    void login_normalizesEmailBeforeAuthenticating() {
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        UserEntity user = new UserEntity("jack@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 1L);
        when(auth.getPrincipal()).thenReturn(user);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(auth);
        stubTokenEncoding();

        service.login(" Jack@Example.com", "whatever");

        ArgumentCaptor<Authentication> captor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(captor.getValue().getPrincipal()).isEqualTo("jack@example.com");
    }

    @Test
    void register_returnsAccessTokenAndAFreshRefreshTokenForTheNewUser() {
        stubTokenEncoding();
        when(userRepository.existsByEmail("jack@example.com")).thenReturn(false);
        when(passwordEncoder.encode("a-long-enough-password")).thenReturn("hash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 7L);
            return u;
        });
        when(refreshTokenService.issue(7L)).thenReturn("refresh-1");

        AuthTokens tokens = service.register("jack@example.com", "a-long-enough-password");

        assertThat(tokens).isEqualTo(new AuthTokens("token", "refresh-1"));
    }

    @Test
    void login_returnsAccessTokenAndAFreshRefreshToken() {
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        UserEntity user = new UserEntity("jack@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 7L);
        when(auth.getPrincipal()).thenReturn(user);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(auth);
        stubTokenEncoding();
        when(refreshTokenService.issue(7L)).thenReturn("refresh-1");

        assertThat(service.login("jack@example.com", "whatever"))
                .isEqualTo(LoginResult.signedIn(new AuthTokens("token", "refresh-1")));
    }

    @Test
    void login_withTwoStepSignInOn_issuesAChallengeAndNoTokens() {
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        UserEntity user = new UserEntity("jack@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 7L);
        when(auth.getPrincipal()).thenReturn(user);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(auth);
        when(mfaService.isRequiredAtLogin(7L)).thenReturn(true);
        when(mfaService.startChallenge(7L)).thenReturn("challenge-1");

        assertThat(service.login("jack@example.com", "whatever"))
                .isEqualTo(LoginResult.needsSecondStep("challenge-1"));

        // The point of the second step: a correct password alone buys no token.
        org.mockito.Mockito.verifyNoInteractions(jwtEncoder, refreshTokenService);
    }

    @Test
    void refresh_rotatesAndBuildsAnAccessTokenForTheTokenOwner() {
        UserEntity user = new UserEntity("jack@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 7L);
        when(refreshTokenService.rotate("refresh-1")).thenReturn(new RefreshTokenService.Rotation(7L, "refresh-2"));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        stubTokenEncoding();

        assertThat(service.refresh("refresh-1")).isEqualTo(new AuthTokens("token", "refresh-2"));

        ArgumentCaptor<JwtEncoderParameters> params = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(params.capture());
        assertThat(params.getValue().getClaims().getSubject()).isEqualTo("jack@example.com");
        assertThat((Object) params.getValue().getClaims().getClaim("uid")).isEqualTo(7L);
    }

    @Test
    void refresh_rejectedToken_issuesNothing() {
        when(refreshTokenService.rotate("stale")).thenThrow(new InvalidRefreshTokenException());

        assertThatThrownBy(() -> service.refresh("stale")).isInstanceOf(InvalidRefreshTokenException.class);
        verifyNoInteractions(jwtEncoder, userRepository);
    }

    @Test
    void refresh_tokenWhoseUserIsGone_isRejected() {
        when(refreshTokenService.rotate("refresh-1")).thenReturn(new RefreshTokenService.Rotation(7L, "refresh-2"));
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("refresh-1")).isInstanceOf(InvalidRefreshTokenException.class);
        verifyNoInteractions(jwtEncoder);
    }

    @Test
    void logout_revokesTheRefreshToken() {
        service.logout("refresh-1");

        verify(refreshTokenService).revoke("refresh-1");
    }
}
