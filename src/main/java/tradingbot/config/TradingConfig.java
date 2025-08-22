package tradingbot.config;

public class TradingConfig {
    private static final String DEFAULT_SYMBOL = "BTCUSDT";
    private static final double DEFAULT_TRADE_AMOUNT = 0.001;
    private static final int DEFAULT_LEVERAGE = 3;
    private static final double DEFAULT_TRAILING_STOP_PERCENT = 1.0;
    private static final int DEFAULT_LOOKBACK_PERIOD_RSI = 14;
    private static final double DEFAULT_RSI_OVERSOLD = 30.0;
    private static final double DEFAULT_RSI_OVERBOUGHT = 70.0;
    private static final int DEFAULT_MACD_FAST = 12;
    private static final int DEFAULT_MACD_SLOW = 26;
    private static final int DEFAULT_MACD_SIGNAL = 9;
    private static final int DEFAULT_BB_PERIOD = 20;
    private static final double DEFAULT_BB_STD = 2.0;
    private static final int DEFAULT_INTERVAL = 900;

    private final String symbol;
    private final double tradeAmount;
    private final int leverage;
    private final double trailingStopPercent;
    private final int lookbackPeriodRsi;
    private final double rsiOversoldThreshold;
    private final double rsiOverboughtThreshold;
    private final int macdFastPeriod;
    private final int macdSlowPeriod;
    private final int macdSignalPeriod;
    private final int bbPeriod;
    private final double bbStandardDeviation;
    private final int interval;

    public TradingConfig() {
        this(DEFAULT_SYMBOL, DEFAULT_TRADE_AMOUNT, DEFAULT_LEVERAGE, DEFAULT_TRAILING_STOP_PERCENT,
                DEFAULT_LOOKBACK_PERIOD_RSI, DEFAULT_RSI_OVERSOLD, DEFAULT_RSI_OVERBOUGHT,
                DEFAULT_MACD_FAST, DEFAULT_MACD_SLOW, DEFAULT_MACD_SIGNAL, DEFAULT_BB_PERIOD,
                DEFAULT_BB_STD, DEFAULT_INTERVAL);
    }

    public TradingConfig(String symbol, double tradeAmount, int leverage, double trailingStopPercent,
                        int lookbackPeriodRsi, double rsiOversoldThreshold, double rsiOverboughtThreshold,
                        int macdFastPeriod, int macdSlowPeriod, int macdSignalPeriod, int bbPeriod,
                        double bbStandardDeviation, int interval) {
        this.symbol = symbol;
        this.tradeAmount = tradeAmount;
        this.leverage = leverage;
        this.trailingStopPercent = trailingStopPercent;
        this.lookbackPeriodRsi = lookbackPeriodRsi;
        this.rsiOversoldThreshold = rsiOversoldThreshold;
        this.rsiOverboughtThreshold = rsiOverboughtThreshold;
        this.macdFastPeriod = macdFastPeriod;
        this.macdSlowPeriod = macdSlowPeriod;
        this.macdSignalPeriod = macdSignalPeriod;
        this.bbPeriod = bbPeriod;
        this.bbStandardDeviation = bbStandardDeviation;
        this.interval = interval;
    }

    public String getSymbol() { return symbol; }
    public double getTradeAmount() { return tradeAmount; }
    public int getLeverage() { return leverage; }
    public double getTrailingStopPercent() { return trailingStopPercent; }
    public int getLookbackPeriodRsi() { return lookbackPeriodRsi; }
    public double getRsiOversoldThreshold() { return rsiOversoldThreshold; }
    public double getRsiOverboughtThreshold() { return rsiOverboughtThreshold; }
    public int getMacdFastPeriod() { return macdFastPeriod; }
    public int getMacdSlowPeriod() { return macdSlowPeriod; }
    public int getMacdSignalPeriod() { return macdSignalPeriod; }
    public int getBbPeriod() { return bbPeriod; }
    public double getBbStandardDeviation() { return bbStandardDeviation; }
    public int getInterval() { return interval; }
}