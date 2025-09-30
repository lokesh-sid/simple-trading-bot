package tradingbot.bot.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response for sentiment analysis toggle operation")
public class SentimentUpdateResponse {
    
    @Schema(description = "Success message", example = "Sentiment analysis enabled")
    @JsonProperty("message")
    private String message;
    
    @Schema(description = "Current sentiment analysis status", example = "true")
    @JsonProperty("sentimentEnabled")
    private boolean sentimentEnabled;
    
    @Schema(description = "Previous sentiment analysis status", example = "false")
    @JsonProperty("previousStatus")
    private boolean previousStatus;
    
    @Schema(description = "Update timestamp", example = "1696070400000")
    @JsonProperty("updatedAt")
    private long updatedAt;
    
    public SentimentUpdateResponse() {
        this.updatedAt = System.currentTimeMillis();
    }
    
    public SentimentUpdateResponse(String message, boolean sentimentEnabled, boolean previousStatus) {
        this.message = message;
        this.sentimentEnabled = sentimentEnabled;
        this.previousStatus = previousStatus;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public boolean isSentimentEnabled() {
        return sentimentEnabled;
    }
    
    public void setSentimentEnabled(boolean sentimentEnabled) {
        this.sentimentEnabled = sentimentEnabled;
    }
    
    public boolean isPreviousStatus() {
        return previousStatus;
    }
    
    public void setPreviousStatus(boolean previousStatus) {
        this.previousStatus = previousStatus;
    }
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}