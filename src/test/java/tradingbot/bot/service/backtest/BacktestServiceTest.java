package tradingbot.bot.service.backtest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import tradingbot.bot.service.BinanceFuturesService.Candle;
import tradingbot.config.TradingConfig;

class BacktestServiceTest {

    @Mock
    private HistoricalDataLoader dataLoader;

    private BacktestService backtestService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        backtestService = new BacktestService(dataLoader);
    }

    @Test
    void shouldRunBacktestSuccessfully() {
        // Mock Data
        List<Candle> history = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            Candle candle = new Candle();
            candle.setOpenTime(1000L + i * 60000);
            candle.setCloseTime(1000L + i * 60000 + 59999);
            candle.setOpen(new BigDecimal(50000));
            candle.setHigh(new BigDecimal(51000));
            candle.setLow(new BigDecimal(49000));
            candle.setClose(new BigDecimal(50000 + (i % 2 == 0 ? 100 : -100))); // Oscillating price
            candle.setVolume(new BigDecimal(1000));
            history.add(candle);
        }
        when(dataLoader.loadFromCsv(anyString())).thenReturn(history);

        TradingConfig config = new TradingConfig();
        config.setSymbol("BTCUSDT");
        config.setTradeAmount(0.1);
        config.setTrailingStopPercent(1.0);
        config.setLeverage(1);
        
        // Set indicator params to avoid null pointers if defaults aren't set
        config.setLookbackPeriodRsi(14);
        config.setMacdFastPeriod(12);
        config.setMacdSlowPeriod(26);
        config.setMacdSignalPeriod(9);
        config.setBbPeriod(20);
        config.setBbStandardDeviation(2.0);
        config.setRsiOversoldThreshold(30);
        config.setRsiOverboughtThreshold(70);
        config.setInterval(900); // Set interval to avoid IllegalArgumentException

        BacktestService.BacktestResult result = backtestService.runBacktest("dummy.csv", config, 0, 0.0, 0.0004);

        assertNotNull(result);
        // Since price oscillates and logic is complex, we just ensure it ran without error and returned a result
        // Initial balance is 10000.
        assertTrue(result.finalBalance > 0);
    }
    
    @Test
    void shouldThrowExceptionIfNoData() {
        when(dataLoader.loadFromCsv(anyString())).thenReturn(new ArrayList<>());
        TradingConfig config = new TradingConfig();
        config.setSymbol("BTCUSDT");
        
        assertThrows(RuntimeException.class, () -> backtestService.runBacktest("empty.csv", config, 0, 0.0, 0.0004));
    }
}
