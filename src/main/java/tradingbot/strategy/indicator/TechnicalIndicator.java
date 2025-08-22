package tradingbot.strategy.indicator;

import java.util.List;

import tradingbot.service.BinanceFuturesService.Candle;

public interface TechnicalIndicator {
    double compute(List<Candle> candles, String timeframe);
}