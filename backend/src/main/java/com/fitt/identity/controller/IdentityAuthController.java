package com.fitt.identity.controller;

import com.fitt.identity.dto.LoginRequest;
import com.fitt.identity.dto.RegisterRequest;
import com.fitt.identity.dto.TokenResponse;
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
        String token = identityAuthService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(new TokenResponse(token));
    }

    // Public endpoint, configured in SecurityConfig.
    // 401 is handled automatically by Spring Security if credentials fail.
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = identityAuthService.login(request.email(), request.password());
        return ResponseEntity.ok(new TokenResponse(token));
    }
}