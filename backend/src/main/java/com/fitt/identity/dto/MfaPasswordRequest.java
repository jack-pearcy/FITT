package com.fitt.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Starting setup asks for the password again, so a phone left unlocked is not
// enough to bind the account to someone else's authenticator.
public record MfaPasswordRequest(

        @NotBlank(message = "Password is required")
        @Size(max = 256, message = "Password is too long")
        String password
) {
}
