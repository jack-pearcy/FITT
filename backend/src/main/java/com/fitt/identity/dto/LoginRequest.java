package com.fitt.identity.dto;

// Inbound DTO for the login endpoint.
// Just carries the username and password in from the request body.
public class LoginRequest {

    private String username;
    private String password;

    // Jackson needs no arg constructor to deserialize JSON into this object.
    // Protected scope so nothing in application code calls it directly.
    protected LoginRequest() {
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
