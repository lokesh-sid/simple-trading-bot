package tradingbot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import tradingbot.bot.LongFuturesTradingBot;
import tradingbot.config.TradingConfig;
import tradingbot.service.BinanceFuturesService;
import tradingbot.service.FuturesExchangeService;
import tradingbot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.strategy.calculator.IndicatorCalculator;
import tradingbot.strategy.calculator.IndicatorValues;
import tradingbot.strategy.exit.PositionExitCondition;
import tradingbot.strategy.tracker.TrailingStopTracker;

class LongFuturesTradingBotTest {
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
    private IndicatorCalculator indicatorCalculator;
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

    private TradingConfig config;
    private LongFuturesTradingBot bot;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        config = new TradingConfig(SYMBOL, TRADE_AMOUNT, LEVERAGE, TRAILING_STOP_PERCENT, RSI_PERIOD,
                RSI_OVERSOLD, RSI_OVERBOUGHT, MACD_FAST, MACD_SLOW, MACD_SIGNAL, BB_PERIOD, BB_STD, INTERVAL);
        bot = new LongFuturesTradingBot(exchangeService, indicatorCalculator, trailingStopTracker,
                sentimentAnalyzer, Arrays.asList(rsiExit, macdExit, liquidationRiskExit), config);
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldEnterLongPositionWhenTechnicalConditionsMet() throws Exception {
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

        invokePrivateMethod("enterLongPosition");

        verify(exchangeService).enterLongPosition(SYMBOL, TRADE_AMOUNT);
        verify(trailingStopTracker).initializeTrailingStop(50000.0);
    }

    @Test
    void shouldEnterLongPositionWithSentimentEnabled() throws Exception {
        bot.enableSentimentAnalysis(true);
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

        invokePrivateMethod("enterLongPosition");

        verify(exchangeService).enterLongPosition(SYMBOL, TRADE_AMOUNT);
        verify(sentimentAnalyzer).isPositiveSentiment(SYMBOL);
    }

    @Test
    void shouldNotEnterLongPositionWithInsufficientBalance() throws Exception {
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

        invokePrivateMethod("enterLongPosition");

        verify(exchangeService, never()).enterLongPosition(anyString(), anyDouble());
    }

    @Test
    void shouldExitLongPositionOnTrailingStop() throws Exception {
        setPosition("long");
        when(trailingStopTracker.checkTrailingStop(anyDouble())).thenReturn(true);
        when(exchangeService.getCurrentPrice(SYMBOL)).thenReturn(49000.0);

        invokePrivateMethod("exitLongPosition");

        verify(exchangeService).exitLongPosition(SYMBOL, TRADE_AMOUNT);
        verify(trailingStopTracker).reset();
    }

    @Test
    void shouldExitLongPositionOnRsiExit() throws Exception {
        setPosition("long");
        when(rsiExit.shouldExit()).thenReturn(true);
        when(exchangeService.getCurrentPrice(SYMBOL)).thenReturn(49000.0);

        invokePrivateMethod("exitLongPosition");

        verify(exchangeService).exitLongPosition(SYMBOL, TRADE_AMOUNT);
        verify(trailingStopTracker).reset();
    }

    @Test
    void shouldHandleInsufficientData() {
        when(indicatorCalculator.computeIndicators("1d", SYMBOL)).thenReturn(null);

        invokePrivateMethod("fetchMarketData");

        verify(indicatorCalculator).computeIndicators("1d", SYMBOL);
        verifyNoInteractions(exchangeService);
    }

    @Test
    void shouldUpdateDynamicLeverage() {
        bot.setDynamicLeverage(5);
        verify(exchangeService).setLeverage(SYMBOL, 5);
    }

    @Test
    void shouldThrowExceptionForInvalidLeverage() {
        assertThrows(IllegalArgumentException.class, () -> bot.setDynamicLeverage(150));
    }

    @Test
    void shouldEnableAndDisableSentimentAnalysis() {
        bot.enableSentimentAnalysis(true);
        assertTrue(getFieldValue("sentimentEnabled"));
        bot.enableSentimentAnalysis(false);
        assertFalse(getFieldValue("sentimentEnabled"));
    }

    @Test
    void shouldUseCachedIndicators() throws Exception {
        IndicatorValues dailyIndicators = new IndicatorValues();
        dailyIndicators.setRsi(25.0);
        dailyIndicators.setMacd(100.0);
        dailyIndicators.setSignal(90.0);
        dailyIndicators.setLowerBand(49500.0);
        dailyIndicators.setUpperBand(50500.0);
        when(indicatorCalculator.computeIndicators("1d", SYMBOL)).thenReturn(dailyIndicators);
        when(indicatorCalculator.computeIndicators("1w", SYMBOL)).thenReturn(new IndicatorValues());

        invokePrivateMethod("fetchMarketData");
        invokePrivateMethod("fetchMarketData");

        verify(indicatorCalculator, times(1)).computeIndicators("1d", SYMBOL); // Cached
        verify(indicatorCalculator, times(1)).computeIndicators("1w", SYMBOL);
    }

    @Test
    void shouldInvalidateCacheOnNewCandle() throws Exception {
        when(indicatorCalculator.computeIndicators("1d", SYMBOL)).thenReturn(new IndicatorValues());
        ArrayNode candleArray = objectMapper.createArrayNode();
        ArrayNode candleData = objectMapper.createArrayNode();
        candleData.add(1000L); // openTime
        candleData.add("50000"); // open
        candleData.add("51000"); // high
        candleData.add("49000"); // low
        candleData.add("50500"); // close
        candleData.add("100"); // volume
        candleData.add(2000L); // closeTime
        candleArray.add(candleData);
        when(exchangeService.fetchOhlcv(SYMBOL, "1d", 1)).thenReturn(Arrays.asList(new BinanceFuturesService.Candle()));
        when(longRedisTemplate.opsForValue().get("timestamp:" + SYMBOL + ":1d")).thenReturn(1000L);
        invokePrivateMethod(indicatorCalculator, "checkForNewCandle");
        verify(redisTemplate).delete("indicators:" + SYMBOL + ":1d");
    }

    private void setPosition(String position) throws Exception {
        var field = LongFuturesTradingBot.class.getDeclaredField("positionStatus");
        field.setAccessible(true);
        field.set(bot, position);
    }

    private void invokePrivateMethod(String methodName) {
        try {
            var method = LongFuturesTradingBot.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(bot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke private method: " + methodName, e);
        }
    }

    private void invokePrivateMethod(Object target, String methodName) {
        try {
            var method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(target);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke private method: " + methodName, e);
        }
    }

    private boolean getFieldValue(String fieldName) {
        try {
            var field = LongFuturesTradingBot.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (boolean) field.get(bot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field value: " + fieldName, e);
        }
    }
}