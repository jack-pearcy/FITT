package com.fitt.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Framework-level failures must keep their 4xx status instead of falling into the
// catch-all handler and turning into 500s.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiExceptionHandlerFrameworkErrorsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownPath_returns404NotA500() throws Exception {
        mockMvc.perform(get("/api/does-not-exist").with(jwt().jwt(t -> t.claim("uid", 1L))))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongHttpMethod_returns405() throws Exception {
        mockMvc.perform(delete("/api/health").with(jwt().jwt(t -> t.claim("uid", 1L))))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void missingRequiredQueryParam_returns400() throws Exception {
        mockMvc.perform(get("/api/users/1/progression/suggestions")
                        .with(jwt().jwt(t -> t.claim("uid", 1L))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonNumericPathVariable_returns400() throws Exception {
        mockMvc.perform(get("/api/users/abc/next-lift").with(jwt().jwt(t -> t.claim("uid", 1L))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedMediaType_returns415() throws Exception {
        mockMvc.perform(post("/api/identity/auth/login")
                        .contentType(MediaType.TEXT_PLAIN).content("nope"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void malformedJson_stillReturnsTheCleanBadRequestBody() throws Exception {
        mockMvc.perform(post("/api/identity/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed request body"));
    }

    @Test
    void validationFailure_keepsPerFieldErrorsMap() throws Exception {
        mockMvc.perform(post("/api/identity/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.properties.errors.email").exists())
                .andExpect(jsonPath("$.properties.errors.password").exists());
    }
}
