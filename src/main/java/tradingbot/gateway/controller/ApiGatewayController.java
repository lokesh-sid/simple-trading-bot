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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
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
import tradingbot.bot.controller.dto.response.BotStartResponse;
import tradingbot.bot.controller.dto.response.BotStatusResponse;
import tradingbot.bot.controller.dto.response.ErrorResponse;
import tradingbot.bot.controller.dto.response.LeverageUpdateResponse;
import tradingbot.bot.controller.dto.response.SentimentUpdateResponse;
import tradingbot.gateway.service.GatewayService;

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
                    content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, String>> createBot(@RequestHeader HttpHeaders headers) {
        return gatewayService.proxyTradingBotRequest(
            "", 
            HttpMethod.POST, 
            null, 
            headers,
            new org.springframework.core.ParameterizedTypeReference<Map<String, String>>() {}
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
            @Valid @RequestBody BotStartRequest request,
            @RequestHeader HttpHeaders headers) {
        
        return gatewayService.proxyTradingBotRequest(
            "/" + botId + "/start", 
            HttpMethod.POST, 
            request, 
            headers,
            BotStartResponse.class
        );
    }
    
    @PutMapping("/api/bots/{botId}/stop")
    @Operation(summary = "Stop trading bot via gateway",
               description = "Stops the specified trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot stopped successfully",
                    content = @Content(schema = @Schema(implementation = BotStatusResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotStatusResponse> stopBot(
            @PathVariable String botId,
            @RequestHeader HttpHeaders headers) {
        return gatewayService.proxyTradingBotRequest(
            "/" + botId + "/stop", 
            HttpMethod.PUT, 
            null, 
            headers,
            BotStatusResponse.class
        );
    }
    
    @GetMapping("/api/bots/{botId}/status")
    @Operation(summary = "Get trading bot status via gateway",
               description = "Returns the current status of the specified trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BotStatusResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotStatusResponse> getStatus(
            @PathVariable String botId,
            @RequestHeader HttpHeaders headers) {
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
            @Valid @RequestBody LeverageUpdateRequest request,
            @RequestHeader HttpHeaders headers) {
        
        return gatewayService.proxyTradingBotRequest(
            "/" + botId + "/leverage", 
            HttpMethod.POST, 
            request, 
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
            @Valid @RequestBody SentimentUpdateRequest request,
            @RequestHeader HttpHeaders headers) {
        
        return gatewayService.proxyTradingBotRequest(
            "/" + botId + "/sentiment", 
            HttpMethod.POST, 
            request, 
            headers,
            SentimentUpdateResponse.class
        );
    }
    
    @GetMapping("/api/bots")
    @Operation(summary = "List all bot IDs via gateway",
               description = "Returns a list of all trading bot identifiers")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot list retrieved successfully"),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, Object>> listBots(@RequestHeader HttpHeaders headers) {
        return gatewayService.proxyTradingBotRequest(
            "", 
            HttpMethod.GET, 
            null, 
            headers,
            new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
        );
    }
    
    @DeleteMapping("/api/bots/{botId}")
    @Operation(summary = "Delete a bot via gateway",
               description = "Stops and removes the specified trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, String>> deleteBot(
            @PathVariable String botId,
            @RequestHeader HttpHeaders headers) {
        return gatewayService.proxyTradingBotRequest(
            "/" + botId, 
            HttpMethod.DELETE, 
            null, 
            headers,
            new org.springframework.core.ParameterizedTypeReference<Map<String, String>>() {}
        );
    }
    
    // Resilience API Gateway Routes
    
    @GetMapping("/api/resilience/rate-limiters")
    @Operation(summary = "Get rate limiter metrics via gateway")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rate limiter metrics retrieved"),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, Object>> getRateLimiters(@RequestHeader HttpHeaders headers) {
        return gatewayService.proxyResilienceRequest("/rate-limiters", headers);
    }
    
    @GetMapping("/api/resilience/circuit-breaker")
    @Operation(summary = "Get circuit breaker metrics via gateway")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Circuit breaker metrics retrieved"),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, Object>> getCircuitBreaker(@RequestHeader HttpHeaders headers) {
        return gatewayService.proxyResilienceRequest("/circuit-breaker", headers);
    }
    
    @GetMapping("/api/resilience/retry")
    @Operation(summary = "Get retry metrics via gateway")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Retry metrics retrieved"),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, Object>> getRetry(@RequestHeader HttpHeaders headers) {
        return gatewayService.proxyResilienceRequest("/retry", headers);
    }
    
    @GetMapping("/api/resilience/metrics")
    @Operation(summary = "Get all resilience metrics via gateway")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "All resilience metrics retrieved"),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, Object>> getAllMetrics(@RequestHeader HttpHeaders headers) {
        return gatewayService.proxyResilienceRequest("/metrics", headers);
    }
    
    @GetMapping("/api/resilience/health")
    @Operation(summary = "Get resilience health status via gateway")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Resilience health status retrieved"),
        @ApiResponse(responseCode = "503", description = "Service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, Object>> getHealth(@RequestHeader HttpHeaders headers) {
        return gatewayService.proxyResilienceRequest("/health", headers);
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
                    content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> gatewayHealth() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "gateway", "operational",
            "timestamp", System.currentTimeMillis(),
            "version", "1.0.0"
        ));
    }
    
    @GetMapping("/info")
    @Operation(summary = "API Gateway information")
    public ResponseEntity<Map<String, Object>> gatewayInfo() {
        return ResponseEntity.ok(Map.of(
            "name", "Simple Trading Bot API Gateway",
            "version", "1.0.0",
            "description", "Centralized API Gateway for routing requests with resilience patterns",
            "features", Map.of(
                "rate-limiting", true,
                "circuit-breaker", true,
                "retry", true,
                "security", true,
                "monitoring", true
            )
        ));
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
