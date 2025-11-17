package tradingbot.bot.controller.dto.response;

/**
 * Response for bot deletion API
 */
public class BotDeletedResponse {
    private String message;

    public BotDeletedResponse() {}

    public BotDeletedResponse(String message) {
        this.message = message;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
