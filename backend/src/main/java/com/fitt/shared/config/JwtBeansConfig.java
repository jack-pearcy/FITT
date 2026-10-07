package com.fitt.shared.config;

import com.fitt.shared.properties.JwtProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

// All JWT infrastructure lives here: secret key, decoder, and encoder.
// @EnableConfigurationProperties is what actually activates JwtProperties binding.
// Without it the fields stay null and nothing works.
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtBeansConfig {

    // Converts the plain string secret into a real cryptographic key.
    // SecretKeySpec wraps raw bytes into something the JCA (Java Cryptography Architecture)
    // understands. Failing fast here is intentional. Better than a silent misconfiguration
    // that only shows up at runtime.
    @Bean
    public SecretKey jwtSecretKey(JwtProperties props) {
        byte[] keyBytes = props.getSecretValue().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "fitt.jwt.secret-value must be at least 32 characters (256 bits) for HS256"
            );
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    // The decoder is used automatically by Spring Security's BearerTokenAuthenticationFilter.
    // On every protected request it checks the signature, verifies expiration, and now also
    // validates the issuer claim against the configured value, so a token signed by this key
    // but minted for a different issuer is rejected.
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new JwtIssuerValidator(props.getIssuer())));
        return decoder;
    }

    // The encoder gets called explicitly at login to create and sign a new token.
    // Nimbus requires the key in JWK (JSON Web Key) format. OctetSequenceKey is the
    // correct JWK type for symmetric keys like this one.
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(jwtSecretKey.getEncoded())
                .algorithm(com.nimbusds.jose.JWSAlgorithm.HS256)
                .keyID("fitt-key-1")   // Nimbus requires a key ID to select the right key from the set
                .build();
        ImmutableJWKSet<SecurityContext> jwkSet = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwkSet);
    }

    // Spring Boot 4 / Jackson 3 auto configures ObjectMapper, but the security handlers
    // inject JsonMapper by type specifically. Declaring it here lets Spring resolve that
    // dependency. JsonMapper is the concrete JSON only subclass of ObjectMapper.
    @Bean
    public JsonMapper jsonMapper() {
        return JsonMapper.builder().build();
    }
}