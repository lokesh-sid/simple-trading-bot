package tradingbot.strategy.calculator;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tradingbot.service.BinanceFuturesService.Candle;
import tradingbot.service.FuturesExchangeService;
import tradingbot.strategy.indicator.TechnicalIndicator;

@Component
public class IndicatorCalculator {
    private static final Logger LOGGER = Logger.getLogger(IndicatorCalculator.class.getName());
    private static final int CANDLE_LIMIT = 100;
    private static final double PRICE_CHANGE_THRESHOLD = 0.01; // 1% price change triggers cache invalidation

    private FuturesExchangeService exchangeService;
    private TechnicalIndicator rsiIndicator;
    private TechnicalIndicator macdIndicator;
    private TechnicalIndicator macdSignalIndicator;
    private TechnicalIndicator bbLowerIndicator;
    private TechnicalIndicator bbUpperIndicator;
    private RedisTemplate<String, IndicatorValues> redisTemplate;
    private RedisTemplate<String, Long> longRedisTemplate;

    public IndicatorCalculator(FuturesExchangeService exchangeService, TechnicalIndicator rsiIndicator,
            TechnicalIndicator macdIndicator, TechnicalIndicator macdSignalIndicator,
            TechnicalIndicator bbLowerIndicator, TechnicalIndicator bbUpperIndicator) {
        this.exchangeService = exchangeService;
        this.rsiIndicator = rsiIndicator;
        this.macdIndicator = macdIndicator;
        this.macdSignalIndicator = macdSignalIndicator;
        this.bbLowerIndicator = bbLowerIndicator;
        this.bbUpperIndicator = bbUpperIndicator;
    }

    @Cacheable(value = "indicators", key = "'indicators:' + #symbol + ':' + #timeframe")
    public IndicatorValues computeIndicators(String timeframe, String symbol) {
        LOGGER.info(String.format("Computing indicators for %s on %s timeframe", symbol, timeframe));
        List<Candle> candles = exchangeService.fetchOhlcv(symbol, timeframe, CANDLE_LIMIT);
        if (candles == null || candles.size() < 26) {
            LOGGER.warning(String.format("Insufficient data for indicators: %s, timeframe: %s", symbol, timeframe));
            return null;
        }
        IndicatorValues values = new IndicatorValues();
        values.setRsi(rsiIndicator.compute(candles, timeframe));
        values.setMacd(macdIndicator.compute(candles, timeframe));
        values.setSignal(macdSignalIndicator.compute(candles, timeframe));
        values.setLowerBand(bbLowerIndicator.compute(candles, timeframe));
        values.setUpperBand(bbUpperIndicator.compute(candles, timeframe));

        // Store last candle timestamp for invalidation
        String timestampKey = "timestamp:" + symbol + ":" + timeframe;
        longRedisTemplate.opsForValue().set(timestampKey, candles.get(candles.size() - 1).getCloseTime());
        return values;
    }

    @Scheduled(fixedRate = 60000) // Check every minute
    public void checkForNewCandle() {
        String symbol = "BTCUSDT";
        for (String timeframe : Arrays.asList("1d", "1w")) {
            String cacheKey = "indicators:" + symbol + ":" + timeframe;
            String timestampKey = "timestamp:" + symbol + ":" + timeframe;
            Long cachedTimestamp = longRedisTemplate.opsForValue().get(timestampKey);
            if (cachedTimestamp == null) {
                continue;
            }

            List<Candle> candles = exchangeService.fetchOhlcv(symbol, timeframe, 1);
            if (candles != null && !candles.isEmpty() && candles.get(0).getCloseTime() > cachedTimestamp) {
                LOGGER.info(String.format("New %s candle detected for %s, invalidating cache", timeframe, symbol));
                redisTemplate.delete(cacheKey);
                longRedisTemplate.opsForValue().set(timestampKey, candles.get(0).getCloseTime());
            }

            double currentPrice = exchangeService.getCurrentPrice(symbol);
            double lastClose = candles != null && !candles.isEmpty() ? candles.get(0).getClose().doubleValue() : 0;
            if (lastClose > 0 && Math.abs((currentPrice - lastClose) / lastClose) > PRICE_CHANGE_THRESHOLD) {
                LOGGER.info(String.format("Significant price change (%.2f%%) detected for %s, invalidating cache",
                        Math.abs((currentPrice - lastClose) / lastClose) * 100, symbol));
                redisTemplate.delete(cacheKey);
            }
        }
    }

    @CacheEvict(value = "indicators", key = "'indicators:' + #symbol + ':' + #timeframe")
    public void evictCache(String symbol, String timeframe) {
        LOGGER.info(String.format("Evicting cache for %s on %s timeframe", symbol, timeframe));
    }
}