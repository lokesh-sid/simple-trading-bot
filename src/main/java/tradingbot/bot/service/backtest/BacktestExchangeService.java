package tradingbot.bot.service.backtest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tradingbot.bot.service.BinanceFuturesService.Candle;
import tradingbot.bot.service.FuturesExchangeService;

public class BacktestExchangeService implements FuturesExchangeService {
    private static final Logger logger = LoggerFactory.getLogger(BacktestExchangeService.class);

    private double marginBalance = 10000.0;
    private int leverage = 1;
    private Map<String, Double> positions = new HashMap<>();
    private Map<String, Double> entryPrices = new HashMap<>();
    
    private Candle currentCandle;
    private long currentTime;
    private List<Candle> history;
    private int currentIndex;
    
    private final long latencyMs;
    private final double slippagePercent;
    private final double takerFeeRate;
    
    private Queue<PendingOrder> pendingOrders = new LinkedList<>();

    private static final String LONG_SUFFIX = ":LONG";
    private static final String SHORT_SUFFIX = ":SHORT";

    public BacktestExchangeService(long latencyMs, double slippagePercent, double takerFeeRate) {
        this.latencyMs = latencyMs;
        this.slippagePercent = slippagePercent;
        this.takerFeeRate = takerFeeRate;
    }

    public void setMarketContext(List<Candle> history, int currentIndex) {
        this.history = history;
        this.currentIndex = currentIndex;
        this.currentCandle = history.get(currentIndex);
        this.currentTime = currentCandle.getCloseTime(); 
        checkLiquidations();
    }

    private void checkLiquidations() {
        double low = currentCandle.getLow().doubleValue();
        double high = currentCandle.getHigh().doubleValue();
        List<String> liquidatedKeys = collectLiquidatedKeys(low, high);
        for (String key : liquidatedKeys) {
            positions.remove(key);
            entryPrices.remove(key);
            logger.info("LIQUIDATION on {} at {}", key, (key.endsWith(LONG_SUFFIX) ? low : high));
        }
    }

    private List<String> collectLiquidatedKeys(double low, double high) {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Double> entrySet : positions.entrySet()) {
            String key = entrySet.getKey();
            Double entryPrice = entryPrices.get(key);
            if (entryPrice == null) {
                continue;
            }
            if (key.endsWith(LONG_SUFFIX)) {
                double liquidationPrice = entryPrice * (1 - (1.0 / leverage));
                if (low <= liquidationPrice) {
                    keys.add(key);
                }
            } else if (key.endsWith(SHORT_SUFFIX)) {
                double liquidationPrice = entryPrice * (1 + (1.0 / leverage));
                if (high >= liquidationPrice) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }
    
    public void processPendingOrders() {
        while (!pendingOrders.isEmpty()) {
            PendingOrder order = pendingOrders.peek();
            if (currentTime >= order.executionTime) {
                pendingOrders.poll();
                executeOrder(order);
            } else {
                break;
            }
        }
    }

    private void executeOrder(PendingOrder order) {
        double price = calculateExecutionPrice(order);
        double tradeValue = order.amount * price;
        double fee = tradeValue * takerFeeRate;

        if (order.isEntry) {
            handleEntry(order, price, tradeValue, fee);
        } else {
            handleExit(order, price, fee);
        }
    }

    private double calculateExecutionPrice(PendingOrder order) {
        double price = currentCandle.getClose().doubleValue();
        // 1. Apply Slippage
        if (order.type == OrderType.BUY) {
            return price * (1 + slippagePercent);
        } else {
            return price * (1 - slippagePercent);
        }
    }

    private void handleEntry(PendingOrder order, double price, double tradeValue, double fee) {
        // ENTRY LOGIC
        double initialMargin = tradeValue / leverage;
        if (marginBalance >= (initialMargin + fee)) {
            marginBalance -= (initialMargin + fee);
            String key = order.symbol + (order.type == OrderType.BUY ? LONG_SUFFIX : SHORT_SUFFIX);
            positions.put(key, order.amount);
            entryPrices.put(key, price);
        }
    }

    private void handleExit(PendingOrder order, double price, double fee) {
        // EXIT LOGIC
        String key = order.symbol + (order.type == OrderType.SELL ? LONG_SUFFIX : SHORT_SUFFIX);
        Double entryPrice = entryPrices.get(key);
        if (entryPrice != null) {
            double pnl;
            double initialMargin = (order.amount * entryPrice) / leverage;

            if (order.type == OrderType.SELL) { // Closing Long
                pnl = (price - entryPrice) * order.amount;
            } else { // Closing Short
                pnl = (entryPrice - price) * order.amount;
            }

            marginBalance += (initialMargin + pnl - fee);
            positions.remove(key);
            entryPrices.remove(key);
        }
    }

    @Override
    public List<Candle> fetchOhlcv(String symbol, String timeframe, int limit) {
        int start = Math.max(0, currentIndex - limit + 1);
        return new ArrayList<>(history.subList(start, currentIndex + 1));
    }

    @Override
    public double getCurrentPrice(String symbol) {
        return currentCandle.getClose().doubleValue();
    }

    @Override
    public double getMarginBalance() {
        return marginBalance;
    }

    @Override
    public void setLeverage(String symbol, int leverage) {
        this.leverage = leverage;
    }

    private double normalizeQuantity(double quantity) {
        double stepSize = 0.001; 
        return Math.floor(quantity / stepSize) * stepSize;
    }

    @Override
    public void enterLongPosition(String symbol, double tradeAmount) {
        pendingOrders.add(new PendingOrder(symbol, normalizeQuantity(tradeAmount), OrderType.BUY, true, currentTime + latencyMs));
    }

    @Override
    public void exitLongPosition(String symbol, double tradeAmount) {
        pendingOrders.add(new PendingOrder(symbol, normalizeQuantity(tradeAmount), OrderType.SELL, false, currentTime + latencyMs));
    }

    @Override
    public void enterShortPosition(String symbol, double tradeAmount) {
        pendingOrders.add(new PendingOrder(symbol, normalizeQuantity(tradeAmount), OrderType.SELL, true, currentTime + latencyMs));
    }

    @Override
    public void exitShortPosition(String symbol, double tradeAmount) {
        pendingOrders.add(new PendingOrder(symbol, normalizeQuantity(tradeAmount), OrderType.BUY, false, currentTime + latencyMs));
    }
    
    private static class PendingOrder {
        String symbol;
        double amount;
        OrderType type;
        boolean isEntry;
        long executionTime;

        public PendingOrder(String symbol, double amount, OrderType type, boolean isEntry, long executionTime) {
            this.symbol = symbol;
            this.amount = amount;
            this.type = type;
            this.isEntry = isEntry;
            this.executionTime = executionTime;
        }
    }
    
    private enum OrderType { BUY, SELL }
}
