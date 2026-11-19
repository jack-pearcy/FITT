package com.fitt.identity.dto;

import jakarta.validation.constraints.NotBlank;

// The current password, typed again. A copied access token must not be enough
// to delete an account.
public record DeleteAccountRequest(

        @NotBlank(message = "Password is required")
        String password
) {
}
