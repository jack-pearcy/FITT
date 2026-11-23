package com.fitt.identity.service;

// What a correct email and password lead to. For most accounts that is a token
// pair. For an account with two-step sign-in it is a short-lived token that
// only buys the second step, and no access to anything yet.
public record LoginResult(AuthTokens tokens, String mfaToken) {

    public static LoginResult signedIn(AuthTokens tokens) {
        return new LoginResult(tokens, null);
    }

    public static LoginResult needsSecondStep(String mfaToken) {
        return new LoginResult(null, mfaToken);
    }

    public boolean needsSecondStep() {
        return mfaToken != null;
    }
}
