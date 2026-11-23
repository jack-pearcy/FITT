package com.fitt.identity.controller;

import com.fitt.identity.dto.MfaCodeRequest;
import com.fitt.identity.dto.MfaPasswordAndCodeRequest;
import com.fitt.identity.dto.MfaPasswordRequest;
import com.fitt.identity.dto.MfaStatusResponse;
import com.fitt.identity.dto.RecoveryCodesResponse;
import com.fitt.identity.dto.TotpSetupResponse;
import com.fitt.identity.service.MfaService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Turning two-step sign-in on and off for the signed in user. The second step
// of signing in itself is public and lives with login in IdentityAuthController.
// Same ownership guard every other controller uses: the path userId must match
// the one in the token.
@RestController
@RequestMapping("/api/users/{userId}/identity/mfa")
public class MfaController {

    private final MfaService mfaService;

    public MfaController(MfaService mfaService) {
        this.mfaService = mfaService;
    }

    @GetMapping
    public ResponseEntity<MfaStatusResponse> status(@PathVariable Long userId, @AuthenticationPrincipal Jwt jwt) {
        requireOwner(userId, jwt);
        MfaService.Status status = mfaService.status(userId);
        return ResponseEntity.ok(new MfaStatusResponse(
                status.available(), status.totpEnabled(), status.recoveryCodesLeft()));
    }

    // no-store on the answers that carry a secret or the recovery codes, so no
    // cache between here and the client keeps a copy.
    @PostMapping("/totp/setup")
    public ResponseEntity<TotpSetupResponse> beginSetup(
            @PathVariable Long userId,
            @Valid @RequestBody MfaPasswordRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireOwner(userId, jwt);
        MfaService.Setup setup = mfaService.beginTotpSetup(userId, request.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new TotpSetupResponse(setup.secret(), setup.otpauthUri()));
    }

    @PostMapping("/totp/confirm")
    public ResponseEntity<RecoveryCodesResponse> confirm(
            @PathVariable Long userId,
            @Valid @RequestBody MfaCodeRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireOwner(userId, jwt);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new RecoveryCodesResponse(mfaService.confirmTotp(userId, request.code())));
    }

    @PostMapping("/totp/disable")
    public ResponseEntity<Void> disable(
            @PathVariable Long userId,
            @Valid @RequestBody MfaPasswordAndCodeRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireOwner(userId, jwt);
        mfaService.disable(userId, request.password(), request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recovery-codes")
    public ResponseEntity<RecoveryCodesResponse> newRecoveryCodes(
            @PathVariable Long userId,
            @Valid @RequestBody MfaPasswordAndCodeRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireOwner(userId, jwt);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new RecoveryCodesResponse(
                        mfaService.regenerateRecoveryCodes(userId, request.password(), request.code())));
    }

    private static void requireOwner(Long userId, Jwt jwt) {
        if (!userId.equals(((Number) jwt.getClaim("uid")).longValue())) {
            throw new AccessDeniedException("access denied");
        }
    }
}
