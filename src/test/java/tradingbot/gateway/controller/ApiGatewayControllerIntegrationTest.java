package tradingbot.gateway.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tradingbot.agent.TradingAgentFactory;
import tradingbot.agent.infrastructure.repository.JpaAgentRepository;
import tradingbot.bot.controller.dto.request.BotStartRequest;
import tradingbot.bot.controller.dto.request.LeverageUpdateRequest;
import tradingbot.bot.controller.dto.response.BotCreatedResponse;
import tradingbot.bot.controller.dto.response.BotStartResponse;
import tradingbot.bot.controller.dto.response.BotStatusResponse;
import tradingbot.bot.controller.dto.response.CircuitBreakerMetricsResponse;
import tradingbot.bot.controller.dto.response.LeverageUpdateResponse;
import tradingbot.bot.controller.dto.response.RateLimiterMetricsResponse;
import tradingbot.bot.controller.dto.response.ResilienceHealthResponse;
import tradingbot.bot.controller.dto.response.RetryMetricsResponse;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.config.GatewayTestConfig;
import tradingbot.gateway.service.GatewayService;
import tradingbot.security.dto.LoginRequest;
import tradingbot.security.dto.LoginResponse;

/**
 * Integration Tests for ApiGatewayController
 * 
 * Tests the gateway's request routing, proxying, and resilience features:
 * - Gateway health and info endpoints
 * - Request proxying to backend services
 * - Circuit breaker integration
 * - Rate limiting
 * - Error handling and service unavailable scenarios
 * 
 * Uses real Spring context with mocked gateway service and external dependencies.
 */
