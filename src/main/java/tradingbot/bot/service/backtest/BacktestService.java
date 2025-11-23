package tradingbot.bot.service.backtest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.springframework.stereotype.Service;

import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.TradeDirection;
import tradingbot.bot.controller.exception.BotOperationException;
import tradingbot.bot.service.BinanceFuturesService.Candle;
import tradingbot.bot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.bot.strategy.calculator.IndicatorCalculator;
import tradingbot.bot.strategy.exit.PositionExitCondition;
import tradingbot.bot.strategy.indicator.BollingerBandsIndicator;
import tradingbot.bot.strategy.indicator.MACDTechnicalIndicator;
import tradingbot.bot.strategy.indicator.RSITechnicalIndicator;
import tradingbot.bot.strategy.indicator.TechnicalIndicator;
import tradingbot.bot.strategy.tracker.TrailingStopTracker;
import tradingbot.config.TradingConfig;

@Service
public class BacktestService {
    private static final Logger LOGGER = Logger.getLogger(BacktestService.class.getName());
    
    private final HistoricalDataLoader dataLoader;
    
    public BacktestService(HistoricalDataLoader dataLoader) {
        this.dataLoader = dataLoader;
    }
    
    public BacktestResult runBacktest(InputStream csvData, TradingConfig config, long latencyMs, double slippagePercent, double feeRate) {
        LOGGER.info("Starting backtest for " + config.getSymbol());
        
        // 1. Load Data
        List<Candle> history = dataLoader.loadFromStream(csvData);
        if (history.isEmpty()) {
            throw new BotOperationException("backtest", "No data loaded from stream");
        }
        
        return executeBacktest(history, config, latencyMs, slippagePercent, feeRate);
    }
    
    public BacktestResult runBacktest(String csvFilePath, TradingConfig config, long latencyMs, double slippagePercent, double feeRate) {
        LOGGER.info("Starting backtest for " + config.getSymbol());
        
        // 1. Load Data
        List<Candle> history = dataLoader.loadFromCsv(csvFilePath);
        if (history.isEmpty()) {
            throw new BotOperationException("backtest", "No data loaded from " + csvFilePath);
        }
        
        return executeBacktest(history, config, latencyMs, slippagePercent, feeRate);
    }

    private BacktestResult executeBacktest(List<Candle> history, TradingConfig config, long latencyMs, double slippagePercent, double feeRate) {
        // 2. Setup Exchange
        BacktestExchangeService exchange = new BacktestExchangeService(latencyMs, slippagePercent, feeRate);
        
        // 3. Setup Indicators
        Map<String, TechnicalIndicator> indicators = new HashMap<>();
        indicators.put("rsi", new RSITechnicalIndicator(14));
        indicators.put("macd", new MACDTechnicalIndicator(12, 26, 9, false));
        indicators.put("signal", new MACDTechnicalIndicator(12, 26, 9, true));
        indicators.put("lowerBand", new BollingerBandsIndicator(20, 2.0, true));
        indicators.put("upperBand", new BollingerBandsIndicator(20, 2.0, false));
        
        // 4. Setup Bot Dependencies
        TrailingStopTracker trailingStopTracker = new TrailingStopTracker(exchange, config.getTrailingStopPercent());
        SentimentAnalyzer sentimentAnalyzer = new MockSentimentAnalyzer(); 
        List<PositionExitCondition> exitConditions = new ArrayList<>();
        exitConditions.add(() -> false); // Dummy condition that never triggers
        
        // 5. Initialize Bot
        FuturesTradingBot.BotParams params = new FuturesTradingBot.BotParams.Builder()
                .exchangeService(exchange)
                .indicatorCalculator(new IndicatorCalculator(exchange, indicators, null))
                .trailingStopTracker(trailingStopTracker)
                .sentimentAnalyzer(sentimentAnalyzer)
                .exitConditions(exitConditions)
                .config(config)
                .tradeDirection(TradeDirection.LONG) 
                .forTesting()
                .build();
                
        FuturesTradingBot bot = new FuturesTradingBot(params);
        
        // 6. Run Simulation Loop
        int startIdx = 100; 
        for (int i = startIdx; i < history.size(); i++) {
            exchange.setMarketContext(history, i);
            exchange.processPendingOrders();
            
            bot.executeTradingStep();
        }
        
        // 7. Generate Report
        return new BacktestResult(
                exchange.getMarginBalance(), 
                exchange.getMarginBalance() - 10000.0, 
                0 
        );
    }
    
    private static class MockSentimentAnalyzer extends SentimentAnalyzer {
        public MockSentimentAnalyzer() {
            super(null);
        }
        @Override
        public boolean isPositiveSentiment(String symbol) { return true; }
        @Override
        public boolean isNegativeSentiment(String symbol) { return true; }
    }
    
    public static class BacktestResult {
        public double finalBalance;
        public double totalProfit;
        public int totalTrades;
        
        public BacktestResult(double finalBalance, double totalProfit, int totalTrades) {
            this.finalBalance = finalBalance;
            this.totalProfit = totalProfit;
            this.totalTrades = totalTrades;
        }
        
        @Override
        public String toString() {
            return "BacktestResult{finalBalance=%.2f, totalProfit=%.2f, totalTrades=%d}".formatted(finalBalance, totalProfit, totalTrades);
        }
    }
}
