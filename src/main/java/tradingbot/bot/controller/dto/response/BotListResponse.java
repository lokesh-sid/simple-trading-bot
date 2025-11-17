package tradingbot.bot.controller.dto.response;

import java.util.List;

/**
 * Response for bot list API with pagination support
 */
public class BotListResponse {
    private List<String> botIds;
    private PaginationInfo pagination;
    private int activeInMemory;

    public BotListResponse() {}

    public BotListResponse(List<String> botIds, PaginationInfo pagination, int activeInMemory) {
        this.botIds = botIds;
        this.pagination = pagination;
        this.activeInMemory = activeInMemory;
    }

    // Getters and setters
    public List<String> getBotIds() { return botIds; }
    public void setBotIds(List<String> botIds) { this.botIds = botIds; }

    public PaginationInfo getPagination() { return pagination; }
    public void setPagination(PaginationInfo pagination) { this.pagination = pagination; }

    public int getActiveInMemory() { return activeInMemory; }
    public void setActiveInMemory(int activeInMemory) { this.activeInMemory = activeInMemory; }
}
