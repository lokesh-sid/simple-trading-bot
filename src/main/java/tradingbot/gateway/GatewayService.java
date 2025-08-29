package tradingbot.gateway;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Gateway service for proxying requests to backend services with resilience patterns
 */
@Service
public class GatewayService {
    
    private final RestTemplate restTemplate;
    private static final String BACKEND_BASE_URL = "http://localhost:8080";
    private static final String GATEWAY_REQUEST_HEADER = "X-Gateway-Request";
    private static final String GATEWAY_TIMESTAMP_HEADER = "X-Gateway-Timestamp";
    private static final String GATEWAY_ERROR_PREFIX = "Gateway error: ";
    
    public GatewayService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @RateLimiter(name = "gateway-trading")
    @CircuitBreaker(name = "gateway-trading", fallbackMethod = "fallbackTradingBot")
    @Retry(name = "gateway-trading")
    public ResponseEntity<String> proxyTradingBotRequest(String endpoint, String method, String body, HttpHeaders headers) {
        try {
            String url = BACKEND_BASE_URL + "/api/simple-trading-bot" + endpoint;
            
            // Add gateway headers
            headers.add(GATEWAY_REQUEST_HEADER, "true");
            headers.add(GATEWAY_TIMESTAMP_HEADER, String.valueOf(System.currentTimeMillis()));
            
            switch (method.toUpperCase()) {
                case "GET":
                    return restTemplate.getForEntity(url, String.class);
                case "POST":
                    return restTemplate.postForEntity(url, body, String.class);
                default:
                    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                            .body("Method " + method + " not allowed");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(GATEWAY_ERROR_PREFIX + e.getMessage());
        }
    }
    
    @RateLimiter(name = "gateway-resilience")
    @CircuitBreaker(name = "gateway-resilience", fallbackMethod = "fallbackResilience")
    @Retry(name = "gateway-resilience")
    public ResponseEntity<Map<String, Object>> proxyResilienceRequest(String endpoint, HttpHeaders headers) {
        try {
            String url = BACKEND_BASE_URL + "/api/resilience" + endpoint;
            
            // Add gateway headers
            headers.add(GATEWAY_REQUEST_HEADER, "true");
            headers.add(GATEWAY_TIMESTAMP_HEADER, String.valueOf(System.currentTimeMillis()));
            
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> rawResponse = restTemplate.getForEntity(url, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = rawResponse.getBody();
            return ResponseEntity.status(rawResponse.getStatusCode())
                    .headers(rawResponse.getHeaders())
                    .body(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", GATEWAY_ERROR_PREFIX + e.getMessage()));
        }
    }
    
    @RateLimiter(name = "gateway-docs")
    @CircuitBreaker(name = "gateway-docs", fallbackMethod = "fallbackDocs")
    public ResponseEntity<String> proxyDocsRequest(String endpoint, HttpHeaders headers) {
        try {
            String url = BACKEND_BASE_URL + endpoint;
            
            // Add gateway headers  
            headers.add(GATEWAY_REQUEST_HEADER, "true");
            headers.add(GATEWAY_TIMESTAMP_HEADER, String.valueOf(System.currentTimeMillis()));
            
            return restTemplate.getForEntity(url, String.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(GATEWAY_ERROR_PREFIX + e.getMessage());
        }
    }
    
    // Fallback methods
    public ResponseEntity<String> fallbackTradingBot(String endpoint, String method, String body, HttpHeaders headers, Exception ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Trading bot service is currently unavailable. Please try again later.");
    }
    
    public ResponseEntity<Map<String, Object>> fallbackResilience(String endpoint, HttpHeaders headers, Exception ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error", "Resilience monitoring service is currently unavailable",
                    "timestamp", System.currentTimeMillis()
                ));
    }
    
    public ResponseEntity<String> fallbackDocs(String endpoint, HttpHeaders headers, Exception ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Documentation service is currently unavailable. Please try again later.");
    }
}
