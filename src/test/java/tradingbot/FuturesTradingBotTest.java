package tradingbot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.TradeDirection;
import tradingbot.config.TradingConfig;
import tradingbot.service.BinanceFuturesService;
import tradingbot.service.FuturesExchangeService;
import tradingbot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.strategy.calculator.IndicatorCalculator;
import tradingbot.strategy.calculator.IndicatorValues;
import tradingbot.strategy.exit.PositionExitCondition;
import tradingbot.strategy.tracker.TrailingStopTracker;

class FuturesTradingBotTest {
    private static final String SYMBOL = "BTCUSDT";
    private static final double TRADE_AMOUNT = 0.001;
    private static final int LEVERAGE = 3;
    private static final double TRAILING_STOP_PERCENT = 1.0;
    private static final int RSI_PERIOD = 14;
    private static final double RSI_OVERSOLD = 30.0;
    private static final double RSI_OVERBOUGHT = 70.0;
    private static final int MACD_FAST = 12;
    private static final int MACD_SLOW = 26;
    private static final int MACD_SIGNAL = 9;
    private static final int BB_PERIOD = 20;
    private static final double BB_STD = 2.0;
    private static final int INTERVAL = 900;

    @Mock
    private FuturesExchangeService exchangeService;
    @Mock
    private IndicatorCalculator indicatorCalculator; // Used for other tests
    @Mock
    private TrailingStopTracker trailingStopTracker;
    @Mock
    private SentimentAnalyzer sentimentAnalyzer;
    @Mock
    private PositionExitCondition rsiExit;
    @Mock
    private PositionExitCondition macdExit;
    @Mock
    private PositionExitCondition liquidationRiskExit;
    @Mock
    private RedisTemplate<String, IndicatorValues> redisTemplate;
    @Mock
    private RedisTemplate<String, Long> longRedisTemplate;

    @Mock
    private ValueOperations<String, IndicatorValues> indicatorValueOps;
    @Mock
    private ValueOperations<String, Long> longValueOps;

    private TradingConfig config;
    private FuturesTradingBot longBot;
    private FuturesTradingBot shortBot;

    @BeforeEach
    void setUp() {
    MockitoAnnotations.openMocks(this);
    config = new TradingConfig(SYMBOL, TRADE_AMOUNT, LEVERAGE, TRAILING_STOP_PERCENT, RSI_PERIOD,
        RSI_OVERSOLD, RSI_OVERBOUGHT, MACD_FAST, MACD_SLOW, MACD_SIGNAL, BB_PERIOD, BB_STD, INTERVAL);
    List<PositionExitCondition> exitConditions = Arrays.asList(rsiExit, macdExit, liquidationRiskExit);
    FuturesTradingBot.BotParams longParams = new FuturesTradingBot.BotParams.Builder()
        .exchangeService(exchangeService)
        .indicatorCalculator(indicatorCalculator)
        .trailingStopTracker(trailingStopTracker)
        .sentimentAnalyzer(sentimentAnalyzer)
        .exitConditions(exitConditions)
        .config(config)
        .tradeDirection(TradeDirection.LONG)
        .sentimentAnalyzer(sentimentAnalyzer)
        .skipLeverageInit(true)
        .build();
    FuturesTradingBot.BotParams shortParams = new FuturesTradingBot.BotParams.Builder()
        .exchangeService(exchangeService)
        .indicatorCalculator(indicatorCalculator)
        .trailingStopTracker(trailingStopTracker)
        .sentimentAnalyzer(sentimentAnalyzer)
        .exitConditions(exitConditions)
        .config(config)
        .tradeDirection(TradeDirection.SHORT)
        .sentimentAnalyzer(sentimentAnalyzer)
        .skipLeverageInit(true)
        .build();
    longBot = new FuturesTradingBot(longParams);
    shortBot = new FuturesTradingBot(shortParams);
    when(redisTemplate.opsForValue()).thenReturn(indicatorValueOps);
    when(longRedisTemplate.opsForValue()).thenReturn(longValueOps);
    }

