package com.fitt.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Inbound DTO for the login endpoint.
// Login must not leak password rules or block accounts made under older
// policies, so there are no format or minimum length checks here. The upper
// bounds are not policy: they sit well above anything a real account can have
// (the email column holds 255 characters, a password at most 72) and only stop
// oversized input from reaching the user lookup and the password hash.
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Size(max = 255, message = "Email is too long")
        String email,

        @NotBlank(message = "Password is required")
        @Size(max = 256, message = "Password is too long")
        String password
) {
}