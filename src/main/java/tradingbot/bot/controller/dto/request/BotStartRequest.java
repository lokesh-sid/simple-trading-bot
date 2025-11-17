package tradingbot.bot.controller.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import tradingbot.bot.TradeDirection;

@Schema(description = "Request to start the trading bot")
public class BotStartRequest {
    
    @NotNull(message = "Trading direction is required")
    @Schema(description = "Trading direction (LONG or SHORT)", example = "LONG", required = true)
    @JsonProperty("direction")
    private TradeDirection direction;
    
    @NotNull(message = "Paper trading flag is required")
    @Schema(description = "Enable paper trading mode", example = "false", defaultValue = "false", required = true)
    @JsonProperty("paper")
    private Boolean paper = false;
    
    public BotStartRequest() {}
    
    public BotStartRequest(TradeDirection direction, Boolean paper) {
        this.direction = direction;
        this.paper = paper != null ? paper : false;
    }
    
    public TradeDirection getDirection() {
        return direction;
    }
    
    public void setDirection(TradeDirection direction) {
        this.direction = direction;
    }
    
    public Boolean isPaper() {
        return paper;
    }
    
    public void setPaper(Boolean paper) {
        this.paper = paper;
    }
}