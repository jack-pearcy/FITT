package com.fitt.identity.dto;

// Inbound DTO for the register endpoint.
public class RegisterRequest {

    private String email;
    private String password;

    // Jackson needs this to deserialize the request body.
    // Protected so nothing in application code constructs this directly.
    protected RegisterRequest() {}

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}