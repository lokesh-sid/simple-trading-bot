package tradingbot.bot.controller.exception;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import jakarta.validation.ConstraintViolationException;
import tradingbot.bot.controller.dto.response.ErrorResponse;

/**
 * Global exception handler for all REST controllers.
 * Provides consistent error responses following RFC 7807 Problem Details standard.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_TYPE_BASE = "https://api.tradingbot.com/errors/";
    
    /**
     * Handle validation errors from @Valid annotation on request bodies.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        
        Map<String, List<String>> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            fieldErrors.computeIfAbsent(error.getField(), k -> new java.util.ArrayList<>())
                .add(error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value")
        );
        
        String requestPath = getRequestPath(request);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .type(ERROR_TYPE_BASE + "validation-failed")
            .title("Validation Failed")
            .httpStatus(HttpStatus.BAD_REQUEST.value())
            .detail("Request validation failed. Please check field errors.")
            .instance(requestPath)
            .timestamp(System.currentTimeMillis())
            .fieldErrors(fieldErrors)
            .build();
        
        if (log.isWarnEnabled()) {
            log.warn("Validation failed for request {}: {}", requestPath, fieldErrors);
        }
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    /**
     * Handle constraint violations from @Validated on method parameters.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            WebRequest request) {
        
        Map<String, List<String>> violations = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> 
            violations.computeIfAbsent(violation.getPropertyPath().toString(), k -> new java.util.ArrayList<>())
                .add(violation.getMessage())
        );
        
        String requestPath = getRequestPath(request);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .type(ERROR_TYPE_BASE + "constraint-violation")
            .httpStatus(HttpStatus.BAD_REQUEST.value())
            .title("Constraint Violation")
            .detail("Request parameter constraints violated.")
            .instance(requestPath)
            .timestamp(System.currentTimeMillis())
            .fieldErrors(violations)
            .build();
        
        if (log.isWarnEnabled()) {
            log.warn("Constraint violation for request {}: {}", requestPath, violations);
        }
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    /**
     * Handle BotNotFoundException - 404 Not Found
     */
    @ExceptionHandler(BotNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBotNotFound(
            BotNotFoundException ex,
            WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .type(ERROR_TYPE_BASE + "bot-not-found")
            .title("Bot Not Found")
            .httpStatus(HttpStatus.NOT_FOUND.value())
            .detail(ex.getMessage())
            .instance(getRequestPath(request))
            .timestamp(System.currentTimeMillis())
            .build();
        
        log.warn("Bot not found: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    /**
     * Handle BotAlreadyRunningException - 409 Conflict
     */
    @ExceptionHandler(BotAlreadyRunningException.class)
    public ResponseEntity<ErrorResponse> handleBotAlreadyRunning(
            BotAlreadyRunningException ex,
            WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .type(ERROR_TYPE_BASE + "bot-already-running")
            .title("Bot Already Running")
            .httpStatus(HttpStatus.CONFLICT.value())
            .detail(ex.getMessage())
            .instance(getRequestPath(request))
            .timestamp(System.currentTimeMillis())
            .build();
        
        log.warn("Bot already running: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }
    
    /**
     * Handle ConflictException - 409 Conflict
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException ex,
            WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .type(ERROR_TYPE_BASE + "conflict")
            .title("Resource Conflict")
            .httpStatus(HttpStatus.CONFLICT.value())
            .detail(ex.getMessage())
            .instance(getRequestPath(request))
            .timestamp(System.currentTimeMillis())
            .build();
        
        log.warn("Resource conflict: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }
    
    /**
     * Handle InvalidBotConfigurationException - 422 Unprocessable Entity
     */
    @ExceptionHandler(InvalidBotConfigurationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBotConfiguration(
            InvalidBotConfigurationException ex,
            WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .type(ERROR_TYPE_BASE + "invalid-bot-configuration")
            .title("Invalid Bot Configuration")
            .httpStatus(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .detail(ex.getMessage())
            .instance(getRequestPath(request))
            .timestamp(System.currentTimeMillis())
            .build();
        
        log.warn("Invalid bot configuration: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }
    
    /**
     * Handle IllegalArgumentException - 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .type(ERROR_TYPE_BASE + "invalid-argument")
            .title("Invalid Argument")
            .httpStatus(HttpStatus.BAD_REQUEST.value())
            .detail(ex.getMessage())
            .instance(getRequestPath(request))
            .timestamp(System.currentTimeMillis())
            .build();
        
        log.warn("Invalid argument: {}", ex.getMessage());
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    /**
     * Handle all other exceptions - 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            WebRequest request) {
        
        String requestPath = getRequestPath(request);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .type(ERROR_TYPE_BASE + "internal-server-error")
            .title("Internal Server Error")
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .detail("An unexpected error occurred. Please try again later.")
            .instance(requestPath)
            .timestamp(System.currentTimeMillis())
            .build();
        
        if (log.isErrorEnabled()) {
            log.error("Unexpected error occurred for request {}", requestPath, ex);
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    private String getRequestPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
