package tradingbot.bot.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class BinanceFuturesService implements FuturesExchangeService {
    private UMFuturesClientImpl futuresClient;
    private ObjectMapper objectMapper;

    public BinanceFuturesService(String apiKey, String apiSecret) {
        this.futuresClient = new UMFuturesClientImpl(apiKey, apiSecret);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<Candle> fetchOhlcv(String symbol, String timeframe, int limit) {
        try {
            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("symbol", symbol);
            parameters.put("interval", timeframe);
            parameters.put("limit", limit);
            String result = futuresClient.market().klines(parameters);
            ArrayNode candlesArray = (ArrayNode) objectMapper.readTree(result);
            List<Candle> candles = new LinkedList<>();
            for (int i = 0; i < candlesArray.size(); i++) {
                ArrayNode candleData = (ArrayNode) candlesArray.get(i);
                Candle candle = new Candle();
                candle.setOpenTime(candleData.get(0).asLong());
                candle.setOpen(new BigDecimal(candleData.get(1).asText()));
                candle.setHigh(new BigDecimal(candleData.get(2).asText()));
                candle.setLow(new BigDecimal(candleData.get(3).asText()));
                candle.setClose(new BigDecimal(candleData.get(4).asText()));
                candle.setVolume(new BigDecimal(candleData.get(5).asText()));
                candle.setCloseTime(candleData.get(6).asLong());
                candles.add(candle);
            }
            return candles;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch OHLCV data for " + symbol, e);
        }
    }

    @Override
    public double getCurrentPrice(String symbol) {
        try {
            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("symbol", symbol);
            String result = futuresClient.market().markPrice(parameters);
            ObjectNode markPrice = (ObjectNode) objectMapper.readTree(result);
            return markPrice.get("markPrice").asDouble();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch current price for " + symbol, e);
        }
    }

    @Override
    public double getMarginBalance() {
        try {
            String result = futuresClient.account().accountInformation(null);
            ObjectNode accountInfo = (ObjectNode) objectMapper.readTree(result);
            ArrayNode assets = (ArrayNode) accountInfo.get("assets");
            for (int i = 0; i < assets.size(); i++) {
                ObjectNode asset = (ObjectNode) assets.get(i);
                if ("USDT".equals(asset.get("asset").asText())) {
                    return asset.get("availableBalance").asDouble();
                }
            }
            return 0.0;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch margin balance", e);
        }
    }

    @Override
    public void setLeverage(String symbol, int leverage) {
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("symbol", symbol);
        parameters.put("leverage", leverage);
        futuresClient.account().changeInitialLeverage(parameters);
    }

    @Override
    public void enterLongPosition(String symbol, double tradeAmount) {
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("symbol", symbol);
        parameters.put("side", "BUY");
        parameters.put("positionSide", "LONG");
        parameters.put("type", "MARKET");
        parameters.put("quantity", tradeAmount);
        futuresClient.account().newOrder(parameters);
    }

    @Override
    public void enterShortPosition(String symbol, double tradeAmount) {
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("symbol", symbol);
        parameters.put("side", "SELL");
        parameters.put("positionSide", "SHORT");
        parameters.put("type", "MARKET");
        parameters.put("quantity", tradeAmount);
        futuresClient.account().newOrder(parameters);
    }

    @Override
    public void exitLongPosition(String symbol, double tradeAmount) {
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("symbol", symbol);
        parameters.put("side", "SELL");
        parameters.put("positionSide", "LONG");
        parameters.put("type", "MARKET");
        parameters.put("quantity", tradeAmount);
        futuresClient.account().newOrder(parameters);
    }

    @Override
    public void exitShortPosition(String symbol, double tradeAmount) {
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("symbol", symbol);
        parameters.put("side", "BUY");
        parameters.put("positionSide", "SHORT");
        parameters.put("type", "MARKET");
        parameters.put("quantity", tradeAmount);
        futuresClient.account().newOrder(parameters);
    }

    public static class Candle {
        private long openTime;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal volume;
        private long closeTime;

        public long getOpenTime() { return openTime; }
        public void setOpenTime(long openTime) { this.openTime = openTime; }
        public BigDecimal getOpen() { return open; }
        public void setOpen(BigDecimal open) { this.open = open; }
        public BigDecimal getHigh() { return high; }
        public void setHigh(BigDecimal high) { this.high = high; }
        public BigDecimal getLow() { return low; }
        public void setLow(BigDecimal low) { this.low = low; }
        public BigDecimal getClose() { return close; }
        public void setClose(BigDecimal close) { this.close = close; }
        public BigDecimal getVolume() { return volume; }
        public void setVolume(BigDecimal volume) { this.volume = volume; }
        public long getCloseTime() { return closeTime; }
        public void setCloseTime(long closeTime) { this.closeTime = closeTime; }
    }
}