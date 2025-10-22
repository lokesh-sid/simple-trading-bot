package tradingbot.gateway.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import tradingbot.bot.controller.dto.response.ErrorResponse;

/**
 * Gateway service for proxying requests to backend services with resilience patterns.
 * Provides type-safe request proxying, structured error handling, and header propagation.
 */
@Service
public class GatewayService {
    
    private final RestTemplate restTemplate;
    
    @Value("${gateway.backend.base-url:http://localhost:8080}")
    private String backendBaseUrl;
    
    private static final String GATEWAY_REQUEST_HEADER = "X-Gateway-Request";
    private static final String GATEWAY_TIMESTAMP_HEADER = "X-Gateway-Timestamp";
    private static final String GATEWAY_PROXIED_HEADER = "X-Gateway-Proxied";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    
    public GatewayService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * Generic type-safe proxy method that preserves response types and headers
     */
    @RateLimiter(name = "gateway-trading")
    @CircuitBreaker(name = "gateway-trading", fallbackMethod = "fallbackProxyRequest")
    @Retry(name = "gateway-trading")
    public <T> ResponseEntity<T> proxyRequest(
            String endpoint, 
            HttpMethod method, 
            Object requestBody, 
            HttpHeaders requestHeaders,
            ParameterizedTypeReference<T> responseType) {
        
        try {
            String url = backendBaseUrl + endpoint;
            
            // Enhance headers with gateway metadata
            HttpHeaders enhancedHeaders = enhanceHeaders(requestHeaders);
            
            // Create HTTP entity with body and headers
            HttpEntity<?> requestEntity = new HttpEntity<>(requestBody, enhancedHeaders);
            
            // Execute request with proper type handling
            ResponseEntity<T> response = restTemplate.exchange(
                url, 
                method, 
                requestEntity, 
                responseType
            );
            
            // Preserve response headers and add gateway metadata
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.putAll(response.getHeaders());
            responseHeaders.add(GATEWAY_PROXIED_HEADER, "true");
            
            return ResponseEntity
                .status(response.getStatusCode())
                .headers(responseHeaders)
                .body(response.getBody());
                
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // Backend returned an error - preserve it
            return handleBackendError(e);
        } catch (Exception e) {
            // Gateway error - return structured error response
            return handleGatewayError(e);
        }
    }
    
    /**
     * Convenience method for trading bot requests with type safety
     */
    @RateLimiter(name = "gateway-trading")
    @CircuitBreaker(name = "gateway-trading", fallbackMethod = "fallbackTradingBot")
    @Retry(name = "gateway-trading")
    public <T> ResponseEntity<T> proxyTradingBotRequest(
            String endpoint, 
            HttpMethod method, 
            Object body, 
            HttpHeaders headers,
            Class<T> responseType) {
        
        String url = "/api/bots" + endpoint;
        return proxyRequest(
            url, 
            method, 
            body, 
            headers, 
            ParameterizedTypeReference.forType(responseType)
        );
    }
    
    /**
     * Convenience method for trading bot requests with ParameterizedTypeReference
     */
    @RateLimiter(name = "gateway-trading")
    @CircuitBreaker(name = "gateway-trading", fallbackMethod = "fallbackTradingBotParameterized")
    @Retry(name = "gateway-trading")
    public <T> ResponseEntity<T> proxyTradingBotRequest(
            String endpoint, 
            HttpMethod method, 
            Object body, 
            HttpHeaders headers,
            ParameterizedTypeReference<T> responseType) {
        
        String url = "/api/bots" + endpoint;
        return proxyRequest(
            url, 
            method, 
            body, 
            headers, 
            responseType
        );
    }
    
