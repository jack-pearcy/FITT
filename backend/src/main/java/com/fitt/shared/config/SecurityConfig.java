package com.fitt.shared.config;

import com.fitt.shared.security.RestAccessDeniedHandler;
import com.fitt.shared.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // Defines the rules for every HTTP request entering the app.
    // Spring Security is a chain of filters. Requests flow through them
    // in order before ever reaching a controller.
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {

        return http
                // CSRF protection is meant for browser cookie/session flows.
                // A stateless token API does not need it.
                .csrf(AbstractHttpConfigurer::disable)

                // Never create an HttpSession. Every request proves identity via its token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Health endpoints need to be reachable without credentials.
                        // Monitoring tools and load balancers depend on this.
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/identity/health").permitAll()
                        // Login has to be public. The client has no token yet at this point.
                        .requestMatchers("/api/identity/auth/login").permitAll()
                        // Everything else requires a valid Bearer token.
                        .anyRequest().authenticated()
                )

                // Activates the BearerTokenAuthenticationFilter which reads the Authorization
                // header, extracts the token, and runs it through the JwtDecoder bean.
                // All automatic once declared here.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))

                // Custom error responses wired in here.
                // authenticationEntryPoint handles 401 (missing or invalid token).
                // accessDeniedHandler handles 403 (valid token but wrong permissions).
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                .build();
    }

    // AuthenticationManager is what actually validates credentials.
    // IdentityAuthService passes it a username and password, and it either returns
    // an authenticated principal or throws if the credentials are wrong.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // Phase 6 placeholder. One hardcoded in-memory user for local dev and smoke testing.
    // The uid=1 in IdentityAuthService is tied to this user specifically.
    // This gets replaced by a real UserDetailsService that hits the database in a future phase.
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("testuser")
                        .password(passwordEncoder.encode("password123"))
                        .roles("USER")
                        .build()
        );
    }

    // BCrypt is the standard for password hashing. It is intentionally slow to resist
    // brute force attacks. Passwords never get stored or compared as plain text.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}