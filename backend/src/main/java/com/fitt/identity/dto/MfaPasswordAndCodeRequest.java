package com.fitt.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// For turning two-step sign-in off and for new recovery codes: both the
// password and a current code.
public record MfaPasswordAndCodeRequest(

        @NotBlank(message = "Password is required")
        @Size(max = 256, message = "Password is too long")
        String password,

        @NotBlank(message = "Code is required")
        @Size(max = 32, message = "Code is too long")
        String code
) {
}
