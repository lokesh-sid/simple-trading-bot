package tradingbot.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import tradingbot.config.TradingConfig;
import tradingbot.service.FuturesExchangeService;
import tradingbot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.strategy.calculator.IndicatorCalculator;
import tradingbot.strategy.calculator.IndicatorValues;
import tradingbot.strategy.exit.PositionExitCondition;
import tradingbot.strategy.tracker.TrailingStopTracker;

public class FuturesTradingBot implements TradingAgent {
    private final Logger logger = Logger.getLogger(FuturesTradingBot.class.getName());
    private static final int CHECK_INTERVAL_SECONDS = 900; // 15 minutes

    private FuturesExchangeService exchangeService;
    private IndicatorCalculator indicatorCalculator;
    private TrailingStopTracker trailingStopTracker;
    private SentimentAnalyzer sentimentAnalyzer;
    private List<PositionExitCondition> exitConditions;
    private TradingConfig config;
    private TradeDirection direction;
    private String positionStatus;
    private double entryPrice;
    private volatile boolean running;
    private volatile boolean sentimentEnabled;
    private int currentLeverage;

    // Public getters for accessibility
    public FuturesExchangeService getExchangeService() { return exchangeService; }
    public IndicatorCalculator getIndicatorCalculator() { return indicatorCalculator; }
    public TrailingStopTracker getTrailingStopTracker() { return trailingStopTracker; }
    public SentimentAnalyzer getSentimentAnalyzer() { return sentimentAnalyzer; }
    public List<PositionExitCondition> getExitConditions() { return exitConditions; }
    public TradingConfig getConfig() { return config; }
    public TradeDirection getDirection() { return direction; }
    public String getPositionStatus() { return positionStatus; }
    public double getEntryPrice() { return entryPrice; }
    public boolean isRunning() { return running; }
    public boolean isSentimentEnabled() { return sentimentEnabled; }
    public int getCurrentLeverage() { return currentLeverage; }

    public FuturesTradingBot(BotParams params) {
        this.exchangeService = params.exchangeService;
        this.indicatorCalculator = params.indicatorCalculator;
        this.trailingStopTracker = params.trailingStopTracker;
        this.sentimentAnalyzer = params.sentimentAnalyzer;
        this.exitConditions = params.exitConditions;
        this.config = params.config;
        this.direction = params.direction;
        this.positionStatus = null;
        this.entryPrice = 0.0;
        this.running = false;
        this.sentimentEnabled = false;
        this.currentLeverage = config.getLeverage();
        if (!params.skipLeverageInit) {
            initializeLeverage();
        }
        logInitialization();
    }

    public static class BotParams {
        private final FuturesExchangeService exchangeService;
        private final IndicatorCalculator indicatorCalculator;
        private final TrailingStopTracker trailingStopTracker;
        private final SentimentAnalyzer sentimentAnalyzer;
        private final List<PositionExitCondition> exitConditions;
        private final TradingConfig config;
        private final TradeDirection direction;
        private final boolean skipLeverageInit;

        private BotParams(Builder builder) {
            this.exchangeService = builder.exchangeService;
            this.indicatorCalculator = builder.indicatorCalculator;
            this.trailingStopTracker = builder.trailingStopTracker;
            this.sentimentAnalyzer = builder.sentimentAnalyzer;
            this.exitConditions = List.copyOf(builder.exitConditions); // Defensive copy
            this.config = builder.config;
            this.direction = builder.direction;
            this.skipLeverageInit = builder.skipLeverageInit;
        }

        public FuturesExchangeService getExchangeService() {
            return exchangeService;
        }

        public IndicatorCalculator getIndicatorCalculator() {
            return indicatorCalculator;
        }

        public TrailingStopTracker getTrailingStopTracker() {
            return trailingStopTracker;
        }

        public SentimentAnalyzer getSentimentAnalyzer() {
            return sentimentAnalyzer;
        }

        public List<PositionExitCondition> getExitConditions() {
            return exitConditions; // Already immutable from defensive copy
        }

        public TradingConfig getConfig() {
            return config;
        }

        public TradeDirection getDirection() {
            return direction;
        }

        public boolean isSkipLeverageInit() {
            return skipLeverageInit;
        }

        public static class Builder {
            private FuturesExchangeService exchangeService;
            private IndicatorCalculator indicatorCalculator;
            private TrailingStopTracker trailingStopTracker;
            private SentimentAnalyzer sentimentAnalyzer;
            private List<PositionExitCondition> exitConditions;
            private TradingConfig config;
            private TradeDirection direction;
            private boolean skipLeverageInit = false; // Default value

