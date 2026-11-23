package com.fitt.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Sent by the signed in device that wants to connect another one. The refresh
// token proves it holds the sign-in itself and not only an access token.
public record DeviceLinkRequest(

        @NotBlank(message = "Refresh token is required")
        @Size(max = 128, message = "Refresh token is not valid")
        String refreshToken
) {
}
