package com.fitt.identity.dto;

// What gets sent back to the client after a successful register, login, or refresh.
// The client attaches the access token to every request as:
// Authorization: Bearer <token>
// and keeps the refresh token to trade for a new pair once the access token expires.
public record TokenResponse(String token, String refreshToken) {
}
