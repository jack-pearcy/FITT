package com.fitt.identity.dto;

import jakarta.validation.constraints.NotBlank;

// Inbound DTO for the refresh and logout endpoints.
public record RefreshRequest(

        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
}
