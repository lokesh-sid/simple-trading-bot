package tradingbot.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tradingbot.service.BinanceFuturesService.Candle;

public class PaperFuturesExchangeService implements FuturesExchangeService {
    private double marginBalance = 10000.0; // Simulated USDT balance
    private int leverage = 1;
    private Map<String, Double> positions = new HashMap<>(); // symbol -> position size
    private Map<String, Double> entryPrices = new HashMap<>(); // symbol -> entry price
    private double lastPrice = 50000.0; // Simulated price

    @Override
    public List<Candle> fetchOhlcv(String symbol, String timeframe, int limit) {
        return List.of(); // No-op for paper trading
    }

    @Override
    public double getCurrentPrice(String symbol) {
        return lastPrice; // Simulated price
    }

    @Override
    public double getMarginBalance() {
        return marginBalance;
    }

    @Override
    public void setLeverage(String symbol, int leverage) {
        this.leverage = leverage;
    }

    @Override
    public void enterLongPosition(String symbol, double tradeAmount) {
        double price = getCurrentPrice(symbol);
        double requiredMargin = tradeAmount * price / leverage;
        if (marginBalance < requiredMargin) throw new RuntimeException("Insufficient margin");
        marginBalance -= requiredMargin;
        positions.put(symbol + ":LONG", tradeAmount);
        entryPrices.put(symbol + ":LONG", price);
    }

    @Override
    public void exitLongPosition(String symbol, double tradeAmount) {
        double price = getCurrentPrice(symbol);
        Double entryPrice = entryPrices.get(symbol + ":LONG");
        if (entryPrice == null) return;
        double profit = (price - entryPrice) * tradeAmount * leverage;
        marginBalance += profit + (tradeAmount * entryPrice / leverage);
        positions.remove(symbol + ":LONG");
        entryPrices.remove(symbol + ":LONG");
    }

    @Override
    public void enterShortPosition(String symbol, double tradeAmount) {
        double price = getCurrentPrice(symbol);
        double requiredMargin = tradeAmount * price / leverage;
        if (marginBalance < requiredMargin) throw new RuntimeException("Insufficient margin");
        marginBalance -= requiredMargin;
        positions.put(symbol + ":SHORT", tradeAmount);
        entryPrices.put(symbol + ":SHORT", price);
    }

    @Override
    public void exitShortPosition(String symbol, double tradeAmount) {
        double price = getCurrentPrice(symbol);
        Double entryPrice = entryPrices.get(symbol + ":SHORT");
        if (entryPrice == null) return;
        double profit = (entryPrice - price) * tradeAmount * leverage;
        marginBalance += profit + (tradeAmount * entryPrice / leverage);
        positions.remove(symbol + ":SHORT");
        entryPrices.remove(symbol + ":SHORT");
    }
}
