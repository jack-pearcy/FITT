package com.fitt.identity.dto;

// Shown once. The secret is for typing into an authenticator app by hand, and
// otpauthUri is the same thing in the form a QR code carries.
public record TotpSetupResponse(String secret, String otpauthUri) {
}