            /**
             * Sets the futures exchange service for executing trades.
             * @param exchangeService the exchange service (required)
             * @return this builder
             * @throws IllegalArgumentException if exchangeService is null
             */
            public Builder exchangeService(FuturesExchangeService exchangeService) {
                this.exchangeService = requireNonNull(exchangeService, "Exchange service cannot be null");
                return this;
            }

            /**
             * Sets the indicator calculator for technical analysis.
             * @param indicatorCalculator the indicator calculator (required)
             * @return this builder
             * @throws IllegalArgumentException if indicatorCalculator is null
             */
            public Builder indicatorCalculator(IndicatorCalculator indicatorCalculator) {
                this.indicatorCalculator = requireNonNull(indicatorCalculator, "Indicator calculator cannot be null");
                return this;
            }

            /**
             * Sets the trailing stop tracker for risk management.
             * @param trailingStopTracker the trailing stop tracker (required)
             * @return this builder
             * @throws IllegalArgumentException if trailingStopTracker is null
             */
            public Builder trailingStopTracker(TrailingStopTracker trailingStopTracker) {
                this.trailingStopTracker = requireNonNull(trailingStopTracker, "Trailing stop tracker cannot be null");
                return this;
            }

            /**
             * Sets the sentiment analyzer for market sentiment analysis.
             * @param sentimentAnalyzer the sentiment analyzer (required)
             * @return this builder
             * @throws IllegalArgumentException if sentimentAnalyzer is null
             */
            public Builder sentimentAnalyzer(SentimentAnalyzer sentimentAnalyzer) {
                this.sentimentAnalyzer = requireNonNull(sentimentAnalyzer, "Sentiment analyzer cannot be null");
                return this;
            }

            /**
             * Sets the exit conditions for position management.
             * @param exitConditions the list of exit conditions (required, non-empty)
             * @return this builder
             * @throws IllegalArgumentException if exitConditions is null or empty
             */
            public Builder exitConditions(List<PositionExitCondition> exitConditions) {
                requireNonNull(exitConditions, "Exit conditions cannot be null");
                if (exitConditions.isEmpty()) {
                    throw new IllegalArgumentException("Exit conditions cannot be empty");
                }
                this.exitConditions = new ArrayList<>(exitConditions); // Defensive copy
                return this;
            }

            /**
             * Sets the trading configuration.
             * @param config the trading configuration (required)
             * @return this builder
             * @throws IllegalArgumentException if config is null
             */
            public Builder config(TradingConfig config) {
                this.config = requireNonNull(config, "Trading config cannot be null");
                return this;
            }

            /**
             * Sets the trade direction (LONG or SHORT).
             * @param direction the trade direction (required)
             * @return this builder
             * @throws IllegalArgumentException if direction is null
             */
            public Builder tradeDirection(TradeDirection direction) {
                this.direction = requireNonNull(direction, "Trade direction cannot be null");
                return this;
            }

            /**
             * Sets whether to skip leverage initialization. 
             * Useful for testing environments or when leverage is managed externally.
             * @param skipLeverageInit true to skip leverage initialization, false otherwise (default: false)
             * @return this builder
             */
            public Builder skipLeverageInit(boolean skipLeverageInit) {
                this.skipLeverageInit = skipLeverageInit;
                return this;
            }

            /**
             * Convenience method for test mode - equivalent to skipLeverageInit(true).
             * @return this builder
             */
            public Builder forTesting() {
                return skipLeverageInit(true);
            }

            /**
             * Validates all required fields and builds the BotParams instance.
             * @return the configured BotParams
             * @throws IllegalStateException if any required field is missing
             */
            public BotParams build() {
                validateRequiredFields();
                return new BotParams(this);
            }

            private void validateRequiredFields() {
                StringBuilder missing = new StringBuilder();
                
                if (exchangeService == null) missing.append("exchangeService, ");
                if (indicatorCalculator == null) missing.append("indicatorCalculator, ");
                if (trailingStopTracker == null) missing.append("trailingStopTracker, ");
                if (sentimentAnalyzer == null) missing.append("sentimentAnalyzer, ");
                if (exitConditions == null || exitConditions.isEmpty()) missing.append("exitConditions, ");
                if (config == null) missing.append("config, ");
                if (direction == null) missing.append("tradeDirection, ");
                
                if (!missing.isEmpty()) {
                    missing.setLength(missing.length() - 2); // Remove trailing ", "
                    throw new IllegalStateException("Missing required fields: " + missing);
                }
            }

            private static <T> T requireNonNull(T obj, String message) {
                if (obj == null) {
                    throw new IllegalArgumentException(message);
                }
                return obj;
            }
        }
    }

