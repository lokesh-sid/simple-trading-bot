package tradingbot.bot.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for monitoring rate limiting and resilience metrics
 */
@RestController
@RequestMapping("/api/resilience")
@Tag(name = "Resilience Controller", description = "API for monitoring rate limiting, circuit breaker, and retry metrics")
public class ResilienceController {

    private final RateLimiter binanceTradingRateLimiter;
    private final RateLimiter binanceMarketRateLimiter;
    private final RateLimiter binanceAccountRateLimiter;
    private final CircuitBreaker binanceApiCircuitBreaker;
    private final Retry binanceApiRetry;

    public ResilienceController(RateLimiter binanceTradingRateLimiter,
                               RateLimiter binanceMarketRateLimiter,
                               RateLimiter binanceAccountRateLimiter,
                               CircuitBreaker binanceApiCircuitBreaker,
                               Retry binanceApiRetry) {
        this.binanceTradingRateLimiter = binanceTradingRateLimiter;
        this.binanceMarketRateLimiter = binanceMarketRateLimiter;
        this.binanceAccountRateLimiter = binanceAccountRateLimiter;
        this.binanceApiCircuitBreaker = binanceApiCircuitBreaker;
        this.binanceApiRetry = binanceApiRetry;
    }

    /**
     * Get current rate limiter metrics
     */
    @GetMapping("/rate-limiters")
    @Operation(summary = "Get rate limiter metrics", 
               description = "Returns current metrics for all rate limiters (trading, market, account)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rate limiter metrics retrieved successfully",
                    content = @Content(mediaType = "application/json", 
                                     schema = @Schema(type = "object", description = "Rate limiter metrics for each category"))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Map<String, Object> getRateLimiterMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Trading rate limiter metrics
        Map<String, Object> trading = new HashMap<>();
        trading.put("availablePermissions", binanceTradingRateLimiter.getMetrics().getAvailablePermissions());
        trading.put("numberOfWaitingThreads", binanceTradingRateLimiter.getMetrics().getNumberOfWaitingThreads());
        metrics.put("trading", trading);
        
        // Market rate limiter metrics
        Map<String, Object> market = new HashMap<>();
        market.put("availablePermissions", binanceMarketRateLimiter.getMetrics().getAvailablePermissions());
        market.put("numberOfWaitingThreads", binanceMarketRateLimiter.getMetrics().getNumberOfWaitingThreads());
        metrics.put("market", market);
        
        // Account rate limiter metrics
        Map<String, Object> account = new HashMap<>();
        account.put("availablePermissions", binanceAccountRateLimiter.getMetrics().getAvailablePermissions());
        account.put("numberOfWaitingThreads", binanceAccountRateLimiter.getMetrics().getNumberOfWaitingThreads());
        metrics.put("account", account);
        
        return metrics;
    }

    /**
     * Get circuit breaker metrics
     */
    @GetMapping("/circuit-breaker")
    public Map<String, Object> getCircuitBreakerMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        CircuitBreaker.Metrics cbMetrics = binanceApiCircuitBreaker.getMetrics();
        metrics.put("state", binanceApiCircuitBreaker.getState().toString());
        metrics.put("failureRate", cbMetrics.getFailureRate());
        metrics.put("numberOfBufferedCalls", cbMetrics.getNumberOfBufferedCalls());
        metrics.put("numberOfFailedCalls", cbMetrics.getNumberOfFailedCalls());
        metrics.put("numberOfSuccessfulCalls", cbMetrics.getNumberOfSuccessfulCalls());
        metrics.put("numberOfNotPermittedCalls", cbMetrics.getNumberOfNotPermittedCalls());
        
        return metrics;
    }

    /**
     * Get retry metrics
     */
    @GetMapping("/retry")
    public Map<String, Object> getRetryMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        Retry.Metrics retryMetrics = binanceApiRetry.getMetrics();
        metrics.put("numberOfSuccessfulCallsWithoutRetryAttempt", 
                   retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt());
        metrics.put("numberOfSuccessfulCallsWithRetryAttempt", 
                   retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt());
        metrics.put("numberOfFailedCallsWithRetryAttempt", 
                   retryMetrics.getNumberOfFailedCallsWithRetryAttempt());
        metrics.put("numberOfFailedCallsWithoutRetryAttempt", 
                   retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt());
        
        return metrics;
    }

    /**
     * Get all resilience metrics in one call
     */
    @GetMapping("/metrics")
    public Map<String, Object> getAllMetrics() {
        Map<String, Object> allMetrics = new HashMap<>();
        allMetrics.put("rateLimiters", getRateLimiterMetrics());
        allMetrics.put("circuitBreaker", getCircuitBreakerMetrics());
        allMetrics.put("retry", getRetryMetrics());
        return allMetrics;
    }

    /**
     * Health check endpoint for resilience components
     */
    @GetMapping("/health")
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        // Check if circuit breaker is operational
        boolean circuitBreakerHealthy = binanceApiCircuitBreaker.getState() != CircuitBreaker.State.OPEN;
        health.put("circuitBreaker", Map.of(
            "healthy", circuitBreakerHealthy,
            "state", binanceApiCircuitBreaker.getState().toString()
        ));
        
        // Check if rate limiters have available capacity
        boolean rateLimitersHealthy = 
            binanceTradingRateLimiter.getMetrics().getAvailablePermissions() > 0 ||
            binanceMarketRateLimiter.getMetrics().getAvailablePermissions() > 0 ||
            binanceAccountRateLimiter.getMetrics().getAvailablePermissions() > 0;
        
        health.put("rateLimiters", Map.of("healthy", rateLimitersHealthy));
        
        // Overall health
        boolean overallHealthy = circuitBreakerHealthy && rateLimitersHealthy;
        health.put("overall", Map.of("healthy", overallHealthy));
        
        return health;
    }
}
