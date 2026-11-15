package com.fitt.identity.service;

import com.fitt.identity.domain.UserEntity;
import com.fitt.identity.repository.UserRepository;
import com.fitt.identity.service.exception.EmailAlreadyExistsException;
import com.fitt.shared.properties.JwtProperties;
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

    public IdentityAuthService(
            AuthenticationManager authenticationManager,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Duplicate check runs first so we return a clean 409 instead of letting
    // a DB constraint violation bubble up as a 500.
    // Password is hashed before the entity is built so plain text never hits the DB.
    public String register(String rawEmail, String password) {
        // BCrypt only reads the first 72 bytes. The DTO limits characters, but a
        // multibyte password can still exceed 72 bytes, so check bytes here.
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Password must be 72 bytes or fewer");
        }

        String email = normalizeEmail(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("email already registered");
        }

        String hash = passwordEncoder.encode(password);
        UserEntity saved = userRepository.save(new UserEntity(email, hash));

        return buildToken(saved);
    }

    // AuthenticationManager runs the full verification chain.
    // Wrong credentials throw AuthenticationException and Spring Security returns 401.
    // Pattern matching instanceof checks the type and binds in one step.
    // In practice this never fails because loadUserByUsername always returns a UserEntity,
    // but failing fast here is cleaner than letting a ClassCastException surface later.
    public String login(String email, String password) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(email), password)
        );

        Object principal = auth.getPrincipal();
        if (!(principal instanceof UserEntity user)) {
            throw new IllegalStateException("unexpected principal type after authentication");
        }

        return buildToken(user);
    }

    // Same normalization on both sides so "Jack@Example.com " and "jack@example.com"
    // are one account, whatever the database collation does.
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // Shared by register and login so token structure has one place to change.
    private String buildToken(UserEntity user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.getTtlSeconds()))
                .claim("uid", user.getId())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}