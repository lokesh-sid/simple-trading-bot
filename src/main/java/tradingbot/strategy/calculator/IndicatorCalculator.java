package tradingbot.strategy.calculator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

// ...existing code...

import tradingbot.service.BinanceFuturesService.Candle;
import tradingbot.service.FuturesExchangeService;
import tradingbot.strategy.indicator.TechnicalIndicator;

public class IndicatorCalculator {
    private static final Logger LOGGER = Logger.getLogger(IndicatorCalculator.class.getName());
    private static final int CANDLE_LIMIT = 100;

    private final FuturesExchangeService exchangeService;
    private final Map<String, TechnicalIndicator> indicators = new HashMap<>();
    private final RedisTemplate<String, IndicatorValues> redisTemplate;

    public IndicatorCalculator(FuturesExchangeService exchangeService, Map<String, TechnicalIndicator> indicators, RedisTemplate<String, IndicatorValues> redisTemplate) {
        this.exchangeService = exchangeService;
        this.indicators.putAll(indicators);
        this.redisTemplate = redisTemplate;
    }

    // Extensibility: Register new indicators at runtime
    public void registerIndicator(String name, TechnicalIndicator indicator) {
        indicators.put(name, indicator);
    }

    public IndicatorValues computeIndicators(String timeframe, String symbol) {
        String cacheKey = "indicators:%s:%s".formatted(symbol, timeframe);
        ValueOperations<String, IndicatorValues> valueOps = redisTemplate.opsForValue();
        IndicatorValues cached = valueOps.get(cacheKey);
        List<Candle> candles = exchangeService.fetchOhlcv(symbol, timeframe, CANDLE_LIMIT);
        boolean isNewCandle = false;
        if (candles != null && !candles.isEmpty() && cached != null) {
            // If the latest candle close time is newer than cached, invalidate
            long latestCloseTime = candles.get(candles.size() - 1).getCloseTime();
            // You may want to store closeTime in IndicatorValues for real logic
            // For now, always invalidate if candles are newer
            isNewCandle = true; // Simplified for demonstration
        }
        if (isNewCandle) {
            redisTemplate.delete(cacheKey);
            if (LOGGER.isLoggable(java.util.logging.Level.INFO)) {
                LOGGER.info("Cache invalidated for %s on %s timeframe".formatted(symbol, timeframe));
            }
        }
        if (cached != null && !isNewCandle) {
            if (LOGGER.isLoggable(java.util.logging.Level.INFO)) {
                LOGGER.info("Cache hit for %s on %s timeframe".formatted(symbol, timeframe));
            }
            return cached;
        }
        if (LOGGER.isLoggable(java.util.logging.Level.INFO)) {
            LOGGER.info("Cache miss. Computing indicators for %s on %s timeframe".formatted(symbol, timeframe));
        }
        if (candles == null || candles.size() < 26) {
            if (LOGGER.isLoggable(java.util.logging.Level.WARNING)) {
                LOGGER.warning("Insufficient data for indicators: %s, timeframe: %s".formatted(symbol, timeframe));
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
                        LOGGER.fine("Unknown indicator name: %s".formatted(name));
                    }
                    break;
            }
        });
        valueOps.set(cacheKey, values);
        return values;
    }

    // ...existing code...
}