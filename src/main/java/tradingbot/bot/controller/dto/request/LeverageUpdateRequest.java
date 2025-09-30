package tradingbot.bot.controller.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to update leverage")
public class LeverageUpdateRequest {
    
    @NotNull(message = "Leverage is required")
    @DecimalMin(value = "1.0", message = "Leverage must be at least 1")
    @DecimalMax(value = "100.0", message = "Leverage must not exceed 100")
    @Schema(description = "Leverage multiplier (1-100)", example = "10", minimum = "1", maximum = "100")
    @JsonProperty("leverage")
    private Double leverage;
    
    public LeverageUpdateRequest() {}
    
    public LeverageUpdateRequest(Double leverage) {
        this.leverage = leverage;
    }
    
    public Double getLeverage() {
        return leverage;
    }
    
    public void setLeverage(Double leverage) {
        this.leverage = leverage;
    }
}