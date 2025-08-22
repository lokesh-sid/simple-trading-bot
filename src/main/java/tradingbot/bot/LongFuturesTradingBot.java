package tradingbot.bot;

import java.util.List;
import java.util.logging.Logger;

import tradingbot.config.TradingConfig;
import tradingbot.service.FuturesExchangeService;
import tradingbot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.strategy.calculator.IndicatorCalculator;
import tradingbot.strategy.calculator.IndicatorValues;
import tradingbot.strategy.exit.PositionExitCondition;
import tradingbot.strategy.tracker.TrailingStopTracker;

public class LongFuturesTradingBot {
    private static final Logger LOGGER = Logger.getLogger(LongFuturesTradingBot.class.getName());
    private static final int CHECK_INTERVAL_SECONDS = 900; // 15 minutes

    private final FuturesExchangeService exchangeService;
    private final IndicatorCalculator indicatorCalculator;
    private final TrailingStopTracker trailingStopTracker;
    private final SentimentAnalyzer sentimentAnalyzer;
    private final List<PositionExitCondition> exitConditions;
    private TradingConfig config;
    private String positionStatus;
    private double entryPrice;
    private volatile boolean running;
    private volatile boolean sentimentEnabled;
    private int currentLeverage;

    public LongFuturesTradingBot(FuturesExchangeService exchangeService, IndicatorCalculator indicatorCalculator,
                                 TrailingStopTracker trailingStopTracker, SentimentAnalyzer sentimentAnalyzer,
                                 List<PositionExitCondition> exitConditions, TradingConfig config) {
        this.exchangeService = exchangeService;
        this.indicatorCalculator = indicatorCalculator;
        this.trailingStopTracker = trailingStopTracker;
        this.sentimentAnalyzer = sentimentAnalyzer;
        this.exitConditions = exitConditions;
        this.config = config;
        this.positionStatus = null;
        this.entryPrice = 0.0;
        this.running = false;
        this.sentimentEnabled = false;
        this.currentLeverage = config.getLeverage();
        initializeLeverage();
        logInitialization();
    }

    public void start() {
        if (running) {
            LOGGER.warning("Trading bot is already running");
            return;
        }
        new Thread(this::run).start();
    }

    public void stop() {
        running = false;
        if (isInLongPosition()) {
            exitLongPosition();
        }
    }

    public String getStatus() {
        return running ? "Running, Position: " + (positionStatus != null ? positionStatus : "None") : "Stopped";
    }

    public void updateConfig(TradingConfig newConfig) {
        this.config = newConfig;
        initializeLeverage();
        LOGGER.info("Configuration updated");
    }

    public void setDynamicLeverage(int newLeverage) {
        if (newLeverage < 1 || newLeverage > 125) { // Binance Futures max leverage
            LOGGER.severe("Invalid leverage value: " + newLeverage);
            throw new IllegalArgumentException("Leverage must be between 1 and 125");
        }
        this.currentLeverage = newLeverage;
        initializeLeverage();
        LOGGER.info(String.format("Dynamic leverage set to %dx", newLeverage));
    }

    public void enableSentimentAnalysis(boolean enable) {
        this.sentimentEnabled = enable;
        LOGGER.info("Sentiment analysis " + (enable ? "enabled" : "disabled"));
    }

    private void initializeLeverage() {
        try {
            exchangeService.setLeverage(config.getSymbol(), currentLeverage);
            LOGGER.info(String.format("Leverage set to %dx for %s", currentLeverage, config.getSymbol()));
        } catch (Exception e) {
            LOGGER.severe("Failed to set leverage: " + e.getMessage());
            throw new RuntimeException("Leverage initialization failed", e);
        }
    }

    private void logInitialization() {
        LOGGER.info(String.format("Bot initialized for longing %s with %dx leverage, trailing stop: %.2f%%",
                config.getSymbol(), currentLeverage, config.getTrailingStopPercent()));
    }

    private void run() {
        running = true;
        while (running) {
            try {
                processTradingCycle();
                Thread.sleep(CHECK_INTERVAL_SECONDS * 1000);
            } catch (InterruptedException e) {
                LOGGER.severe("Trading loop interrupted");
                Thread.currentThread().interrupt();
                running = false;
            } catch (Exception e) {
                LOGGER.severe("Error in trading cycle: " + e.getMessage());
                sleepSafely();
            }
        }
    }

    private void processTradingCycle() {
        double currentPrice = exchangeService.getCurrentPrice(config.getSymbol());
        trailingStopTracker.updateTrailingStop(currentPrice);

        if (isInLongPosition() && shouldExitPosition(currentPrice)) {
            exitLongPosition();
            return;
        }

        MarketData marketData = fetchMarketData();
        if (marketData == null) {
            return;
        }

        logMarketData(currentPrice, marketData);
        if (!isInLongPosition() && isEntrySignalValid(marketData)) {
            enterLongPosition();
        }
    }

