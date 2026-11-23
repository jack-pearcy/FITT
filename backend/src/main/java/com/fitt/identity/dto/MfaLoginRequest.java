package com.fitt.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The second step of a sign-in: the token login handed back, and either the six
// digits from the authenticator app or one recovery code. The bounds are not
// policy, they only keep oversized input away from the lookups.
public record MfaLoginRequest(

        @NotBlank(message = "Sign-in token is required")
        @Size(max = 128, message = "Sign-in token is too long")
        String mfaToken,

        @NotBlank(message = "Code is required")
        @Size(max = 32, message = "Code is too long")
        String code
) {
}
