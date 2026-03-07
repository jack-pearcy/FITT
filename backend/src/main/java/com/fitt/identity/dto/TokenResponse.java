package com.fitt.identity.dto;

// What gets sent back to the client after a successful login.
// The client stores this and attaches it to every future request as:
// Authorization: Bearer <token>
public class TokenResponse {

    private final String token;

    public TokenResponse(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}