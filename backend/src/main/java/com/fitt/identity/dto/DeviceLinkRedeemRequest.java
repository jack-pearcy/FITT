package com.fitt.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Sent by the device being connected. A real code is 43 characters. The bound
// stops oversized input from being hashed and looked up.
public record DeviceLinkRedeemRequest(

        @NotBlank(message = "Code is required")
        @Size(max = 128, message = "Code is not valid")
        String code
) {
}
