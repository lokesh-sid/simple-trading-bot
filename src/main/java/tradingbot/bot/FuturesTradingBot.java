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
        public FuturesExchangeService exchangeService;
        public IndicatorCalculator indicatorCalculator;
        public TrailingStopTracker trailingStopTracker;
        public SentimentAnalyzer sentimentAnalyzer;
        public List<PositionExitCondition> exitConditions;
        public TradingConfig config;
        public TradeDirection direction;
        public boolean skipLeverageInit;

        private BotParams() {}

        public static class Builder {
            private FuturesExchangeService exchangeService;
            private IndicatorCalculator indicatorCalculator;
            private TrailingStopTracker trailingStopTracker;
            private SentimentAnalyzer sentimentAnalyzer;
            private List<PositionExitCondition> exitConditions;
            private TradingConfig config;
            private TradeDirection direction;
            private boolean skipLeverageInit;

            public Builder exchangeService(FuturesExchangeService exchangeService) {
                this.exchangeService = exchangeService;
    // Usage example:
    // FuturesTradingBot.BotParams params = new FuturesTradingBot.BotParams.Builder()
    //     .exchangeService(exchangeService)
    //     .indicatorCalculator(indicatorCalculator)
    //     .trailingStopTracker(trailingStopTracker)
    //     .sentimentAnalyzer(sentimentAnalyzer)
    //     .exitConditions(exitConditions)
    //     .config(config)
    //     .direction(direction)
    //     .skipLeverageInit(false)
    //     .build();
    // FuturesTradingBot bot = new FuturesTradingBot(params);
            public Builder exitConditions(List<PositionExitCondition> exitConditions) {
                this.exitConditions = exitConditions;
                return this;
            }

            public Builder config(TradingConfig config) {
                this.config = config;
                return this;
            }

            public Builder direction(TradeDirection direction) {
                this.direction = direction;
                return this;
            }

            public Builder skipLeverageInit(boolean skipLeverageInit) {
                this.skipLeverageInit = skipLeverageInit;
                return this;
            }

            public Builder indicatorCalculator(IndicatorCalculator indicatorCalculator) {
                this.indicatorCalculator = indicatorCalculator;
                return this;
            }

            public Builder trailingStopTracker(TrailingStopTracker trailingStopTracker) {
                this.trailingStopTracker = trailingStopTracker;
                return this;
            }

            public Builder tradeDirection(TradeDirection direction) {
                this.direction = direction;
                return this;
            }

            public Builder sentimentAnalyzer(SentimentAnalyzer sentimentAnalyzer) {
                this.sentimentAnalyzer = sentimentAnalyzer;
                return this;
            }

            public Builder testMode(boolean testMode) {
                this.skipLeverageInit = testMode;
                return this;
            }

            public BotParams build() {
                BotParams params = new BotParams();
                params.exchangeService = this.exchangeService;
                params.indicatorCalculator = this.indicatorCalculator;
                params.trailingStopTracker = this.trailingStopTracker;
                params.sentimentAnalyzer = this.sentimentAnalyzer;
                params.exitConditions = this.exitConditions;
                params.config = this.config;
                params.direction = this.direction;
                params.skipLeverageInit = this.skipLeverageInit;
                return params;
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
