package tradingbot.security.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tradingbot.AbstractContainerIntegrationTest;
import tradingbot.config.ContainerIntegrationTestConfig;

@SpringBootTest(classes = ContainerIntegrationTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("container-test")
public class DirectAuthAccessIntegrationTest extends AbstractContainerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testDirectApiAuthIsDeniedFromExternalIp() throws Exception {
        mockMvc.perform(get("/api/auth/health")
                .with(request -> {
                    request.setRemoteAddr("192.168.1.100");
                    return request;
                }))
                .andExpect(status().isForbidden()); // Spring Security defaults to 403 Forbidden for missing auth on protected endpoints // Unauthenticated because it matches the fallback block
    }

    @Test
    public void testDirectApiAuthIsAllowedFromLoopback() throws Exception {
        mockMvc.perform(get("/api/auth/health")
                .with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk());
    }
}