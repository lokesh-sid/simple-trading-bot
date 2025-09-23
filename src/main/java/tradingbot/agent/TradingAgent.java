package tradingbot.agent;

public interface TradingAgent<T> {
    void start();
    void stop();
    void processMarketData(T marketData);
    void executeTrade();
}
