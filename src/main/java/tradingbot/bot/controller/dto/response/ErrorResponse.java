package tradingbot.bot.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standard error response for API operations")
public class ErrorResponse {
    
    @Schema(description = "Error code", example = "INVALID_PARAMETERS")
    @JsonProperty("errorCode")
    private String errorCode;
    
    @Schema(description = "Error message", example = "Leverage must be between 1 and 125")
    @JsonProperty("message")
    private String message;
    
    @Schema(description = "Error details", example = "Field 'leverage' with value '150' exceeds maximum allowed value")
    @JsonProperty("details")
    private String details;
    
    @Schema(description = "Error timestamp", example = "1696070400000")
    @JsonProperty("timestamp")
    private long timestamp;
    
    public ErrorResponse() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public ErrorResponse(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    public ErrorResponse(String errorCode, String message, String details) {
        this.errorCode = errorCode;
        this.message = message;
        this.details = details;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getDetails() {
        return details;
    }
    
    public void setDetails(String details) {
        this.details = details;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}