package com.fitt.shared.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Instead of scattering @Value annotations everywhere, @ConfigurationProperties
// binds all "fitt.jwt.*" properties into one typed class. Much cleaner approach.
@ConfigurationProperties(prefix = "fitt.jwt")
public class JwtProperties {

    // The secret used to sign and verify tokens. Has to be at least 32 chars (256 bits)
    // for HS256. Enforced in JwtBeansConfig so it fails fast if misconfigured.
    // Never put a real production value here. Use environment-specific properties files.
    private String secretValue;

    // Gets embedded into every token as the "iss" claim.
    // The decoder checks incoming tokens against this, so both sides need to agree on the value.
    private String issuer = "fitt-api";

    // How long a token stays valid, in seconds. 1 hour by default.
    // Shorter lived tokens limit the damage if one gets stolen.
    private long ttlSeconds = 3600;

    // How long a refresh token stays valid, in seconds. 30 days by default.
    // Each refresh issues a new one, so an active user is never signed out by this;
    // it bounds how long an unused login (a lost phone, an old install) stays usable.
    private long refreshTtlSeconds = 2_592_000;

    public String getSecretValue() {
        return secretValue;
    }

    public void setSecretValue(String secretValue) {
        this.secretValue = secretValue;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public long getRefreshTtlSeconds() {
        return refreshTtlSeconds;
    }

    public void setRefreshTtlSeconds(long refreshTtlSeconds) {
        this.refreshTtlSeconds = refreshTtlSeconds;
    }
}