    @Override
    public void start() {
        if (running) {
            logger.warning("Trading bot is already running");
            return;
        }
        new Thread(this::run).start();
    }

    @Override
    public void stop() {
        running = false;
        if (isInPosition()) {
            exitPosition();
        }
    }

    @Override
    public void processMarketData(Object marketData) {
        if (marketData instanceof MarketData) {
            MarketData md = (MarketData) marketData;
            logMarketData(exchangeService.getCurrentPrice(config.getSymbol()), md);
            if (!isInPosition() && isEntrySignalValid(md)) {
                enterPosition();
            }
        } else {
            logger.warning("Unsupported market data type for agent");
        }
    }

    @Override
    public void executeTrade() {
        MarketData marketData = fetchMarketData();
        if (marketData != null && !isInPosition() && isEntrySignalValid(marketData)) {
            enterPosition();
        }
    }

    public String getStatus() {
        return running ? "Running, Direction: " + direction + ", Position: " + (positionStatus != null ? positionStatus : "None") : "Stopped";
    }

    public void updateConfig(TradingConfig newConfig) {
        this.config = newConfig;
        initializeLeverage();
    logger.info("Configuration updated");
    }

    public void setDynamicLeverage(int newLeverage) {
        if (newLeverage < 1 || newLeverage > 125) {
            logger.severe(() -> "Invalid leverage value: " + newLeverage);
            throw new IllegalArgumentException("Leverage must be between 1 and 125");
        }
        this.currentLeverage = newLeverage;
        initializeLeverage();
        logger.info(() -> String.format("Dynamic leverage set to %dx", newLeverage));
    }

    public void enableSentimentAnalysis(boolean enable) {
        this.sentimentEnabled = enable;
        logger.info(() -> "Sentiment analysis  " + (enable ? "enabled" : "disabled"));
    }

    private void initializeLeverage() {
        try {
            exchangeService.setLeverage(config.getSymbol(), currentLeverage);
            logger.info(() -> String.format("Leverage set to %dx for %s", currentLeverage, config.getSymbol()));
        } catch (Exception e) {
            logger.severe("Failed to set leverage: " + e.getMessage());
            throw new RuntimeException("Leverage initialization failed", e);
        }
    }

    private void logInitialization() {
        logger.info(() -> String.format("Bot initialized for %s %s with %dx leverage, trailing stop: %.2f%%",
                direction == TradeDirection.LONG ? "longing" : "shorting",
                config.getSymbol(), currentLeverage, config.getTrailingStopPercent()));
    }

    private void run() {
        running = true;
        while (running) {
            try {
                processTradingCycle();
                Thread.sleep(CHECK_INTERVAL_SECONDS * 1000);
            } catch (InterruptedException e) {
                logger.severe("Trading loop interrupted");
                Thread.currentThread().interrupt();
                running = false;
            } catch (Exception e) {
                logger.severe("Error in trading cycle: " + e.getMessage());
                sleepSafely();
            }
        }
    }

    private void processTradingCycle() {
        double currentPrice = exchangeService.getCurrentPrice(config.getSymbol());
        trailingStopTracker.updateTrailingStop(currentPrice);

        if (isInPosition() && shouldExitPosition(currentPrice)) {
            exitPosition();
            return;
        }

        MarketData marketData = fetchMarketData();
        if (marketData == null) {
            return;
        }

        logMarketData(currentPrice, marketData);
        if (!isInPosition() && isEntrySignalValid(marketData)) {
            enterPosition();
        }
    }

    private boolean isInPosition() {
        if (direction == TradeDirection.LONG) {
            return "long".equals(positionStatus);
        } else {
            return "short".equals(positionStatus);
        }
    }

    private boolean shouldExitPosition(double currentPrice) {
        return trailingStopTracker.checkTrailingStop(currentPrice) ||
               exitConditions.stream().anyMatch(PositionExitCondition::shouldExit);
    }

    private MarketData cachedMarketData = null;
    private MarketData fetchMarketData() {
        if (cachedMarketData != null) {
            return cachedMarketData;
        }
        IndicatorValues dailyIndicators = indicatorCalculator.computeIndicators("1d", config.getSymbol());
        IndicatorValues weeklyIndicators = indicatorCalculator.computeIndicators("1w", config.getSymbol());
        if (dailyIndicators == null || weeklyIndicators == null) {
            logger.warning("Insufficient data for indicators");
            return null;
        }
        cachedMarketData = new MarketData(dailyIndicators, weeklyIndicators);
        return cachedMarketData;
    }

