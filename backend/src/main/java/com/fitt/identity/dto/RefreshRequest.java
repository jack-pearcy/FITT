package com.fitt.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Inbound DTO for the refresh and logout endpoints.
// A real refresh token is 43 characters. The bound leaves room for a longer
// format later and stops oversized input from being hashed and looked up.
public record RefreshRequest(

        @NotBlank(message = "Refresh token is required")
        @Size(max = 128, message = "Refresh token is not valid")
        String refreshToken
) {
}