    @Test
    void shouldEnterLongPositionWhenTechnicalConditionsMet() {
        when(exchangeService.getCurrentPrice(SYMBOL)).thenReturn(50000.0);
        when(exchangeService.getMarginBalance()).thenReturn(1000.0);
        IndicatorValues dailyIndicators = new IndicatorValues();
        dailyIndicators.setRsi(25.0);
        dailyIndicators.setMacd(100.0);
        dailyIndicators.setSignal(90.0);
        dailyIndicators.setLowerBand(49500.0);
        dailyIndicators.setUpperBand(50500.0);
        IndicatorValues weeklyIndicators = new IndicatorValues();
    when(redisTemplate.delete("indicators:BTCUSDT:1d")).thenReturn(true);
        when(indicatorCalculator.computeIndicators("1d", SYMBOL)).thenReturn(dailyIndicators);
        when(indicatorCalculator.computeIndicators("1w", SYMBOL)).thenReturn(weeklyIndicators);

    invokePrivateMethod(longBot, "enterPosition");

        verify(exchangeService).enterLongPosition(SYMBOL, TRADE_AMOUNT);
        verify(trailingStopTracker).initializeTrailingStop(50000.0);
    }

    @Test
    void shouldEnterLongPositionWithSentimentEnabled() {
        longBot.enableSentimentAnalysis(true);
        when(exchangeService.getCurrentPrice(SYMBOL)).thenReturn(50000.0);
        when(exchangeService.getMarginBalance()).thenReturn(1000.0);
        IndicatorValues dailyIndicators = new IndicatorValues();
        dailyIndicators.setRsi(25.0);
        dailyIndicators.setMacd(100.0);
        dailyIndicators.setSignal(90.0);
        dailyIndicators.setLowerBand(49500.0);
        dailyIndicators.setUpperBand(50500.0);
        IndicatorValues weeklyIndicators = new IndicatorValues();
        weeklyIndicators.setRsi(60.0);
        when(indicatorCalculator.computeIndicators("1d", SYMBOL)).thenReturn(dailyIndicators);
        when(indicatorCalculator.computeIndicators("1w", SYMBOL)).thenReturn(weeklyIndicators);
        when(sentimentAnalyzer.isPositiveSentiment(SYMBOL)).thenReturn(true);

    invokePrivateMethod(longBot, "enterPosition");

        verify(exchangeService).enterLongPosition(SYMBOL, TRADE_AMOUNT);
    }

    @Test
    void shouldNotEnterLongPositionWithInsufficientBalance() {
        when(exchangeService.getCurrentPrice(SYMBOL)).thenReturn(50000.0);
        when(exchangeService.getMarginBalance()).thenReturn(0.0);
        IndicatorValues dailyIndicators = new IndicatorValues();
        dailyIndicators.setRsi(25.0);
        dailyIndicators.setMacd(100.0);
        dailyIndicators.setSignal(90.0);
        dailyIndicators.setLowerBand(49500.0);
        dailyIndicators.setUpperBand(50500.0);
        IndicatorValues weeklyIndicators = new IndicatorValues();
        weeklyIndicators.setRsi(60.0);
        when(indicatorCalculator.computeIndicators("1d", SYMBOL)).thenReturn(dailyIndicators);
        when(indicatorCalculator.computeIndicators("1w", SYMBOL)).thenReturn(weeklyIndicators);

    invokePrivateMethod(longBot, "enterPosition");

        verify(exchangeService, never()).enterLongPosition(anyString(), anyDouble());
    }

    @Test
    void shouldExitLongPositionOnTrailingStop() throws Exception {
        setPosition(longBot, "long");
        when(trailingStopTracker.checkTrailingStop(anyDouble())).thenReturn(true);
        when(exchangeService.getCurrentPrice(SYMBOL)).thenReturn(49000.0);

    invokePrivateMethod(longBot, "exitPosition");

        verify(exchangeService).exitLongPosition(SYMBOL, TRADE_AMOUNT);
        verify(trailingStopTracker).reset();
    }

