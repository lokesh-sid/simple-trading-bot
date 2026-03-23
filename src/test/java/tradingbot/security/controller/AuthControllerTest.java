package tradingbot.security.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import tradingbot.AbstractContainerIntegrationTest;
import tradingbot.agent.infrastructure.llm.LLMProvider;
import tradingbot.security.dto.RegisterRequest;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("container-test")
@DisplayName("AuthController Integration Tests (Testcontainers Postgres)")
class AuthControllerTest extends AbstractContainerIntegrationTest {


    @MockitoBean
    private LLMProvider llmProvider;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;


    @Test
    @DisplayName("Should register user successfully (integration)")
    void testRegisterSuccess() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
            "testuser", "test@example.com", "Test123!@#", "Test User"
        );
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.access_token").exists())
            .andExpect(jsonPath("$.username").value("testuser"))
            .andExpect(jsonPath("$.error").doesNotExist());
    }

    // Add more integration tests for login, refresh, etc. using MockMvc as above.
}