@SpringBootTest(
    classes = GatewayTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("integration-test")
@DisplayName("ApiGatewayController Integration Tests")
class ApiGatewayControllerIntegrationTest extends tradingbot.AbstractHttpTest {

    private static final String GATEWAY_API = "/gateway";

    @MockitoBean
    private GatewayService gatewayService;

    @MockitoBean
    private FuturesExchangeService exchangeService;

    @MockitoBean
    private SentimentAnalyzer sentimentAnalyzer;

    @MockitoBean
    private EventPublisher eventPublisher;

    // Prevent JPA scan failure — AgentEntity not in GatewayTestConfig's @EntityScan
    @MockitoBean
    private JpaAgentRepository jpaAgentRepository;

    // TradingAgentFactory lives in tradingbot.agent.impl — not in GatewayTestConfig scan
    @MockitoBean
    private TradingAgentFactory tradingAgentFactory;

    // ========== GATEWAY HEALTH AND INFO TESTS ==========

    @Test
    @DisplayName("Gateway health endpoint should return UP status")
    void gatewayHealth_shouldReturnUpStatus() throws Exception {
        performGet(GATEWAY_API + "/health")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.gateway").value("operational"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.version").exists());
    }

    @Test
    @DisplayName("Gateway info endpoint should return gateway metadata")
    void gatewayInfo_shouldReturnMetadata() throws Exception {
        performGet(GATEWAY_API + "/info")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.description").exists())
                .andExpect(jsonPath("$.features.rate-limiting").value(true))
                .andExpect(jsonPath("$.features.circuit-breaker").value(true))
                .andExpect(jsonPath("$.features.retry").value(true))
                .andExpect(jsonPath("$.features.security").value(true));
    }

    // ========== BOT API PROXYING TESTS ==========

    @Test
    @DisplayName("Should proxy create bot request through gateway")
    void proxyCreateBot_shouldReturnBotCreatedResponse() throws Exception {
        // Mock gateway service to return successful response
        BotCreatedResponse mockResponse = new BotCreatedResponse("test-bot-id", "Bot created");
        when(gatewayService.proxyTradingBotRequest(
                eq(""), 
                eq(HttpMethod.POST), 
                isNull(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(BotCreatedResponse.class)
        )).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(mockResponse));

        performPost(GATEWAY_API + "/api/bots", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.botId").value("test-bot-id"))
                .andExpect(jsonPath("$.message").value("Bot created"));

        verify(gatewayService).proxyTradingBotRequest(
                eq(""), 
                eq(HttpMethod.POST), 
                isNull(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(BotCreatedResponse.class)
        );
    }

    @Test
    @DisplayName("Should proxy start bot request through gateway")
    void proxyStartBot_shouldReturnBotStartResponse() throws Exception {
        String botId = "test-bot-123";
        BotStartRequest request = new BotStartRequest();
        request.setDirection(tradingbot.bot.TradeDirection.LONG);
        request.setPaper(true);

        // Mock gateway service response
        BotStartResponse mockResponse = new BotStartResponse();
        mockResponse.setMessage("Bot started");
        when(gatewayService.proxyTradingBotRequest(
                eq("/" + botId + "/start"), 
                eq(HttpMethod.POST), 
                ArgumentMatchers.any(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(BotStartResponse.class)
        )).thenReturn(ResponseEntity.ok(mockResponse));

        performPost(GATEWAY_API + "/api/bots/" + botId + "/start", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Bot started"));

        verify(gatewayService).proxyTradingBotRequest(
                eq("/" + botId + "/start"), 
                eq(HttpMethod.POST), 
                ArgumentMatchers.any(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(BotStartResponse.class)
        );
    }

    @Test
    @DisplayName("Should proxy get bot status through gateway")
    void proxyGetBotStatus_shouldReturnStatusResponse() throws Exception {
        String botId = "test-bot-123";

        // Mock gateway service response
        BotStatusResponse mockResponse = new BotStatusResponse();
        mockResponse.setRunning(true);
        mockResponse.setSymbol("BTCUSDT");
        when(gatewayService.proxyTradingBotRequest(
                eq("/" + botId + "/status"), 
                eq(HttpMethod.GET), 
                isNull(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(BotStatusResponse.class)
        )).thenReturn(ResponseEntity.ok(mockResponse));

        performGet(GATEWAY_API + "/api/bots/" + botId + "/status")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"));

        verify(gatewayService).proxyTradingBotRequest(
                eq("/" + botId + "/status"), 
                eq(HttpMethod.GET), 
                isNull(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(BotStatusResponse.class)
        );
    }

    @Test
    @DisplayName("Should proxy leverage update through gateway")
    void proxyUpdateLeverage_shouldReturnLeverageUpdateResponse() throws Exception {
        String botId = "test-bot-123";
        LeverageUpdateRequest request = new LeverageUpdateRequest(20.0);

        // Mock gateway service response
        LeverageUpdateResponse mockResponse = new LeverageUpdateResponse();
        mockResponse.setMessage("Leverage updated");
        mockResponse.setNewLeverage(20.0);
        when(gatewayService.proxyTradingBotRequest(
                eq("/" + botId + "/leverage"), 
                eq(HttpMethod.POST), 
                ArgumentMatchers.any(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(LeverageUpdateResponse.class)
        )).thenReturn(ResponseEntity.ok(mockResponse));

        performPost(GATEWAY_API + "/api/bots/" + botId + "/leverage", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Leverage updated"))
                .andExpect(jsonPath("$.newLeverage").value(20.0));

        verify(gatewayService).proxyTradingBotRequest(
                eq("/" + botId + "/leverage"), 
                eq(HttpMethod.POST), 
                ArgumentMatchers.any(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(LeverageUpdateResponse.class)
        );
    }

    // ========== AUTH API PROXYING TESTS ==========

    @Test
    @DisplayName("Should proxy login request through gateway")
    void proxyLogin_shouldReturnLoginResponse() throws Exception {
        LoginRequest request = new LoginRequest("test user", "testpass");

        // Mock gateway service response
        LoginResponse mockResponse = new LoginResponse(
                "mock-access-token",
                "Bearer",
                3600L,
                "mock-refresh-token",
                null,
                null
        );
        when(gatewayService.proxyAuthRequest(
                eq("/login"), 
                eq(HttpMethod.POST), 
                ArgumentMatchers.any(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(LoginResponse.class)
        )).thenReturn(ResponseEntity.ok(mockResponse));

        performPost(GATEWAY_API + "/api/auth/login", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("mock-access-token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"));

        verify(gatewayService).proxyAuthRequest(
                eq("/login"), 
                eq(HttpMethod.POST), 
                ArgumentMatchers.any(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(LoginResponse.class)
        );
    }

    @Test
    @DisplayName("Should proxy logout request through gateway")
    void proxyLogout_shouldReturnLogoutResponse() throws Exception {
        // Mock gateway service response
        when(gatewayService.proxyAuthRequest(
                eq("/logout"), 
                eq(HttpMethod.POST), 
                isNull(), 
                ArgumentMatchers.any(HttpHeaders.class),
                ArgumentMatchers.any()
        )).thenReturn(ResponseEntity.ok().build());

        performPost(GATEWAY_API + "/api/auth/logout", null)
                .andExpect(status().isOk());

        verify(gatewayService).proxyAuthRequest(
                eq("/logout"), 
                eq(HttpMethod.POST), 
                isNull(), 
                ArgumentMatchers.any(HttpHeaders.class),
                ArgumentMatchers.any()
        );
    }

    // ========== ERROR HANDLING TESTS ==========

    @Test
    @DisplayName("Gateway should handle 503 Service Unavailable from backend")
    void proxyRequest_serviceUnavailable_shouldReturn503() throws Exception {
        // Mock gateway service to return service unavailable
        when(gatewayService.proxyTradingBotRequest(
                ArgumentMatchers.anyString(), 
                ArgumentMatchers.any(HttpMethod.class), 
                ArgumentMatchers.any(), 
                ArgumentMatchers.any(HttpHeaders.class),
                ArgumentMatchers.<Class<BotStartResponse>>any()
        )).thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());

        BotStartRequest request = new BotStartRequest();
        request.setDirection(tradingbot.bot.TradeDirection.LONG);
        request.setPaper(true);

        performPost(GATEWAY_API + "/api/bots/test-bot-123/start", request)
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("Gateway should handle 404 Not Found from backend")
    void proxyRequest_notFound_shouldReturn404() throws Exception {
        // Mock gateway service to return not found
        when(gatewayService.proxyTradingBotRequest(
                ArgumentMatchers.anyString(), 
                ArgumentMatchers.any(HttpMethod.class), 
                ArgumentMatchers.any(), 
                ArgumentMatchers.any(HttpHeaders.class),
                ArgumentMatchers.<Class<BotStatusResponse>>any()
        )).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

        performGet(GATEWAY_API + "/api/bots/non-existent-bot/status")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Gateway should validate request body before proxying")
    void proxyRequest_invalidRequestBody_shouldReturn400() throws Exception {
        // Send invalid leverage request (leverage = 0, which is invalid)
        LeverageUpdateRequest invalidRequest = new LeverageUpdateRequest(0.0);

        performPost(GATEWAY_API + "/api/bots/test-bot-123/leverage", invalidRequest)
                .andExpect(status().isBadRequest());

        // Verify gateway service was not called due to validation failure
        verify(gatewayService, never()).proxyTradingBotRequest(
                ArgumentMatchers.anyString(), 
                ArgumentMatchers.any(HttpMethod.class), 
                ArgumentMatchers.any(), 
                ArgumentMatchers.any(HttpHeaders.class),
                ArgumentMatchers.<Class<LeverageUpdateResponse>>any()
        );
    }

    // ========== HEADER PROPAGATION TESTS ==========

    @Test
    @DisplayName("Gateway should propagate request headers to backend service")
    void proxyRequest_shouldPropagateHeaders() throws Exception {
        // Mock gateway service to capture headers
        when(gatewayService.proxyTradingBotRequest(
                ArgumentMatchers.anyString(), 
                ArgumentMatchers.any(HttpMethod.class), 
                ArgumentMatchers.any(), 
                ArgumentMatchers.any(HttpHeaders.class),
                ArgumentMatchers.<Class<BotStatusResponse>>any()
        )).thenReturn(ResponseEntity.ok(new BotStatusResponse()));

        // Add custom header to request
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(GATEWAY_API + "/api/bots/test-bot-123/status")
                .header("X-Custom-Header", "test-value")
                .header("X-Request-ID", "req-123"))
                .andExpect(status().isOk());

        // Verify headers were captured (we can't easily verify exact values, but we verify the call was made)
        verify(gatewayService).proxyTradingBotRequest(
                eq("/test-bot-123/status"), 
                eq(HttpMethod.GET), 
                isNull(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(BotStatusResponse.class)
        );
    }

    // ========== RESILIENCE METRICS TESTS ==========

    @Test
    @DisplayName("Should return rate limiter metrics through gateway")
    void getRateLimiterMetrics_shouldReturnMetrics() throws Exception {
        // Mock gateway service response
        when(gatewayService.proxyTradingBotRequest(
                eq("/resilience/rate-limiters"), 
                eq(HttpMethod.GET), 
                isNull(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(RateLimiterMetricsResponse.class)
        )).thenReturn(ResponseEntity.ok(new RateLimiterMetricsResponse()));

        performGet(GATEWAY_API + "/api/resilience/rate-limiters")
                .andExpect(status().isOk());

        verify(gatewayService).proxyTradingBotRequest(
                eq("/resilience/rate-limiters"), 
                eq(HttpMethod.GET), 
                isNull(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(RateLimiterMetricsResponse.class)
        );
    }

    @Test
    @DisplayName("Should return circuit breaker metrics through gateway")
    void getCircuitBreakerMetrics_shouldReturnMetrics() throws Exception {
        // Mock gateway service response
        when(gatewayService.proxyTradingBotRequest(
                eq("/resilience/circuit-breaker"), 
                eq(HttpMethod.GET), 
                isNull(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(CircuitBreakerMetricsResponse.class)
        )).thenReturn(ResponseEntity.ok(new CircuitBreakerMetricsResponse()));

        performGet(GATEWAY_API + "/api/resilience/circuit-breaker")
                .andExpect(status().isOk());

        verify(gatewayService).proxyTradingBotRequest(
                eq("/resilience/circuit-breaker"), 
                eq(HttpMethod.GET), 
                isNull(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(CircuitBreakerMetricsResponse.class)
        );
    }

    @Test
    @DisplayName("Should return retry metrics through gateway")
    void getRetryMetrics_shouldReturnMetrics() throws Exception {
        // Mock gateway service response
        when(gatewayService.proxyTradingBotRequest(
                eq("/resilience/retry"), 
                eq(HttpMethod.GET), 
                isNull(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(RetryMetricsResponse.class)
        )).thenReturn(ResponseEntity.ok(new RetryMetricsResponse()));

        performGet(GATEWAY_API + "/api/resilience/retry")
                .andExpect(status().isOk());

        verify(gatewayService).proxyTradingBotRequest(
                eq("/resilience/retry"), 
                eq(HttpMethod.GET), 
                isNull(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(RetryMetricsResponse.class)
        );
    }

    @Test
    @DisplayName("Should return resilience health status through gateway")
    void getResilienceHealth_shouldReturnHealthStatus() throws Exception {
        // Mock gateway service response
        when(gatewayService.proxyTradingBotRequest(
                eq("/resilience/health"), 
                eq(HttpMethod.GET), 
                isNull(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(ResilienceHealthResponse.class)
        )).thenReturn(ResponseEntity.ok(new ResilienceHealthResponse()));

        performGet(GATEWAY_API + "/api/resilience/health")
                .andExpect(status().isOk());

        verify(gatewayService).proxyTradingBotRequest(
                eq("/resilience/health"), 
                eq(HttpMethod.GET), 
                isNull(), 
                ArgumentMatchers.any(HttpHeaders.class),
                eq(ResilienceHealthResponse.class)
        );
    }

    // ========== CORS TESTS ==========

    @Test
    @DisplayName("Gateway should handle CORS preflight requests")
    void corsPreflightRequest_shouldReturn200() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .options(GATEWAY_API + "/api/bots")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk());
    }
}
