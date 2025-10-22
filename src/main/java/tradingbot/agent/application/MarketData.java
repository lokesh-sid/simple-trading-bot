package tradingbot.agent.application;

/**
 * MarketData - Simple DTO for market data
 * 
 * MVP: Simplified market data structure
 * Future: Replace with comprehensive market data from Binance API
 */
public class MarketData {
    
    private final double currentPrice;
    private final double priceChange24h;
    private final double volume;
    private final String trend;
    private final String sentiment;
    private final double sentimentScore;
    
    public MarketData(double currentPrice, double priceChange24h, double volume,
                     String trend, String sentiment, double sentimentScore) {
        this.currentPrice = currentPrice;
        this.priceChange24h = priceChange24h;
        this.volume = volume;
        this.trend = trend;
        this.sentiment = sentiment;
        this.sentimentScore = sentimentScore;
    }
    
    // Getters
    public double getCurrentPrice() { return currentPrice; }
    public double getPriceChange24h() { return priceChange24h; }
    public double getVolume() { return volume; }
    public String getTrend() { return trend; }
    public String getSentiment() { return sentiment; }
    public double getSentimentScore() { return sentimentScore; }
}
