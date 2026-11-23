package com.fitt.identity.dto;

// The code to pass to the other device, and how long it has.
public record DeviceLinkCodeResponse(String code, long expiresInSeconds) {
}
