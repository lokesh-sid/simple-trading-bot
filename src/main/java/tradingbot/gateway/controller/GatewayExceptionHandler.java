package tradingbot.gateway.controller;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.WebRequest;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import tradingbot.bot.controller.dto.response.ErrorResponse;

/**
 * Global exception handler for API Gateway Controller
 * 
 * Handles various types of exceptions that can occur during gateway operations:
 * - HTTP client errors (4xx from backend)
 * - HTTP server errors (5xx from backend)
 * - Resilience4j exceptions (circuit breaker, rate limiter)
 * - Validation errors
 * - Network/connection errors
 * 
 * Uses Spring Boot 3's ProblemDetail (RFC 7807) for standardized error responses
 */
@RestControllerAdvice(assignableTypes = ApiGatewayController.class)
public class GatewayExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GatewayExceptionHandler.class);
    private static final String TIMESTAMP_KEY = "timestamp";
    
    /**
     * Handle HTTP 4xx errors from backend services
     */
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleClientError(
            HttpClientErrorException ex, 
            WebRequest request) {
        
        logger.warn("Backend returned client error: {} - {}", ex.getStatusCode(), ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            ex.getStatusCode().toString(),
            "Backend service returned error: " + ex.getStatusText()
        );
        
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }
    
    /**
     * Handle HTTP 5xx errors from backend services
     */
    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleServerError(
            HttpServerErrorException ex, 
            WebRequest request) {
        
        logger.error("Backend returned server error: {} - {}", ex.getStatusCode(), ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            ex.getStatusCode().toString(),
            "Backend service encountered an error: " + ex.getStatusText()
        );
        
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }
    
    /**
     * Handle circuit breaker open state
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> handleCircuitBreakerOpen(
            CallNotPermittedException ex, 
            WebRequest request) {
        
        logger.warn("Circuit breaker is open: {}", ex.getMessage());
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Service is temporarily unavailable due to circuit breaker protection"
        );
        problem.setTitle("Circuit Breaker Open");
        problem.setProperty(TIMESTAMP_KEY, Instant.now());
        problem.setProperty("circuitBreaker", ex.getCausingCircuitBreakerName());
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }
    
    /**
     * Handle rate limiter exceptions
     */
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(
            RequestNotPermitted ex, 
            WebRequest request) {
        
        logger.warn("Rate limit exceeded: {}", ex.getMessage());
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too many requests. Please try again later."
        );
        problem.setTitle("Rate Limit Exceeded");
        problem.setProperty(TIMESTAMP_KEY, Instant.now());
        problem.setProperty("retryAfter", "60 seconds");
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
    }
    
    /**
     * Handle validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationError(
            MethodArgumentNotValidException ex, 
            WebRequest request) {
        
        logger.warn("Validation error: {}", ex.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage); 
        });
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed for request"
        );
        problem.setTitle("Validation Error");
        problem.setProperty(TIMESTAMP_KEY, Instant.now());
        problem.setProperty("errors", errors);
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        
        return ResponseEntity.badRequest().body(problem);
    }
    
    /**
     * Handle network/connection errors
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleConnectionError(
            ResourceAccessException ex, 
            WebRequest request) {
        
        logger.error("Connection error to backend service: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            "SERVICE_UNAVAILABLE",
            "Unable to connect to backend service. Please try again later."
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
    
    /**
     * Handle all other unexpected exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericError(
            Exception ex, 
            WebRequest request) {
        
        logger.error("Unexpected error in gateway: {}", ex.getMessage(), ex);
        
        ErrorResponse error = new ErrorResponse(
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred in the gateway"
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
