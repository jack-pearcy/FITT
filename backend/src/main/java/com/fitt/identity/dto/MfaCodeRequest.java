package com.fitt.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MfaCodeRequest(

        @NotBlank(message = "Code is required")
        @Size(max = 32, message = "Code is too long")
        String code
) {
}
