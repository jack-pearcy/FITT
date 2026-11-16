package com.fitt.identity.service;

// What a successful register, login, or refresh hands back: a short-lived
// access token for the Authorization header, and the refresh token that buys the next one.
public record AuthTokens(String accessToken, String refreshToken) {
}
