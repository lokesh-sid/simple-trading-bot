package tradingbot.gateway;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * API Gateway Controller for routing and proxying requests to backend services
 * 
 * Provides a centralized entry point with:
 * - Request routing and proxying
 * - Rate limiting
 * - Circuit breaker patterns
 * - Request/response transformation
 * - Security enforcement
 */
@RestController
@RequestMapping("/gateway")
@Tag(name = "API Gateway", description = "Centralized API Gateway for routing requests to backend services")
public class ApiGatewayController {
    
    private final GatewayService gatewayService;
    
    public ApiGatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }
    
    // Trading Bot API Gateway Routes
    
    @PostMapping("/api/trading-bot/start")
    @Operation(summary = "Start trading bot via gateway", 
               description = "Routes request to start the trading bot through the API gateway")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Request proxied successfully"),
        @ApiResponse(responseCode = "503", description = "Service unavailable")
    })
    public ResponseEntity<String> startBot(
            @Parameter(description = "Trading direction", example = "LONG") @RequestParam String direction,
            @Parameter(description = "Paper trading mode", example = "false") @RequestParam(defaultValue = "false") boolean paper,
            HttpServletRequest request) {
        
        String endpoint = "/start?direction=" + direction + "&paper=" + paper;
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest(endpoint, "POST", null, headers);
    }
    
    @PostMapping("/api/trading-bot/stop")
    @Operation(summary = "Stop trading bot via gateway")
    public ResponseEntity<String> stopBot(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest("/stop", "POST", null, headers);
    }
    
    @GetMapping("/api/trading-bot/status")
    @Operation(summary = "Get trading bot status via gateway")
    public ResponseEntity<String> getStatus(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest("/status", "GET", null, headers);
    }
    
    @PostMapping("/api/trading-bot/configure")
    @Operation(summary = "Configure trading bot via gateway")
    public ResponseEntity<String> configureBot(@RequestBody String config, HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest("/configure", "POST", config, headers);
    }
    
    @PostMapping("/api/trading-bot/leverage")
    @Operation(summary = "Set leverage via gateway")
    public ResponseEntity<String> setLeverage(
            @Parameter(description = "Leverage value") @RequestParam int leverage,
            HttpServletRequest request) {
        
        String endpoint = "/leverage?leverage=" + leverage;
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest(endpoint, "POST", null, headers);
    }
    
    @PostMapping("/api/trading-bot/sentiment")
    @Operation(summary = "Set sentiment analysis via gateway")
    public ResponseEntity<String> setSentiment(
            @Parameter(description = "Enable sentiment analysis") @RequestParam boolean enable,
            HttpServletRequest request) {
        
        String endpoint = "/sentiment?enable=" + enable;
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyTradingBotRequest(endpoint, "POST", null, headers);
    }
    
    // Resilience API Gateway Routes
    
    @GetMapping("/api/resilience/rate-limiters")
    @Operation(summary = "Get rate limiter metrics via gateway")
    public ResponseEntity<Map<String, Object>> getRateLimiters(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyResilienceRequest("/rate-limiters", headers);
    }
    
    @GetMapping("/api/resilience/circuit-breaker")
    @Operation(summary = "Get circuit breaker metrics via gateway")
    public ResponseEntity<Map<String, Object>> getCircuitBreaker(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyResilienceRequest("/circuit-breaker", headers);
    }
    
    @GetMapping("/api/resilience/retry")
    @Operation(summary = "Get retry metrics via gateway")
    public ResponseEntity<Map<String, Object>> getRetry(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyResilienceRequest("/retry", headers);
    }
    
    @GetMapping("/api/resilience/metrics")
    @Operation(summary = "Get all resilience metrics via gateway")
    public ResponseEntity<Map<String, Object>> getAllMetrics(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
        return gatewayService.proxyResilienceRequest("/metrics", headers);
    }
    
    @GetMapping("/api/resilience/health")
    @Operation(summary = "Get resilience health status via gateway")
    public ResponseEntity<Map<String, Object>> getHealth(HttpServletRequest request) {
        HttpHeaders headers = extractHeaders(request);
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