    /**
     * Convenience method for resilience monitoring requests
     */
    @RateLimiter(name = "gateway-resilience")
    @CircuitBreaker(name = "gateway-resilience", fallbackMethod = "fallbackResilience")
    @Retry(name = "gateway-resilience")
    public ResponseEntity<Map<String, Object>> proxyResilienceRequest(
            String endpoint, 
            HttpHeaders headers) {
        
        String url = "/api/resilience" + endpoint;
        return proxyRequest(
            url, 
            HttpMethod.GET, 
            null, 
            headers, 
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
    }
    
    /**
     * Convenience method for documentation requests
     */
    @RateLimiter(name = "gateway-docs")
    @CircuitBreaker(name = "gateway-docs", fallbackMethod = "fallbackDocs")
    public ResponseEntity<String> proxyDocsRequest(String endpoint, HttpHeaders headers) {
        String url = endpoint;
        return proxyRequest(
            url, 
            HttpMethod.GET, 
            null, 
            headers, 
            new ParameterizedTypeReference<String>() {}
        );
    }
    
    /**
     * Convenience method for authentication requests with stricter rate limiting
     * Uses dedicated resilience configuration optimized for auth security
     */
    @RateLimiter(name = "gateway-auth")  // Stricter rate limiting for auth
    @CircuitBreaker(name = "gateway-auth", fallbackMethod = "fallbackAuth")
    @Retry(name = "gateway-auth")
    public <T> ResponseEntity<T> proxyAuthRequest(
            String endpoint,
            HttpMethod method,
            Object body,
            HttpHeaders headers,
            Class<T> responseType) {
        
        String url = "/api/auth" + endpoint;
        return proxyRequest(
            url,
            method,
            body,
            headers,
            ParameterizedTypeReference.forType(responseType)
        );
    }
    
    /**
     * Enhance headers with gateway metadata
     */
    private HttpHeaders enhanceHeaders(HttpHeaders original) {
        HttpHeaders enhanced = new HttpHeaders();
        if (original != null) {
            enhanced.putAll(original);
        }
        enhanced.add(GATEWAY_REQUEST_HEADER, "true");
        enhanced.add(GATEWAY_TIMESTAMP_HEADER, String.valueOf(System.currentTimeMillis()));
        if (!enhanced.containsKey(CONTENT_TYPE)) {
            enhanced.add(CONTENT_TYPE, APPLICATION_JSON);
        }
        return enhanced;
    }
    
    /**
     * Handle backend HTTP errors (4xx, 5xx) with structured error response
     */
    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> handleBackendError(RuntimeException e) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorMessage = e.getMessage();
        
        if (e instanceof HttpClientErrorException httpClientErrorException) {
            status = HttpStatus.valueOf(httpClientErrorException.getStatusCode().value());
        } else if (e instanceof HttpServerErrorException httpServerErrorException) {
            status = HttpStatus.valueOf(httpServerErrorException.getStatusCode().value());
        }
        
        // Create structured error response
        ErrorResponse error = new ErrorResponse(
            "BACKEND_ERROR",
            errorMessage != null ? errorMessage : "Backend service error",
            status.toString()
        );
        
        return (ResponseEntity<T>) ResponseEntity
            .status(status)
            .body(error);
    }
    
    /**
     * Handle gateway-level errors with structured error response
     */
    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> handleGatewayError(Exception e) {
        ErrorResponse error = new ErrorResponse(
            "GATEWAY_ERROR",
            "Gateway error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"),
            HttpStatus.INTERNAL_SERVER_ERROR.toString()
        );
        
        return (ResponseEntity<T>) ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error);
    }
    
    // Fallback methods
    
    /**
     * Generic fallback for all proxy requests
     */
    @SuppressWarnings("unchecked")
    public <T> ResponseEntity<T> fallbackProxyRequest(
            String endpoint, 
            HttpMethod method, 
            Object requestBody, 
            HttpHeaders requestHeaders,
            ParameterizedTypeReference<T> responseType,
            Exception ex) {
        
        ErrorResponse error = new ErrorResponse(
            "SERVICE_UNAVAILABLE",
            "Service is currently unavailable. Please try again later.",
            ex.getMessage()
        );
        
        return (ResponseEntity<T>) ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(error);
    }
    
    /**
     * Fallback for trading bot requests
     */
    public <T> ResponseEntity<T> fallbackTradingBot(
            String endpoint, 
            HttpMethod method, 
            Object body, 
            HttpHeaders headers,
            Class<T> responseType,
            Exception ex) {
        
        return fallbackProxyRequest(
            endpoint, 
            method, 
            body, 
            headers, 
            ParameterizedTypeReference.forType(responseType),
            ex
        );
    }
    
    /**
     * Fallback for trading bot requests with ParameterizedTypeReference
     */
    public <T> ResponseEntity<T> fallbackTradingBotParameterized(
            String endpoint, 
            HttpMethod method, 
            Object body, 
            HttpHeaders headers,
            ParameterizedTypeReference<T> responseType,
            Exception ex) {
        
        return fallbackProxyRequest(
            endpoint, 
            method, 
            body, 
            headers, 
            responseType,
            ex
        );
    }
    
    /**
     * Fallback for resilience monitoring requests
     */
    public ResponseEntity<Map<String, Object>> fallbackResilience(
            String endpoint, 
            HttpHeaders headers, 
            Exception ex) {
        
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Resilience monitoring service is currently unavailable",
                "message", ex.getMessage() != null ? ex.getMessage() : "Unknown error",
                "timestamp", System.currentTimeMillis()
            ));
    }
    
    /**
     * Fallback for documentation requests
     */
    public ResponseEntity<String> fallbackDocs(
            String endpoint, 
            HttpHeaders headers, 
            Exception ex) {
        
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body("Documentation service is currently unavailable. Error: " + 
                  (ex.getMessage() != null ? ex.getMessage() : "Unknown error"));
    }
    
    /**
     * Fallback for authentication requests
     */
    @SuppressWarnings("unchecked")
    public <T> ResponseEntity<T> fallbackAuth(
            String endpoint,
            HttpMethod method,
            Object body,
            HttpHeaders headers,
            Class<T> responseType,
            Exception ex) {
        
        ErrorResponse error = new ErrorResponse(
            "AUTH_SERVICE_UNAVAILABLE",
            "Authentication service is temporarily unavailable. Please try again later.",
            ex.getMessage() != null ? ex.getMessage() : "Service unavailable"
        );
        
        return (ResponseEntity<T>) ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(error);
    }
}