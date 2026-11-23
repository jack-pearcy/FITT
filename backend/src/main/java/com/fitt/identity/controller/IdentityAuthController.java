package com.fitt.identity.controller;

import com.fitt.identity.dto.DeviceLinkRedeemRequest;
import com.fitt.identity.dto.LoginRequest;
import com.fitt.identity.dto.LoginResponse;
import com.fitt.identity.dto.MfaLoginRequest;
import com.fitt.identity.dto.RefreshRequest;
import com.fitt.identity.dto.RegisterRequest;
import com.fitt.identity.dto.TokenResponse;
import com.fitt.identity.service.AuthTokens;
import com.fitt.identity.service.IdentityAuthService;
import com.fitt.identity.service.LoginResult;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
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
    // For an account with two-step sign-in the answer holds no tokens, only the
    // token for the second step below.
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = identityAuthService.login(request.email(), request.password());
        if (result.needsSecondStep()) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(LoginResponse.secondStepDue(result.mfaToken()));
        }
        return ResponseEntity.ok(LoginResponse.signedIn(
                result.tokens().accessToken(), result.tokens().refreshToken()));
    }

    // The second step: the token from login plus a code from the authenticator
    // app, or a recovery code. Public for the same reason refresh is: there is no
    // access token yet, and the token in the body is the credential.
    @PostMapping("/mfa")
    public ResponseEntity<TokenResponse> completeMfaLogin(@Valid @RequestBody MfaLoginRequest request) {
        AuthTokens tokens = identityAuthService.completeMfaLogin(request.mfaToken(), request.code());
        return ResponseEntity.ok(toResponse(tokens));
    }

    // A second device, first the Apple Watch, trades the code its owner's phone
    // asked for. Public: the device has no token yet, and the code in the body
    // is the credential. The answer is a full pair, so no-store.
    @PostMapping("/device-link")
    public ResponseEntity<TokenResponse> redeemDeviceLink(@Valid @RequestBody DeviceLinkRedeemRequest request) {
        AuthTokens tokens = identityAuthService.redeemDeviceLink(request.code());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(toResponse(tokens));
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