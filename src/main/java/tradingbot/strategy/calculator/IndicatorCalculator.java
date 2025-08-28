package tradingbot.strategy.calculator;

import java.util.HashMap;
// ...existing code...
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

// ...existing code...

import tradingbot.service.BinanceFuturesService.Candle;
import tradingbot.service.FuturesExchangeService;
import tradingbot.strategy.indicator.TechnicalIndicator;

public final class IndicatorCalculator {
    private static final Logger LOGGER = Logger.getLogger(IndicatorCalculator.class.getName());
    private static final int CANDLE_LIMIT = 100;

    private final FuturesExchangeService exchangeService;
    private final Map<String, TechnicalIndicator> indicators = new HashMap<>();

    public IndicatorCalculator(FuturesExchangeService exchangeService, Map<String, TechnicalIndicator> indicators) {
        this.exchangeService = exchangeService;
        this.indicators.putAll(indicators);
    }

    // Extensibility: Register new indicators at runtime
    public void registerIndicator(String name, TechnicalIndicator indicator) {
        indicators.put(name, indicator);
    }

    public IndicatorValues computeIndicators(String timeframe, String symbol) {
        if (LOGGER.isLoggable(java.util.logging.Level.INFO)) {
            LOGGER.info(String.format("Computing indicators for %s on %s timeframe", symbol, timeframe));
        }
        List<Candle> candles = exchangeService.fetchOhlcv(symbol, timeframe, CANDLE_LIMIT);
        if (candles == null || candles.size() < 26) {
            if (LOGGER.isLoggable(java.util.logging.Level.WARNING)) {
                LOGGER.warning(String.format("Insufficient data for indicators: %s, timeframe: %s", symbol, timeframe));
            }
            return null;
        }
        IndicatorValues values = new IndicatorValues();
        indicators.forEach((name, indicator) -> {
            double result = indicator.compute(candles, timeframe);
            switch (name) {
                case "rsi": values.setRsi(result); break;
                case "macd": values.setMacd(result); break;
                case "signal": values.setSignal(result); break;
                case "lowerBand": values.setLowerBand(result); break;
                case "upperBand": values.setUpperBand(result); break;
                // Add more cases for new indicators
                default:
                    if (LOGGER.isLoggable(java.util.logging.Level.FINE)) {
                        LOGGER.fine(String.format("Unknown indicator name: %s", name));
                    }
                    break;
            }
        });
        return values;
    }

    // ...existing code...
}