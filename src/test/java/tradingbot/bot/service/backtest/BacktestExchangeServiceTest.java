package tradingbot.bot.service.backtest;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tradingbot.bot.service.BinanceFuturesService.Candle;

class BacktestExchangeServiceTest {

    private BacktestExchangeService exchangeService;
    private List<Candle> history;

    @BeforeEach
    void setUp() {
        exchangeService = new BacktestExchangeService(0, 0.0, 0.0004); // No latency, no slippage, 0.04% fee
        history = new ArrayList<>();
        
        // Create some dummy candles
        for (int i = 0; i < 10; i++) {
            Candle candle = new Candle();
            candle.setCloseTime(1000L + i * 60000); // 1 minute intervals
            BigDecimal price = new BigDecimal(50000 + i * 100);
            candle.setClose(price);
            candle.setHigh(price.add(new BigDecimal(50)));
            candle.setLow(price.subtract(new BigDecimal(50)));
            history.add(candle);
        }
    }

    @Test
    void shouldUpdateCurrentPriceBasedOnContext() {
        exchangeService.setMarketContext(history, 0);
        assertEquals(50000.0, exchangeService.getCurrentPrice("BTCUSDT"));

        exchangeService.setMarketContext(history, 5);
        assertEquals(50500.0, exchangeService.getCurrentPrice("BTCUSDT"));
    }

    @Test
    void shouldExecuteLongOrderImmediatelyWithoutLatency() {
        exchangeService.setMarketContext(history, 0);
        exchangeService.enterLongPosition("BTCUSDT", 0.1);
        
        // Process pending orders (should execute immediately as latency is 0)
        exchangeService.processPendingOrders();
        
        // 10000 - (0.1 * 50000) = 5000 used margin
        // Fee = 5000 * 0.0004 = 2.0
        // Balance = 4998.0
        assertEquals(4998.0, exchangeService.getMarginBalance(), 0.01);
    }

    @Test
    void shouldExecuteShortOrderWithLatency() {
        exchangeService = new BacktestExchangeService(1000, 0.0, 0.0004); // 1000ms latency, 0.04% fee
        exchangeService.setMarketContext(history, 0); // Time 1000
        exchangeService.enterShortPosition("BTCUSDT", 0.1);
        
        exchangeService.processPendingOrders(); // Time 1000 < 1000 + 1000. Should not execute.
        assertEquals(10000.0, exchangeService.getMarginBalance());
        
        exchangeService.setMarketContext(history, 1); // Time 1000 + 60000 = 61000 > 2000.
        exchangeService.processPendingOrders();
        
        // Should execute now at price of index 1 (50100)
        // requiredMargin = 0.1 * 50100 = 5010.
        // Fee = 5010 * 0.0004 = 2.004
        // Balance = 10000 - 5010 - 2.004 = 4987.996
        assertEquals(4987.996, exchangeService.getMarginBalance(), 0.01);
    }

    @Test
    void shouldApplySlippage() {
        exchangeService = new BacktestExchangeService(0, 0.01, 0.0004); // 1% slippage, 0.04% fee
        exchangeService.setMarketContext(history, 0); // Price 50000
        
        // Buy with slippage: Price = 50000 * 1.01 = 50500
        exchangeService.enterLongPosition("BTCUSDT", 0.1);
        exchangeService.processPendingOrders();
        
        // Margin = 0.1 * 50500 = 5050
        // Fee = 5050 * 0.0004 = 2.02
        // Balance = 10000 - 5050 - 2.02 = 4947.98
        assertEquals(4947.98, exchangeService.getMarginBalance(), 0.01);
    }
    
    @Test
    void shouldCalculateProfitOnExit() {
        exchangeService.setMarketContext(history, 0); // Price 50000
        exchangeService.enterLongPosition("BTCUSDT", 0.1);
        exchangeService.processPendingOrders();
        
        // Balance = 4998.0
        
        exchangeService.setMarketContext(history, 5); // Price 50500
        exchangeService.exitLongPosition("BTCUSDT", 0.1);
        exchangeService.processPendingOrders();
        
        // Profit = (50500 - 50000) * 0.1 = 50
        // Returned Margin = 5000
        // Exit Fee = 5050 * 0.0004 = 2.02
        // New Balance = 4998.0 + 5000 + 50 - 2.02 = 10045.98
        assertEquals(10045.98, exchangeService.getMarginBalance(), 0.01);
    }
}
