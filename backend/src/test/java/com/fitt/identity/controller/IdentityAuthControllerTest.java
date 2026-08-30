package com.fitt.identity.controller;

import com.fitt.identity.service.IdentityAuthService;
import com.fitt.identity.service.exception.EmailAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
    private static final String REQUEST_BODY =
            "{\"email\":\"test@example.com\",\"password\":\"password1234\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityAuthService identityAuthService;

    @Test
    void register_validRequest_returns201WithTokenInBody() throws Exception {
        when(identityAuthService.register(VALID_EMAIL, VALID_PASSWORD)).thenReturn(FAKE_TOKEN);

        mockMvc.perform(post("/api/identity/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(FAKE_TOKEN));

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
        when(identityAuthService.login(VALID_EMAIL, VALID_PASSWORD)).thenReturn(FAKE_TOKEN);

        mockMvc.perform(post("/api/identity/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(FAKE_TOKEN));

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
}