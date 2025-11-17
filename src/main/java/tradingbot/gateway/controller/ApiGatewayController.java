package tradingbot.gateway.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import tradingbot.bot.controller.dto.request.BotStartRequest;
import tradingbot.bot.controller.dto.request.LeverageUpdateRequest;
import tradingbot.bot.controller.dto.request.SentimentUpdateRequest;
import tradingbot.bot.controller.dto.response.AllResilienceMetricsResponse;
import tradingbot.bot.controller.dto.response.BotCreatedResponse;
import tradingbot.bot.controller.dto.response.BotDeletedResponse;
import tradingbot.bot.controller.dto.response.BotListResponse;
import tradingbot.bot.controller.dto.response.BotStartResponse;
import tradingbot.bot.controller.dto.response.BotStatusResponse;
import tradingbot.bot.controller.dto.response.BotStopResponse;
import tradingbot.bot.controller.dto.response.CircuitBreakerMetricsResponse;
import tradingbot.bot.controller.dto.response.ErrorResponse;
import tradingbot.bot.controller.dto.response.GatewayHealthResponse;
import tradingbot.bot.controller.dto.response.GatewayInfoResponse;
import tradingbot.bot.controller.dto.response.LeverageUpdateResponse;
import tradingbot.bot.controller.dto.response.RateLimiterMetricsResponse;
import tradingbot.bot.controller.dto.response.ResilienceHealthResponse;
import tradingbot.bot.controller.dto.response.RetryMetricsResponse;
import tradingbot.bot.controller.dto.response.SentimentUpdateResponse;
import tradingbot.gateway.service.GatewayService;
import tradingbot.security.dto.HealthResponse;
import tradingbot.security.dto.LoginRequest;
import tradingbot.security.dto.LoginResponse;
import tradingbot.security.dto.LogoutResponse;
import tradingbot.security.dto.RefreshTokenRequest;
import tradingbot.security.dto.RegisterRequest;

/**
 * API Gateway Controller for routing and proxying requests to backend services
 * 
 * Provides a centralized entry point with:
 * - Request routing and proxying
 * - Rate limiting
 * - Circuit breaker patterns
 * - Request/response transformation
 * - Security enforcement
 * - Header propagation and enrichment
 * 
 * Spring Framework Features:
 * - Automatic header extraction via @RequestHeader
 * - Bean validation with @Validated and @Valid
 * - CORS support with @CrossOrigin
 * - Externalized configuration with @Value
 */
@RestController
@RequestMapping("/gateway")
@Validated
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8080"})
@Tag(name = "API Gateway", description = "Centralized API Gateway for routing requests to backend services")
public class ApiGatewayController {
    
    private final GatewayService gatewayService;
    
    @Value("${gateway.version:1.0.0}")
    private String gatewayVersion;
    
    @Value("${gateway.name:Simple Trading Bot API Gateway}")
    private String gatewayName;
    
