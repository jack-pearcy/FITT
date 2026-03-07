package com.fitt.identity.controller;

import com.fitt.identity.dto.LoginRequest;
import com.fitt.identity.dto.TokenResponse;
import com.fitt.identity.service.IdentityAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Thin controller, only deals with HTTP concerns.
// Parses the request, calls the service, returns the response.
@RestController
@RequestMapping("/api/identity/auth")
public class IdentityAuthController {

    private final IdentityAuthService identityAuthService;

    public IdentityAuthController(IdentityAuthService identityAuthService) {
        this.identityAuthService = identityAuthService;
    }

    // POST /api/identity/auth/login is a public endpoint, configured in SecurityConfig.
    // Returns 200 with the token on success. Returns 401 if credentials are wrong,
    // Spring handles that automatically via the AuthenticationManager.
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        String token = identityAuthService.login(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(new TokenResponse(token));
    }
}
