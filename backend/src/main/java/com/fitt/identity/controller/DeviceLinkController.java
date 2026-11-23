package com.fitt.identity.controller;

import com.fitt.identity.dto.DeviceLinkCodeResponse;
import com.fitt.identity.dto.DeviceLinkRequest;
import com.fitt.identity.service.DeviceLinkService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The signed in half of connecting a device: asking for the code. Trading the
// code in is public and lives with login in IdentityAuthController.
// Same ownership guard every other controller uses: the path userId must match
// the one in the token.
@RestController
@RequestMapping("/api/users/{userId}/identity/device-link")
public class DeviceLinkController {

    private final DeviceLinkService deviceLinkService;

    public DeviceLinkController(DeviceLinkService deviceLinkService) {
        this.deviceLinkService = deviceLinkService;
    }

    // no-store, so no cache between here and the client keeps the code.
    @PostMapping
    public ResponseEntity<DeviceLinkCodeResponse> issue(
            @PathVariable Long userId,
            @Valid @RequestBody DeviceLinkRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (!userId.equals(((Number) jwt.getClaim("uid")).longValue())) {
            throw new AccessDeniedException("access denied");
        }
        DeviceLinkService.IssuedCode issued = deviceLinkService.issue(userId, request.refreshToken());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new DeviceLinkCodeResponse(issued.code(), issued.expiresInSeconds()));
    }
}