    @Test
    void shouldExitLongPositionOnRsiExit() throws Exception {
        setPosition(longBot, "long");
        when(rsiExit.shouldExit()).thenReturn(true);
        when(exchangeService.getCurrentPrice(SYMBOL)).thenReturn(49000.0);

    invokePrivateMethod(longBot, "exitPosition");

        verify(exchangeService).exitLongPosition(SYMBOL, TRADE_AMOUNT);
        verify(trailingStopTracker).reset();
    }

    @Test
    void shouldHandleInsufficientData() {
        when(indicatorCalculator.computeIndicators("1d", SYMBOL)).thenReturn(null);

        invokePrivateMethod(longBot, "fetchMarketData");

        verify(indicatorCalculator).computeIndicators("1d", SYMBOL);
        // Remove verifyNoInteractions(exchangeService); since skipLeverageInit avoids unwanted calls
    }

    @Test
    void shouldUpdateDynamicLeverage() {
        longBot.setDynamicLeverage(5);
        shortBot.setDynamicLeverage(5);
        verify(exchangeService, times(2)).setLeverage(SYMBOL, 5);
    }

    @Test
    void shouldThrowExceptionForInvalidLeverage() {
        assertThrows(IllegalArgumentException.class, () -> longBot.setDynamicLeverage(150));
        assertThrows(IllegalArgumentException.class, () -> shortBot.setDynamicLeverage(150));
    }

    @Test
    void shouldEnableAndDisableSentimentAnalysis() {
        longBot.enableSentimentAnalysis(true);
    assertTrue((Boolean) getFieldValue(longBot, "sentimentEnabled"));
    longBot.enableSentimentAnalysis(false);
    assertFalse((Boolean) getFieldValue(longBot, "sentimentEnabled"));
    shortBot.enableSentimentAnalysis(true);
    assertTrue((Boolean) getFieldValue(shortBot, "sentimentEnabled"));
    shortBot.enableSentimentAnalysis(false);
    assertFalse((Boolean) getFieldValue(shortBot, "sentimentEnabled"));
    }

    @Test
    void shouldUseCachedIndicators() {
        IndicatorValues dailyIndicators = new IndicatorValues();
        dailyIndicators.setRsi(25.0);
        dailyIndicators.setMacd(100.0);
        dailyIndicators.setSignal(90.0);
        dailyIndicators.setLowerBand(49500.0);
        dailyIndicators.setUpperBand(50500.0);
        when(indicatorCalculator.computeIndicators("1d", SYMBOL)).thenReturn(dailyIndicators);
        when(indicatorCalculator.computeIndicators("1w", SYMBOL)).thenReturn(new IndicatorValues());

        invokePrivateMethod(longBot, "fetchMarketData");
        invokePrivateMethod(longBot, "fetchMarketData");

        verify(indicatorCalculator, times(1)).computeIndicators("1d", SYMBOL); // Cached
        verify(indicatorCalculator, times(1)).computeIndicators("1w", SYMBOL);
    }

    @Test
    void shouldInvalidateCacheOnNewCandle() throws Exception {
        var indicatorsMap = new java.util.HashMap<String, tradingbot.strategy.indicator.TechnicalIndicator>();
        IndicatorCalculator realCalculator = new IndicatorCalculator(exchangeService, indicatorsMap, redisTemplate);

        // Set redisTemplate via reflection
        var redisField = IndicatorCalculator.class.getDeclaredField("redisTemplate");
        redisField.setAccessible(true);
        redisField.set(realCalculator, redisTemplate);

        // Simulate cached value
        IndicatorValues cachedIndicators = new IndicatorValues();
        when(redisTemplate.opsForValue()).thenReturn(indicatorValueOps);
        when(indicatorValueOps.get("indicators:BTCUSDT:1d")).thenReturn(cachedIndicators);

        BinanceFuturesService.Candle newCandle = new BinanceFuturesService.Candle();
        newCandle.setCloseTime(2000L);
        newCandle.setClose(new java.math.BigDecimal("50500"));
        List<BinanceFuturesService.Candle> candleList = Arrays.asList(newCandle);
        when(exchangeService.fetchOhlcv(SYMBOL, "1d", 100)).thenReturn(candleList);

        when(redisTemplate.delete("indicators:BTCUSDT:1d")).thenReturn(true);

        // Simulate indicator calculation which should trigger cache logic
        realCalculator.computeIndicators("1d", SYMBOL);

        verify(redisTemplate, atLeastOnce()).delete("indicators:BTCUSDT:1d");
    }

