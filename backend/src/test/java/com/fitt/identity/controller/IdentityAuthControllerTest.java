package com.fitt.identity.controller;

import com.fitt.identity.service.AuthTokens;
import com.fitt.identity.service.IdentityAuthService;
import com.fitt.identity.service.exception.EmailAlreadyExistsException;
import com.fitt.identity.service.exception.InvalidRefreshTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controller test for the public auth endpoints. Matches the
// IdentityHealthControllerTest pattern using the full Spring context.
// IdentityAuthService is replaced with a MockitoBean so the test exercises
// only controller wiring and the exception handler chain.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityAuthControllerTest {

    private static final String VALID_EMAIL = "test@example.com";
    private static final String VALID_PASSWORD = "password1234";
    private static final String FAKE_TOKEN = "fake-jwt-token-for-test";
    private static final String FAKE_REFRESH = "fake-refresh-token-for-test";
    private static final AuthTokens FAKE_TOKENS = new AuthTokens(FAKE_TOKEN, FAKE_REFRESH);
    private static final String REFRESH_BODY = "{\"refreshToken\":\"fake-refresh-token-for-test\"}";
    private static final String REQUEST_BODY =
            "{\"email\":\"test@example.com\",\"password\":\"password1234\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityAuthService identityAuthService;

    @Test
    void register_validRequest_returns201WithTokenInBody() throws Exception {
        when(identityAuthService.register(VALID_EMAIL, VALID_PASSWORD)).thenReturn(FAKE_TOKENS);

        mockMvc.perform(post("/api/identity/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(FAKE_TOKEN))
                .andExpect(jsonPath("$.refreshToken").value(FAKE_REFRESH));

        verify(identityAuthService).register(VALID_EMAIL, VALID_PASSWORD);
    }


    @Test
    void register_shortPassword_returns400() throws Exception {
        String shortPasswordBody = "{\"email\":\"test@example.com\",\"password\":\"short\"}";

        mockMvc.perform(post("/api/identity/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shortPasswordBody))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(identityAuthService);
    }
    @Test
    void register_invalidEmail_returns400() throws Exception{
        String invalidEmailBody = "{\"email\":\"test!example#com\",\"password\":\"password1234\"}";

        mockMvc.perform(post("/api/identity/auth/register")
                        .contentType(MediaType.APPLICATION_JSON) 
                        .content(invalidEmailBody))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(identityAuthService);
    }

    @Test
    void register_shortPassword_returnsFieldErrorForPassword() throws Exception {
        String shortPasswordBody = "{\"email\":\"test@example.com\",\"password\":\"short\"}";

        mockMvc.perform(post("/api/identity/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shortPasswordBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.password").exists());

        verifyNoInteractions(identityAuthService);
    }

    @Test
    void register_malformedJson_returns400() throws Exception {
        String brokenJson = "{\"email\":\"test@example.com\",\"password\":";

        mockMvc.perform(post("/api/identity/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brokenJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(identityAuthService);
    }

    @Test
    void register_existingEmail_returns409Conflict() throws Exception {
        when(identityAuthService.register(VALID_EMAIL, VALID_PASSWORD))
                .thenThrow(new EmailAlreadyExistsException("email already exists: " + VALID_EMAIL));

        mockMvc.perform(post("/api/identity/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void login_validCredentials_returns200WithTokenInBody() throws Exception {
        when(identityAuthService.login(VALID_EMAIL, VALID_PASSWORD)).thenReturn(FAKE_TOKENS);

        mockMvc.perform(post("/api/identity/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(FAKE_TOKEN))
                .andExpect(jsonPath("$.refreshToken").value(FAKE_REFRESH));

        verify(identityAuthService).login(VALID_EMAIL, VALID_PASSWORD);
    }

    @Test
    void login_badCredentials_returns401Unauthorized() throws Exception {
        when(identityAuthService.login(VALID_EMAIL, VALID_PASSWORD))
                .thenThrow(new BadCredentialsException("invalid credentials"));

        mockMvc.perform(post("/api/identity/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Bad Credentials"));
    }

    // No Authorization header on purpose: the access token has expired by the
    // time a client refreshes, so the route has to be public.
    @Test
    void refresh_validToken_returns200WithANewPair() throws Exception {
        when(identityAuthService.refresh(FAKE_REFRESH)).thenReturn(new AuthTokens("new-access", "new-refresh"));

        mockMvc.perform(post("/api/identity/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFRESH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
    }

    @Test
    void refresh_rejectedToken_returns401() throws Exception {
        when(identityAuthService.refresh(FAKE_REFRESH)).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/identity/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFRESH_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid Refresh Token"))
                .andExpect(jsonPath("$.detail").value("Refresh token is invalid or expired"));
    }

    @Test
    void refresh_blankToken_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/identity/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.refreshToken").exists());

        verifyNoInteractions(identityAuthService);
    }

    @Test
    void logout_returns204WithoutAnAccessToken() throws Exception {
        mockMvc.perform(post("/api/identity/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFRESH_BODY))
                .andExpect(status().isNoContent());

        verify(identityAuthService).logout(FAKE_REFRESH);
    }
}
