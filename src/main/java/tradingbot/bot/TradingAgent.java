package tradingbot.bot;

public interface TradingAgent {
    void start();
    void stop();
    void processMarketData(Object marketData);
    void executeTrade();
}
