package com.fitt.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Activates application-test.properties so the context loads with
// H2 and the JWT config instead of requiring a real MySQL connection.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void identityHealth_isPublic() throws Exception {
        mockMvc.perform(get("/api/identity/health"))
                .andExpect(status().isOk());
    }
}