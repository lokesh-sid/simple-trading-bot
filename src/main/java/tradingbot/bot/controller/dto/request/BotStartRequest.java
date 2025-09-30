package tradingbot.bot.controller.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import tradingbot.bot.TradeDirection;

@Schema(description = "Request to start the trading bot")
public class BotStartRequest {
    
    @NotNull(message = "Trading direction is required")
    @Schema(description = "Trading direction (LONG or SHORT)", example = "LONG")
    @JsonProperty("direction")
    private TradeDirection direction;
    
    @Schema(description = "Enable paper trading mode", example = "false", defaultValue = "false")
    @JsonProperty("paper")
    private boolean paper = false;
    
    public BotStartRequest() {}
    
    public BotStartRequest(TradeDirection direction, boolean paper) {
        this.direction = direction;
        this.paper = paper;
    }
    
    public TradeDirection getDirection() {
        return direction;
    }
    
    public void setDirection(TradeDirection direction) {
        this.direction = direction;
    }
    
    public boolean isPaper() {
        return paper;
    }
    
    public void setPaper(boolean paper) {
        this.paper = paper;
    }
}