package com.fitt.identity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// What login answers with. One of two shapes, and absent fields are left out:
//   signed in:        {"token": "...", "refreshToken": "..."}
//   second step due:  {"mfaRequired": true, "mfaToken": "..."}
// The first is byte for byte what login always returned, so a client that knows
// nothing about two-step sign-in keeps working for every account without it.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(String token, String refreshToken, Boolean mfaRequired, String mfaToken) {

    public static LoginResponse signedIn(String token, String refreshToken) {
        return new LoginResponse(token, refreshToken, null, null);
    }

    public static LoginResponse secondStepDue(String mfaToken) {
        return new LoginResponse(null, null, true, mfaToken);
    }
}