    private void logMarketData(double price, MarketData marketData) {
        logger.info(() -> String.format("Price: %.2f, Daily RSI: %.2f, Daily MACD: %.2f, Daily Signal: %.2f, " +
                        "Daily Lower BB: %.2f, Daily Upper BB: %.2f, Weekly RSI: %.2f, Highest Price: %.2f",
                price, marketData.dailyIndicators.getRsi(), marketData.dailyIndicators.getMacd(),
                marketData.dailyIndicators.getSignal(), marketData.dailyIndicators.getLowerBand(),
                marketData.dailyIndicators.getUpperBand(), marketData.weeklyIndicators.getRsi(),
                trailingStopTracker.getHighestPrice()));
    }

    private boolean isEntrySignalValid(MarketData marketData) {
        double currentPrice = exchangeService.getCurrentPrice(config.getSymbol());
        boolean technicalSignal;
        if (direction == TradeDirection.LONG) {
            technicalSignal = marketData.dailyIndicators.getRsi() <= config.getRsiOversoldThreshold() &&
                             marketData.dailyIndicators.getMacd() > marketData.dailyIndicators.getSignal() &&
                             currentPrice <= marketData.dailyIndicators.getLowerBand() * 1.01 &&
                             marketData.weeklyIndicators.getRsi() < config.getRsiOverboughtThreshold();
            if (sentimentEnabled) {
                return technicalSignal && sentimentAnalyzer.isPositiveSentiment(config.getSymbol());
            }
            return technicalSignal;
        } else {
            technicalSignal = marketData.dailyIndicators.getRsi() >= config.getRsiOverboughtThreshold() &&
                             marketData.dailyIndicators.getMacd() < marketData.dailyIndicators.getSignal() &&
                             currentPrice >= marketData.dailyIndicators.getUpperBand() * 0.99 &&
                             marketData.weeklyIndicators.getRsi() > config.getRsiOversoldThreshold();
            if (sentimentEnabled) {
                return technicalSignal && sentimentAnalyzer.isNegativeSentiment(config.getSymbol());
            }
            return technicalSignal;
        }
    }

    private void enterPosition() {
        double price = exchangeService.getCurrentPrice(config.getSymbol());
        double requiredMargin = config.getTradeAmount() * price / currentLeverage;

        if (exchangeService.getMarginBalance() < requiredMargin) {
            logger.warning(() -> String.format("Insufficient margin balance (USDT) to %s %.4f %s with %dx leverage",
                    direction == TradeDirection.LONG ? "buy" : "sell",
                    config.getTradeAmount(), config.getSymbol(), currentLeverage));
            return;
        }

        try {
            if (direction == TradeDirection.LONG) {
                exchangeService.enterLongPosition(config.getSymbol(), config.getTradeAmount());
                logger.info(() ->String.format("Entered long: Bought %.4f %s at %.2f with %dx leverage",
                        config.getTradeAmount(), config.getSymbol(), price, currentLeverage));
                positionStatus = "long";
            } else {
                exchangeService.enterShortPosition(config.getSymbol(), config.getTradeAmount());
                logger.info(() ->String.format("Entered short: Sold %.4f %s at %.2f with %dx leverage",
                        config.getTradeAmount(), config.getSymbol(), price, currentLeverage));
                positionStatus = "short";
            }
            entryPrice = price;
            trailingStopTracker.initializeTrailingStop(price);
        } catch (Exception e) {
            logger.severe("Failed to enter " + direction.name().toLowerCase() + " position: " + e.getMessage());
        }
    }

    private void exitPosition() {
        double price = exchangeService.getCurrentPrice(config.getSymbol());
        try {
            if (direction == TradeDirection.LONG) {
                exchangeService.exitLongPosition(config.getSymbol(), config.getTradeAmount());
                double profit = (price - entryPrice) * config.getTradeAmount() * currentLeverage;
                logger.info(() -> String.format("Exited long: Sold %.4f %s at %.2f with %dx leverage, Profit: %.2f",
                        config.getTradeAmount(), config.getSymbol(), price, currentLeverage, profit));
            } else {
                exchangeService.exitShortPosition(config.getSymbol(), config.getTradeAmount());
                double profit = (entryPrice - price) * config.getTradeAmount() * currentLeverage;
                logger.info(() -> String.format("Exited short: Bought %.4f %s at %.2f with %dx leverage, Profit: %.2f",
                        config.getTradeAmount(), config.getSymbol(), price, currentLeverage, profit));
            }
            positionStatus = null;
            entryPrice = 0.0;
            trailingStopTracker.reset();
        } catch (Exception e) {
            logger.severe("Failed to exit " + direction.name().toLowerCase() + " position: " + e.getMessage());
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
