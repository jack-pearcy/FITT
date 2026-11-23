package com.fitt.identity.dto;

// available is false when the server has two-step sign-in switched off, so a
// client can hide the setting instead of offering something that will fail.
public record MfaStatusResponse(boolean available, boolean totpEnabled, long recoveryCodesLeft) {
}
