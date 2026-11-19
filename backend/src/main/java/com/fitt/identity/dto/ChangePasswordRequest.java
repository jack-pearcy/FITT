package com.fitt.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The current password proves it is the owner asking. The new one follows the
// same rule as registration.
public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 12, max = 72, message = "Password must be between 12 and 72 characters")
        String newPassword
) {
}
