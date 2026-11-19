package com.fitt.identity.controller;

import com.fitt.identity.dto.DeleteAccountRequest;
import com.fitt.identity.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Changes to the account itself. Same ownership guard every other controller
// uses: the path userId must match the one in the token.
@RestController
@RequestMapping("/api/users/{userId}/identity/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // POST with a body, not DELETE: the password has to travel with the request,
    // and RFC 9110 gives a body on DELETE no defined meaning, so a proxy may drop it.
    @PostMapping("/delete")
    public ResponseEntity<Void> deleteAccount(
            @PathVariable Long userId,
            @Valid @RequestBody DeleteAccountRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (!userId.equals(((Number) jwt.getClaim("uid")).longValue())) {
            throw new AccessDeniedException("access denied");
        }
        accountService.deleteAccount(userId, request.password());
        return ResponseEntity.noContent().build();
    }
}
