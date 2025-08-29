
package tradingbot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static tradingbot.util.FuturesTradingBotTestUtils.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.FuturesTradingBot.BotParams;
import tradingbot.bot.TradeDirection;
import tradingbot.service.PaperFuturesExchangeService;
import tradingbot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.strategy.calculator.IndicatorCalculator;
import tradingbot.strategy.exit.PositionExitCondition;
import tradingbot.strategy.tracker.TrailingStopTracker;


class FuturesTradingBotIntegrationTest {
    private PaperFuturesExchangeService paperExchange;
    private IndicatorCalculator indicatorCalculator;
    private TrailingStopTracker trailingStopTracker;
    private SentimentAnalyzer sentimentAnalyzer;
    private PositionExitCondition rsiExit;
    private PositionExitCondition macdExit;

    // Initialize in a setup method
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        paperExchange = mock(tradingbot.service.PaperFuturesExchangeService.class);
        indicatorCalculator = mock(IndicatorCalculator.class);
        trailingStopTracker = mock(TrailingStopTracker.class);
        sentimentAnalyzer = mock(SentimentAnalyzer.class);
        rsiExit = mock(PositionExitCondition.class);
        macdExit = mock(PositionExitCondition.class);
    }

    @Test
    @DisplayName("Integration Test: Long and Short Paper Trading")
    void integrationTest_LongAndShortPaperTrading() {

        var liquidationRiskExit = mock(PositionExitCondition.class);
        List<PositionExitCondition> exitConditions = Arrays.asList(rsiExit, macdExit, liquidationRiskExit);
        FuturesTradingBot.BotParams longPaperParams = getPaperExchangeParams(exitConditions, TradeDirection.LONG);
        FuturesTradingBot.BotParams shortPaperParams = getBotParams(paperExchange, indicatorCalculator, trailingStopTracker, sentimentAnalyzer, exitConditions, TradeDirection.SHORT);

        FuturesTradingBot longPaperBot = new FuturesTradingBot(longPaperParams);
        FuturesTradingBot shortPaperBot = new FuturesTradingBot(shortPaperParams);

        // Simulate technical conditions for long
        when(indicatorCalculator.computeIndicators("1d", SYMBOL)).thenReturn(new tradingbot.strategy.calculator.IndicatorValues());
        when(indicatorCalculator.computeIndicators("1w", SYMBOL)).thenReturn(new tradingbot.strategy.calculator.IndicatorValues());
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

    private BotParams getPaperExchangeParams(List<PositionExitCondition> exitConditions, TradeDirection direction) {
        return getBotParams(paperExchange, indicatorCalculator, trailingStopTracker, sentimentAnalyzer, exitConditions, direction);
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
}
