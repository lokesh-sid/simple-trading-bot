package tradingbot.bot.controller.advice;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import tradingbot.bot.controller.dto.response.ErrorResponse;
import tradingbot.bot.controller.exception.TradingBotException;
import tradingbot.bot.exception.ExchangeException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TradingBotException.class)
    public ResponseEntity<ErrorResponse> handleTradingBotException(TradingBotException ex, WebRequest request) {
        logError(ex);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setSuccess(false);
        errorResponse.setTitle("Trading Bot Error");
        errorResponse.setDetail(ex.getMessage());
        errorResponse.setTimestamp(System.currentTimeMillis());
        errorResponse.setRequestId(getRequestId());
        
        HttpStatus status = ex.getHttpStatus() != null ? ex.getHttpStatus() : HttpStatus.BAD_REQUEST;
        errorResponse.setHttpStatus(status.value());
        
        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler(ExchangeException.class)
    public ResponseEntity<ErrorResponse> handleExchangeException(ExchangeException ex, WebRequest request) {
        logError(ex);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setSuccess(false);
        errorResponse.setTitle("Exchange Error");
        errorResponse.setDetail(ex.getMessage());
        errorResponse.setTimestamp(System.currentTimeMillis());
        errorResponse.setRequestId(getRequestId());
        errorResponse.setHttpStatus(HttpStatus.BAD_GATEWAY.value());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        logError(ex);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setSuccess(false);
        errorResponse.setTitle("Internal Server Error");
        errorResponse.setDetail(ex.getMessage()); // In prod, maybe hide this
        errorResponse.setTimestamp(System.currentTimeMillis());
        errorResponse.setRequestId(getRequestId());
        errorResponse.setHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void logError(Exception ex) {
        log.error("Exception occurred: {}", ex.getMessage(), ex);
    }

    private String getRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : UUID.randomUUID().toString();
    }
}
