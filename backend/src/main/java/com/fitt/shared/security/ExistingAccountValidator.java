package com.fitt.shared.security;

import com.fitt.identity.domain.UserEntity;
import com.fitt.identity.repository.UserRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

// Rejects an access token whose account has been deleted, or whose password has
// been changed since the token was issued.
//
// A JWT is checked without the database, so on its own it stays valid until it
// expires, whatever has happened to the account. A second device could keep
// writing rows for a deleted user, and a thief could keep using a stolen token
// after the owner changed the password. This costs one primary key lookup per
// request and closes that hour.
public class ExistingAccountValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error ACCOUNT_GONE =
            new OAuth2Error("invalid_token", "The account for this token no longer exists", null);
    private static final OAuth2Error SUPERSEDED =
            new OAuth2Error("invalid_token", "The token was issued before the password changed", null);

    private final UserRepository userRepository;

    public ExistingAccountValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (!(jwt.getClaim("uid") instanceof Number uid)) {
            return OAuth2TokenValidatorResult.failure(ACCOUNT_GONE);
        }
        Optional<UserEntity> user = userRepository.findById(uid.longValue());
        if (user.isEmpty()) {
            return OAuth2TokenValidatorResult.failure(ACCOUNT_GONE);
        }
        // A token from before the version claim existed has none, and counts as 0,
        // the value every account starts with.
        int tokenVersion = jwt.getClaim("ver") instanceof Number version ? version.intValue() : 0;
        if (tokenVersion != user.get().getTokenVersion()) {
            return OAuth2TokenValidatorResult.failure(SUPERSEDED);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
