package com.fitt.shared.security;

import com.fitt.identity.repository.UserRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

// Rejects an access token whose account has been deleted.
//
// A JWT is checked without the database, so on its own it stays valid until it
// expires, even after its account is gone. A second device could then keep
// writing rows for a user that no longer exists. This costs one primary key
// lookup per request and closes that hour.
public class ExistingAccountValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error ACCOUNT_GONE =
            new OAuth2Error("invalid_token", "The account for this token no longer exists", null);

    private final UserRepository userRepository;

    public ExistingAccountValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object uid = jwt.getClaim("uid");
        if (uid instanceof Number number && userRepository.existsById(number.longValue())) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(ACCOUNT_GONE);
    }
}
