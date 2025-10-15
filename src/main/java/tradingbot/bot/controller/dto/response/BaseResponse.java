package tradingbot.bot.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Base class for all API responses
 */
@Schema(description = "Base response class for all API operations")
public abstract class BaseResponse {

    @Schema(description = "Response timestamp", example = "1696070400000")
    @JsonProperty("timestamp")
    private long timestamp;

    protected BaseResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}