package com.fitt.identity.service;

import com.fitt.identity.domain.UserEntity;
import com.fitt.identity.repository.UserRepository;
import com.fitt.identity.service.exception.DeviceLinkException;
import com.fitt.identity.service.exception.EmailAlreadyExistsException;
import com.fitt.identity.service.exception.IncorrectPasswordException;
import com.fitt.identity.service.exception.InvalidRefreshTokenException;
import com.fitt.identity.service.exception.MfaChallengeException;
import com.fitt.shared.properties.JwtProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

@Service
public class IdentityAuthService {

    private static final int MAX_PASSWORD_BYTES = 72;

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final MfaService mfaService;
    private final DeviceLinkService deviceLinkService;

    public IdentityAuthService(
            AuthenticationManager authenticationManager,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            MfaService mfaService,
            DeviceLinkService deviceLinkService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.mfaService = mfaService;
        this.deviceLinkService = deviceLinkService;
    }

    // Duplicate check runs first so we return a clean 409 instead of letting
    // a DB constraint violation bubble up as a 500.
    // Password is hashed before the entity is built so plain text never hits the DB.
    // One transaction, so a user row never exists without its first refresh token.
    @Transactional
    public AuthTokens register(String rawEmail, String password) {
        requireWithinByteLimit(password);

        String email = normalizeEmail(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("email already registered");
        }

        String hash = passwordEncoder.encode(password);
        UserEntity saved = userRepository.save(new UserEntity(email, hash));

        return tokensFor(saved);
    }

    // AuthenticationManager runs the full verification chain.
    // Wrong credentials throw AuthenticationException and Spring Security returns 401.
    // Pattern matching instanceof checks the type and binds in one step.
    // In practice this never fails because loadUserByUsername always returns a UserEntity,
    // but failing fast here is cleaner than letting a ClassCastException surface later.
    //
    // A correct password is the whole sign-in only for an account without
    // two-step sign-in. With it, no token is issued here: the caller gets a
    // challenge, and completeMfaLogin issues the tokens once the code is right.
    public LoginResult login(String email, String password) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(email), password)
        );

        Object principal = auth.getPrincipal();
        if (!(principal instanceof UserEntity user)) {
            throw new IllegalStateException("unexpected principal type after authentication");
        }

        if (mfaService.isRequiredAtLogin(user.getId())) {
            return LoginResult.needsSecondStep(mfaService.startChallenge(user.getId()));
        }
        return LoginResult.signedIn(tokensFor(user));
    }

    // The second step. The challenge names the user, so the email is not sent
    // again, and it is used up by a correct code.
    public AuthTokens completeMfaLogin(String mfaToken, String code) {
        Long userId = mfaService.redeemChallenge(mfaToken, code);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(MfaChallengeException::new);
        return tokensFor(user);
    }

    // Signs in a second device with a code the first one asked for. The pair
    // starts a token family of its own, so the two devices refresh and sign out
    // independently. Two-step sign-in is not asked again: the device that asked
    // for the code had already passed it.
    public AuthTokens redeemDeviceLink(String code) {
        Long userId = deviceLinkService.redeem(code);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(DeviceLinkException::new);
        return tokensFor(user);
    }

    // Trades a refresh token for a new pair. The presented token is retired, so
    // the caller must store the new one. Not transactional here on purpose: the
    // rotation commits on its own, including the revocation it does on a replay.
    public AuthTokens refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        UserEntity user = userRepository.findById(rotation.userId())
                .orElseThrow(InvalidRefreshTokenException::new);
        return new AuthTokens(buildToken(user), rotation.refreshToken());
    }

    // Ends the login the refresh token belongs to. The access token stays valid
    // until it expires, which is the accepted cost of a stateless JWT.
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    // Replaces the password and ends every session of the account: the refresh
    // tokens are removed, and the new token version makes every access token
    // issued before this moment fail. The caller gets a fresh pair back, so the
    // device that made the change stays signed in and every other one is out.
    // One transaction, so the hash, the version and the tokens change together.
    @Transactional
    public AuthTokens changePassword(Long userId, String currentPassword, String newPassword) {
        requireWithinByteLimit(newPassword);

        // The token check has already seen this user, so a miss means the account
        // was deleted between the two reads. The token is no longer good for anything.
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("access denied"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IncorrectPasswordException("currentPassword");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from the current one");
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenService.endAllLogins(userId);
        // A code asked for under the old password must not outlive it.
        deviceLinkService.eraseAll(userId);
        return tokensFor(user);
    }

    // BCrypt only reads the first 72 bytes. The DTO limits characters, but a
    // multibyte password can still exceed 72 bytes, so check bytes here.
    private static void requireWithinByteLimit(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Password must be 72 bytes or fewer");
        }
    }

    private AuthTokens tokensFor(UserEntity user) {
        return new AuthTokens(buildToken(user), refreshTokenService.issue(user.getId()));
    }

    // Same normalization on both sides so "Jack@Example.com " and "jack@example.com"
    // are one account, whatever the database collation does.
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // Shared by register, login, and refresh so token structure has one place to change.
    private String buildToken(UserEntity user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.getTtlSeconds()))
                .claim("uid", user.getId())
                .claim("ver", user.getTokenVersion())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}