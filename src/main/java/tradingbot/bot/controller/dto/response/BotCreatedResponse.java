package tradingbot.bot.controller.dto.response;

/**
 * Response for bot creation API
 */
public class BotCreatedResponse {
    private String botId;
    private String message;

    public BotCreatedResponse() {}

    public BotCreatedResponse(String botId, String message) {
        this.botId = botId;
        this.message = message;
    }

    public String getBotId() { return botId; }
    public void setBotId(String botId) { this.botId = botId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
