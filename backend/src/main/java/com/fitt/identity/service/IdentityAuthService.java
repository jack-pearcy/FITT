package com.fitt.identity.service;

import com.fitt.shared.properties.JwtProperties;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Instant;

@Service
public class IdentityAuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public IdentityAuthService(
            AuthenticationManager authenticationManager,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    // Two things happen here.
    //
    // First, AuthenticationManager verifies the credentials against the UserDetailsService.
    // If they are wrong it throws an AuthenticationException and execution stops there.
    // Spring Security catches it and returns a 401 automatically.
    //
    // Second, the claims get built and the token gets signed. Claims are the assertions
    // the token carries: iss is who issued it, sub is who it is for, iat is when it was
    // issued, exp is when it expires, and uid is the internal numeric user ID used later
    // for ownership enforcement in the controllers.
    //
    // Phase 6 note: uid is hardcoded to 1L because the in-memory user has no numeric ID.
    // This gets replaced with a real DB lookup once the identity domain is fully built out.
    public String login(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(authentication.getName())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.getTtlSeconds()))
                .claim("uid", 1L) // Phase 6 placeholder, replace with real user lookup
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
