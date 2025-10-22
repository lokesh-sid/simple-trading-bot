package tradingbot.agent.domain.model;

import java.time.Instant;

/**
 * Perception - What the agent observes about the market
 */
public class Perception {
    
    private final String symbol;
    private final double currentPrice;
    private final String trend;
    private final String sentiment;
    private final double volume;
    private final Instant timestamp;
    
    public Perception(String symbol, double currentPrice, String trend, 
                     String sentiment, double volume, Instant timestamp) {
        this.symbol = symbol;
        this.currentPrice = currentPrice;
        this.trend = trend;
        this.sentiment = sentiment;
        this.volume = volume;
        this.timestamp = timestamp;
    }
    
    public String getSymbol() { return symbol; }
    public double getCurrentPrice() { return currentPrice; }
    public String getTrend() { return trend; }
    public String getSentiment() { return sentiment; }
    public double getVolume() { return volume; }
    public Instant getTimestamp() { return timestamp; }
}