    public ApiGatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }
    
    // Trading Bot API Gateway Routes
    
    @PostMapping("/api/bots")
    @Operation(summary = "Create a new trading bot via gateway", 
               description = "Creates a new bot instance and returns its unique ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Bot created successfully",
                    content = @Content(schema = @Schema(implementation = BotCreatedResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotCreatedResponse> createBot(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest(
            "", 
            HttpMethod.POST, 
            null, 
            headers,
            BotCreatedResponse.class
        );
    }
    
    @PostMapping("/api/bots/{botId}/start")
    @Operation(summary = "Start trading bot via gateway", 
               description = "Routes request to start the specified trading bot through the API gateway")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot started successfully",
                    content = @Content(schema = @Schema(implementation = BotStartResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotStartResponse> startBot(
            @PathVariable String botId,
            @Valid @RequestBody BotStartRequest requestBody,
            HttpServletRequest request) {
        
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest(
            "/" + botId + "/start", 
            HttpMethod.POST, 
            requestBody, 
            headers,
            BotStartResponse.class
        );
    }
    
        @PostMapping("/api/bots/{botId}/stop")
    @Operation(summary = "Stop trading bot via gateway")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot stopped successfully",
                    content = @Content(schema = @Schema(implementation = BotStopResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotStopResponse> stopBot(
            @PathVariable String botId,
            HttpServletRequest request) {
        
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest(
            "/" + botId + "/stop", 
            HttpMethod.POST, 
            null, 
            headers,
            BotStopResponse.class
        );
    }
    
        @GetMapping("/api/bots/{botId}/status")
    @Operation(summary = "Get bot status via gateway")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BotStatusResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotStatusResponse> getBotStatus(
            @PathVariable String botId,
            HttpServletRequest request) {
        
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest(
            "/" + botId + "/status", 
            HttpMethod.GET, 
            null, 
            headers,
            BotStatusResponse.class
        );
    }
    
    @PostMapping("/api/bots/{botId}/leverage")
    @Operation(summary = "Set leverage via gateway",
               description = "Update the leverage for the specified trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Leverage updated successfully",
                    content = @Content(schema = @Schema(implementation = LeverageUpdateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid leverage value",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<LeverageUpdateResponse> setLeverage(
            @PathVariable String botId,
            @Valid @RequestBody LeverageUpdateRequest requestBody,
            HttpServletRequest request) {
        
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest(
            "/" + botId + "/leverage", 
            HttpMethod.POST, 
            requestBody, 
            headers,
            LeverageUpdateResponse.class
        );
    }
    
    @PostMapping("/api/bots/{botId}/sentiment")
    @Operation(summary = "Set sentiment analysis via gateway",
               description = "Toggles sentiment analysis for the specified trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Sentiment setting updated successfully",
                    content = @Content(schema = @Schema(implementation = SentimentUpdateResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SentimentUpdateResponse> setSentiment(
            @PathVariable String botId,
            @Valid @RequestBody SentimentUpdateRequest requestBody,
            HttpServletRequest request) {
        
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest(
            "/" + botId + "/sentiment", 
            HttpMethod.POST, 
            requestBody, 
            headers,
            SentimentUpdateResponse.class
        );
    }
    
    @GetMapping("/api/bots")
    @Operation(summary = "List all trading bots with filtering and pagination",
               description = "Returns a paginated list of bots with optional filters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot list retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BotListResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid filter parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotListResponse> listBots(
            @RequestParam(required = false) @Parameter(description = "Filter by bot status (RUNNING, STOPPED, ERROR)") String status,
            @RequestParam(required = false) @Parameter(description = "Filter by paper trading mode") Boolean paper,
            @RequestParam(required = false) @Parameter(description = "Filter by trade direction (LONG, SHORT)") String direction,
            @RequestParam(required = false) @Parameter(description = "Search in botId or symbol") String search,
            @RequestParam(defaultValue = "0") @Parameter(description = "Page number (0-indexed)") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "Page size (1-100)") int size,
            @RequestParam(defaultValue = "createdAt") @Parameter(description = "Sort field (botId, createdAt, status, symbol)") String sortBy,
            @RequestParam(defaultValue = "DESC") @Parameter(description = "Sort order (ASC, DESC)") String sortOrder,
            HttpServletRequest request) {

        HttpHeaders headers = extractHeaders(request);
        
        // Build URL with query parameters
        StringBuilder urlBuilder = new StringBuilder("?");
        urlBuilder.append("page=").append(page);
        urlBuilder.append("&size=").append(size);
        urlBuilder.append("&sortBy=").append(sortBy);
        urlBuilder.append("&sortOrder=").append(sortOrder);

        // Add optional filters
        if (status != null && !status.isEmpty()) {
            urlBuilder.append("&status=").append(status);
        }
        if (paper != null) {
            urlBuilder.append("&paper=").append(paper);
        }
        if (direction != null && !direction.isEmpty()) {
            urlBuilder.append("&direction=").append(direction);
        }
        if (search != null && !search.isEmpty()) {
            urlBuilder.append("&search=").append(search);
        }

        return gatewayService.proxyTradingBotRequest(
            urlBuilder.toString(),
            HttpMethod.GET,
            null,
            headers,
            BotListResponse.class
        );
    }
    
    @DeleteMapping("/api/bots/{botId}")
    @Operation(summary = "Delete a bot via gateway",
               description = "Stops and removes the specified trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot deleted successfully",
                    content = @Content(schema = @Schema(implementation = BotDeletedResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotDeletedResponse> deleteBot(
            @PathVariable String botId,
            HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest(
            "/" + botId, 
            HttpMethod.DELETE, 
            null, 
            headers,
            BotDeletedResponse.class
        );
    }
    
    // Authentication API Gateway Routes
    
    @PostMapping("/api/auth/register")
    @Operation(summary = "User registration via gateway",
               description = "Register a new user with username, password, and email. Provides OAuth 2.0 compliant response with rate limiting and brute force protection.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User registered successfully",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request or user already exists",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "429", description = "Too many requests - rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Authentication service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<LoginResponse> register(
            @Valid @RequestBody RegisterRequest requestBody,
            HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyAuthRequest(
            "/register",
            HttpMethod.POST,
            requestBody,
            headers,
            LoginResponse.class
        );
    }
    
    @PostMapping("/api/auth/login")
    @Operation(summary = "User login via gateway",
               description = "Authenticate user and receive OAuth 2.0 compliant access and refresh tokens. Protected by rate limiting to prevent brute force attacks.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "429", description = "Too many login attempts - rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Authentication service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest requestBody,
            HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyAuthRequest(
            "/login",
            HttpMethod.POST,
            requestBody,
            headers,
            LoginResponse.class
        );
    }
    
    @PostMapping("/api/auth/refresh")
    @Operation(summary = "Refresh access token via gateway",
               description = "Exchange a valid refresh token for a new access token. Protected by rate limiting.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "429", description = "Too many requests - rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Authentication service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<LoginResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest requestBody,
            HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyAuthRequest(
            "/refresh",
            HttpMethod.POST,
            requestBody,
            headers,
            LoginResponse.class
        );
    }
    
    @PostMapping("/api/auth/logout")
    @Operation(summary = "User logout via gateway",
               description = "Logout the current user. Client should discard stored tokens after successful logout.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Logout successful",
                    content = @Content(schema = @Schema(implementation = LogoutResponse.class))),
        @ApiResponse(responseCode = "503", description = "Authentication service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<LogoutResponse> logout(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyAuthRequest(
            "/logout",
            HttpMethod.POST,
            null,
            headers,
            LogoutResponse.class
        );
    }
    
    @GetMapping("/api/auth/health")
    @Operation(summary = "Authentication service health check via gateway",
               description = "Check the health status of the authentication service")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Authentication service is healthy",
                    content = @Content(schema = @Schema(implementation = HealthResponse.class))),
        @ApiResponse(responseCode = "503", description = "Authentication service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<HealthResponse> authHealth(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyAuthRequest(
            "/health",
            HttpMethod.GET,
            null,
            headers,
            HealthResponse.class
        );
    }
    
    // Resilience API Gateway Routes
    
    @GetMapping("/api/resilience/rate-limiters")
    @Operation(summary = "Get rate limiter metrics via gateway")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rate limiter metrics retrieved",
                    content = @Content(schema = @Schema(implementation = RateLimiterMetricsResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RateLimiterMetricsResponse> getRateLimiters(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest(
            "/resilience/rate-limiters", 
            HttpMethod.GET, 
            null, 
            headers,
            RateLimiterMetricsResponse.class
        );
    }
    
    @GetMapping("/api/resilience/circuit-breaker")
    @Operation(summary = "Get circuit breaker metrics via gateway")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Circuit breaker metrics retrieved",
                    content = @Content(schema = @Schema(implementation = CircuitBreakerMetricsResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CircuitBreakerMetricsResponse> getCircuitBreaker(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest(
            "/resilience/circuit-breaker", 
            HttpMethod.GET, 
            null, 
            headers,
            CircuitBreakerMetricsResponse.class
        );
    }
    
    @GetMapping("/api/resilience/retry")
    @Operation(summary = "Get retry metrics via gateway")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Retry metrics retrieved",
                    content = @Content(schema = @Schema(implementation = RetryMetricsResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RetryMetricsResponse> getRetry(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest(
            "/resilience/retry", 
            HttpMethod.GET, 
            null, 
            headers,
            RetryMetricsResponse.class
        );
    }
    
    @GetMapping("/api/resilience/metrics")
    @Operation(summary = "Get all resilience metrics via gateway")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "All resilience metrics retrieved",
                    content = @Content(schema = @Schema(implementation = AllResilienceMetricsResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AllResilienceMetricsResponse> getAllMetrics(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest(
            "/resilience/metrics", 
            HttpMethod.GET, 
            null, 
            headers,
            AllResilienceMetricsResponse.class
        );
    }
    
    @GetMapping("/api/resilience/health")
    @Operation(summary = "Get resilience health status via gateway")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Resilience health status retrieved",
                    content = @Content(schema = @Schema(implementation = ResilienceHealthResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ResilienceHealthResponse> getHealth(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest(
            "/resilience/health", 
            HttpMethod.GET, 
            null, 
            headers,
            ResilienceHealthResponse.class
        );
    }
    
    // Documentation Gateway Routes
    
    @GetMapping("/swagger-ui.html")
    @Operation(summary = "Access Swagger UI via gateway")
    public ResponseEntity<String> getSwaggerUI(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyDocsRequest("/swagger-ui.html", headers);
    }
    
    @GetMapping("/api-docs/{path}")
    @Operation(summary = "Access API docs via gateway")
    public ResponseEntity<String> getApiDocs(@PathVariable String path, HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyDocsRequest("/api-docs/" + path, headers);
    }
    
    // Gateway Status and Health
    
    @GetMapping("/health")
    @Operation(summary = "API Gateway health check")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Gateway is healthy",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GatewayHealthResponse.class)))
    })
    public ResponseEntity<GatewayHealthResponse> gatewayHealth() {
        GatewayHealthResponse response = new GatewayHealthResponse(
            "UP",
            "operational",
            System.currentTimeMillis(),
            gatewayVersion
        );
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/info")
    @Operation(summary = "API Gateway information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Gateway information retrieved",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GatewayInfoResponse.class)))
    })
    public ResponseEntity<GatewayInfoResponse> gatewayInfo() {
        GatewayInfoResponse response = new GatewayInfoResponse(
            gatewayName,
            gatewayVersion,
            "Centralized API Gateway for routing requests with resilience patterns",
            Map.of(
                "rate-limiting", true,
                "circuit-breaker", true,
                "retry", true,
                "security", true,
                "monitoring", true
            )
        );
        return ResponseEntity.ok(response);
    }
    
    /**
     * Extract HTTP headers from servlet request
     */
    private HttpHeaders extractHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        
        // Copy all headers from the original request
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.add(headerName, headerValue);
        }
        
        // Add client IP information
        String clientIp = getClientIpAddress(request);
        headers.add("X-Client-IP", clientIp);
        headers.add("X-Forwarded-For", clientIp);
        
        return headers;
    }
    
    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
