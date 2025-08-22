package tradingbot.service;

import java.util.List;

import tradingbot.service.BinanceFuturesService.Candle;

public interface FuturesExchangeService {
    List<Candle> fetchOhlcv(String symbol, String timeframe, int limit);
    double getCurrentPrice(String symbol);
    double getMarginBalance();
    void setLeverage(String symbol, int leverage);
    void enterLongPosition(String symbol, double tradeAmount);
    void exitLongPosition(String symbol, double tradeAmount);
}