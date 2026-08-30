package com.fitt.identity.dto;

import jakarta.validation.constraints.NotBlank;

// Inbound DTO for the login endpoint.
// Only non-blank is enforced. Login must not leak password rules or block
// accounts made under older policies, so no format or length checks here.
public record LoginRequest(

        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {
}