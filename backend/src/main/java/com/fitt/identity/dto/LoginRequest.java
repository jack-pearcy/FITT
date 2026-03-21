package com.fitt.identity.dto;

// Inbound DTO for the login endpoint.
public class LoginRequest {

    private String email;
    private String password;

    // Jackson needs this to deserialize the request body.
    // Protected so nothing in application code constructs this directly.
    protected LoginRequest() {}

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}