    private boolean isInLongPosition() {
        return "long".equals(positionStatus);
    }

    private boolean shouldExitPosition(double currentPrice) {
        return trailingStopTracker.checkTrailingStop(currentPrice) ||
               exitConditions.stream().anyMatch(PositionExitCondition::shouldExit);
    }

    private MarketData fetchMarketData() {
        IndicatorValues dailyIndicators = indicatorCalculator.computeIndicators("1d", config.getSymbol());
        IndicatorValues weeklyIndicators = indicatorCalculator.computeIndicators("1w", config.getSymbol());
        if (dailyIndicators == null || weeklyIndicators == null) {
            LOGGER.warning("Insufficient data for indicators");
            return null;
        }
        return new MarketData(dailyIndicators, weeklyIndicators);
    }

    private void logMarketData(double price, MarketData marketData) {
        LOGGER.info(String.format("Price: %.2f, Daily RSI: %.2f, Daily MACD: %.2f, Daily Signal: %.2f, " +
                        "Daily Lower BB: %.2f, Daily Upper BB: %.2f, Weekly RSI: %.2f, Highest Price: %.2f",
                price, marketData.dailyIndicators.getRsi(), marketData.dailyIndicators.getMacd(),
                marketData.dailyIndicators.getSignal(), marketData.dailyIndicators.getLowerBand(),
                marketData.dailyIndicators.getUpperBand(), marketData.weeklyIndicators.getRsi(),
                trailingStopTracker.getHighestPrice()));
    }

    private boolean isEntrySignalValid(MarketData marketData) {
        double currentPrice = exchangeService.getCurrentPrice(config.getSymbol());
        boolean technicalSignal = marketData.dailyIndicators.getRsi() <= config.getRsiOversoldThreshold() &&
                                 marketData.dailyIndicators.getMacd() > marketData.dailyIndicators.getSignal() &&
                                 currentPrice <= marketData.dailyIndicators.getLowerBand() * 1.01 &&
                                 marketData.weeklyIndicators.getRsi() < config.getRsiOverboughtThreshold();
        if (sentimentEnabled) {
            return technicalSignal && sentimentAnalyzer.isPositiveSentiment(config.getSymbol());
        }
        return technicalSignal;
    }

    private void enterLongPosition() {
        double price = exchangeService.getCurrentPrice(config.getSymbol());
        double requiredMargin = config.getTradeAmount() * price / currentLeverage;

        if (exchangeService.getMarginBalance() < requiredMargin) {
            LOGGER.warning(String.format("Insufficient margin balance (USDT) to buy %.4f %s with %dx leverage",
                    config.getTradeAmount(), config.getSymbol(), currentLeverage));
            return;
        }

        try {
            exchangeService.enterLongPosition(config.getSymbol(), config.getTradeAmount());
            LOGGER.info(String.format("Entered long: Bought %.4f %s at %.2f with %dx leverage",
                    config.getTradeAmount(), config.getSymbol(), price, currentLeverage));
            positionStatus = "long";
            entryPrice = price;
            trailingStopTracker.initializeTrailingStop(price);
        } catch (Exception e) {
            LOGGER.severe("Failed to enter long position: " + e.getMessage());
        }
    }

    private void exitLongPosition() {
        double price = exchangeService.getCurrentPrice(config.getSymbol());
        String baseCurrency = config.getSymbol().replace("USDT", "");

        try {
            exchangeService.exitLongPosition(config.getSymbol(), config.getTradeAmount());
            double profit = (price - entryPrice) * config.getTradeAmount() * currentLeverage;
            LOGGER.info(String.format("Exited long: Sold %.4f %s at %.2f with %dx leverage, Profit: %.2f",
                    config.getTradeAmount(), config.getSymbol(), price, currentLeverage, profit));
            positionStatus = null;
            entryPrice = 0.0;
            trailingStopTracker.reset();
        } catch (Exception e) {
            LOGGER.severe("Failed to exit long position: " + e.getMessage());
        }
    }

    private void sleepSafely() {
        try {
            Thread.sleep(CHECK_INTERVAL_SECONDS * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class MarketData {
        final IndicatorValues dailyIndicators;
        final IndicatorValues weeklyIndicators;

        MarketData(IndicatorValues dailyIndicators, IndicatorValues weeklyIndicators) {
            this.dailyIndicators = dailyIndicators;
            this.weeklyIndicators = weeklyIndicators;
        }
    }
}