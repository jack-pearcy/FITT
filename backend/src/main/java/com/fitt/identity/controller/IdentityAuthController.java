package com.fitt.identity.controller;

import com.fitt.identity.dto.LoginRequest;
import com.fitt.identity.dto.RefreshRequest;
import com.fitt.identity.dto.RegisterRequest;
import com.fitt.identity.dto.TokenResponse;
import com.fitt.identity.service.AuthTokens;
import com.fitt.identity.service.IdentityAuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Thin controller, HTTP concerns only.
// Parses the request, calls the service, returns the response.
@RestController
@RequestMapping("/api/identity/auth")
public class IdentityAuthController {

    private final IdentityAuthService identityAuthService;

    public IdentityAuthController(IdentityAuthService identityAuthService) {
        this.identityAuthService = identityAuthService;
    }

    // Returns 201 so the client knows a resource was created.
    // Token is included so the user is logged in immediately after sign up.
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthTokens tokens = identityAuthService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(tokens));
    }

    // Public endpoint, configured in SecurityConfig.
    // 401 is handled automatically by Spring Security if credentials fail.
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthTokens tokens = identityAuthService.login(request.email(), request.password());
        return ResponseEntity.ok(toResponse(tokens));
    }

    // Public, because the access token has usually expired by the time this is
    // called. The refresh token in the body is the credential.
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(toResponse(identityAuthService.refresh(request.refreshToken())));
    }

    // 204 whether or not the token was known, so logging out twice is harmless.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        identityAuthService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private static TokenResponse toResponse(AuthTokens tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken());
    }
}