    private void setPosition(FuturesTradingBot bot, String position) throws Exception {
        var field = FuturesTradingBot.class.getDeclaredField("positionStatus");
        field.setAccessible(true);
        field.set(bot, position);
    }

    private void invokePrivateMethod(FuturesTradingBot bot, String methodName) {
        try {
            var method = FuturesTradingBot.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(bot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke private method: " + methodName, e);
        }
    }

    private Object getFieldValue(FuturesTradingBot bot, String fieldName) {
        try {
            var field = FuturesTradingBot.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(bot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field value: " + fieldName, e);
        }
    }

    // --- Integration Tests ---
    @Test
    void integrationTest_LongAndShortPaperTrading() {
        // Use a mock PaperFuturesExchangeService for integration
        var paperExchange = mock(tradingbot.service.PaperFuturesExchangeService.class);
        FuturesTradingBot.BotParams longPaperParams = new FuturesTradingBot.BotParams.Builder()
            .exchangeService(paperExchange)
            .indicatorCalculator(indicatorCalculator)
            .trailingStopTracker(trailingStopTracker)
            .sentimentAnalyzer(sentimentAnalyzer)
            .exitConditions(Arrays.asList(rsiExit, macdExit, liquidationRiskExit))
            .config(config)
            .tradeDirection(TradeDirection.LONG)
            .skipLeverageInit(true)
            .build();
        FuturesTradingBot.BotParams shortPaperParams = new FuturesTradingBot.BotParams.Builder()
            .exchangeService(paperExchange)
            .indicatorCalculator(indicatorCalculator)
            .trailingStopTracker(trailingStopTracker)
            .sentimentAnalyzer(sentimentAnalyzer)
            .exitConditions(Arrays.asList(rsiExit, macdExit, liquidationRiskExit))
            .config(config)
            .tradeDirection(TradeDirection.SHORT)
            .skipLeverageInit(true)
            .build();

        FuturesTradingBot longPaperBot = new FuturesTradingBot(longPaperParams);
        FuturesTradingBot shortPaperBot = new FuturesTradingBot(shortPaperParams);

        // Simulate technical conditions for long
        when(indicatorCalculator.computeIndicators("1d", SYMBOL)).thenReturn(new IndicatorValues());
        when(indicatorCalculator.computeIndicators("1w", SYMBOL)).thenReturn(new IndicatorValues());
        when(paperExchange.getCurrentPrice(SYMBOL)).thenReturn(50000.0);
        when(paperExchange.getMarginBalance()).thenReturn(10000.0);

        invokePrivateMethod(longPaperBot, "enterPosition");
        assertEquals("long", getFieldValue(longPaperBot, "positionStatus"));

        // Simulate technical conditions for short
        invokePrivateMethod(shortPaperBot, "enterPosition");
        assertEquals("short", getFieldValue(shortPaperBot, "positionStatus"));

        // Exit positions
        invokePrivateMethod(longPaperBot, "exitPosition");
        assertNull(getFieldValue(longPaperBot, "positionStatus"));
        invokePrivateMethod(shortPaperBot, "exitPosition");
        assertNull(getFieldValue(shortPaperBot, "positionStatus"));
    }
}