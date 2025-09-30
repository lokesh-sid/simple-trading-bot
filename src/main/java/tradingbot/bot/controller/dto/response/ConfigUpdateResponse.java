package tradingbot.bot.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response for configuration update operation")
public class ConfigUpdateResponse {
    
    @Schema(description = "Success message", example = "Configuration updated successfully")
    @JsonProperty("message")
    private String message;
    
    @Schema(description = "Updated configuration symbol", example = "BTCUSDT")
    @JsonProperty("symbol")
    private String symbol;
    
    @Schema(description = "Updated leverage", example = "5.0")
    @JsonProperty("leverage")
    private Double leverage;
    
    @Schema(description = "Updated trailing stop percentage", example = "2.0")
    @JsonProperty("trailingStopPercent")
    private Double trailingStopPercent;
    
    @Schema(description = "Update timestamp", example = "1696070400000")
    @JsonProperty("updatedAt")
    private long updatedAt;
    
    public ConfigUpdateResponse() {
        this.updatedAt = System.currentTimeMillis();
    }
    
    public ConfigUpdateResponse(String message, String symbol, Double leverage, Double trailingStopPercent) {
        this.message = message;
        this.symbol = symbol;
        this.leverage = leverage;
        this.trailingStopPercent = trailingStopPercent;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public Double getLeverage() {
        return leverage;
    }
    
    public void setLeverage(Double leverage) {
        this.leverage = leverage;
    }
    
    public Double getTrailingStopPercent() {
        return trailingStopPercent;
    }
    
    public void setTrailingStopPercent(Double trailingStopPercent) {
        this.trailingStopPercent = trailingStopPercent;
    }
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}