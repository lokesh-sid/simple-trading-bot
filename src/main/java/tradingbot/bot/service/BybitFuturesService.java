package tradingbot.bot.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Bybit Futures Service - Alternative to Binance
 * Implements FuturesExchangeService for Bybit derivatives trading
 * 
 * Uses Bybit V5 REST API directly for maximum compatibility
 * 
 * Features:
 * - USDT Perpetual Futures trading
 * - Up to 100x leverage support
 * - Market orders
 * - Position management
 * - Real-time market data
 */
public class BybitFuturesService implements FuturesExchangeService {
    
    private static final Logger logger = LoggerFactory.getLogger(BybitFuturesService.class);
    
    private static final String MAINNET_URL = "https://api.bybit.com";
    private static final String TESTNET_URL = "https://api-testnet.bybit.com";
    
    private final String apiKey;
    private final String apiSecret;
    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public BybitFuturesService(String apiKey, String apiSecret) {
        this(apiKey, apiSecret, MAINNET_URL);
    }
    
    public BybitFuturesService(String apiKey, String apiSecret, String baseUrl) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        
        logger.info("Bybit Futures Service initialized (URL: {})", baseUrl);
    }
    
    @Override
    public List<BinanceFuturesService.Candle> fetchOhlcv(String symbol, String timeframe, int limit) {
        try {
            logger.debug("Fetching OHLCV for {} (interval: {}, limit: {})", symbol, timeframe, limit);
            
            String interval = convertTimeframe(timeframe);
            String url = String.format("%s/v5/market/kline?category=linear&symbol=%s&interval=%s&limit=%d",
                baseUrl, symbol, interval, limit);
            
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            
            List<BinanceFuturesService.Candle> candles = new LinkedList<>();
            
            if (root.has("result") && root.get("result").has("list")) {
                JsonNode list = root.get("result").get("list");
                
                // Bybit returns in reverse order, so iterate backwards
                for (int i = list.size() - 1; i >= 0; i--) {
                    JsonNode kline = list.get(i);
                    BinanceFuturesService.Candle candle = new BinanceFuturesService.Candle();
                    candle.setOpenTime(kline.get(0).asLong());
                    candle.setOpen(new BigDecimal(kline.get(1).asText()));
                    candle.setHigh(new BigDecimal(kline.get(2).asText()));
                    candle.setLow(new BigDecimal(kline.get(3).asText()));
                    candle.setClose(new BigDecimal(kline.get(4).asText()));
                    candle.setVolume(new BigDecimal(kline.get(5).asText()));
                    candle.setCloseTime(kline.get(0).asLong() + getIntervalMillis(interval));
                    candles.add(candle);
                }
            }
            
            logger.debug("Fetched {} candles for {}", candles.size(), symbol);
            return candles;
            
        } catch (Exception e) {
            logger.error("Failed to fetch OHLCV for {}", symbol, e);
            throw new RuntimeException("Failed to fetch OHLCV for " + symbol, e);
        }
    }
    
    @Override
    public double getCurrentPrice(String symbol) {
        try {
            logger.debug("Fetching current price for {}", symbol);
            
            String url = String.format("%s/v5/market/tickers?category=linear&symbol=%s",
                baseUrl, symbol);
            
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            
            if (root.has("result") && root.get("result").has("list")) {
                JsonNode list = root.get("result").get("list");
                if (list.size() > 0) {
                    double price = list.get(0).get("lastPrice").asDouble();
                    logger.debug("Current price for {}: {}", symbol, price);
                    return price;
                }
            }
            
            throw new RuntimeException("No price data for " + symbol);
            
        } catch (Exception e) {
            logger.error("Failed to fetch price for {}", symbol, e);
            throw new RuntimeException("Failed to fetch price for " + symbol, e);
        }
    }
    
    @Override
    public double getMarginBalance() {
        try {
            logger.debug("Fetching margin balance");
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            String queryString = "accountType=UNIFIED";
            String signature = generateSignature(timestamp, queryString);
            
            String url = String.format("%s/v5/account/wallet-balance?%s", baseUrl, queryString);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-BAPI-API-KEY", apiKey);
            headers.set("X-BAPI-SIGN", signature);
            headers.set("X-BAPI-TIMESTAMP", timestamp);
            headers.set("X-BAPI-RECV-WINDOW", "5000");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            JsonNode root = objectMapper.readTree(response.getBody());
            
            if (root.has("result") && root.get("result").has("list")) {
                JsonNode list = root.get("result").get("list");
                if (list.size() > 0) {
                    JsonNode coins = list.get(0).get("coin");
                    for (JsonNode coin : coins) {
                        if ("USDT".equals(coin.get("coin").asText())) {
                            double balance = coin.get("availableToWithdraw").asDouble();
                            logger.debug("USDT balance: {}", balance);
                            return balance;
                        }
                    }
                }
            }
            
            logger.warn("No USDT balance found");
            return 0.0;
            
        } catch (Exception e) {
            logger.error("Failed to fetch balance", e);
            throw new RuntimeException("Failed to fetch balance", e);
        }
    }
    
    @Override
    public void setLeverage(String symbol, int leverage) {
        try {
            logger.info("Setting leverage to {}x for {}", leverage, symbol);
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            String body = String.format("{\"category\":\"linear\",\"symbol\":\"%s\",\"buyLeverage\":\"%d\",\"sellLeverage\":\"%d\"}",
                symbol, leverage, leverage);
            String signature = generateSignature(timestamp, body);
            
            String url = baseUrl + "/v5/position/set-leverage";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("X-BAPI-API-KEY", apiKey);
            headers.set("X-BAPI-SIGN", signature);
            headers.set("X-BAPI-TIMESTAMP", timestamp);
            headers.set("X-BAPI-RECV-WINDOW", "5000");
            
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.get("retCode").asInt() == 0) {
                logger.info("Leverage set to {}x for {}", leverage, symbol);
            } else {
                logger.warn("Leverage setting response: {}", response.getBody());
            }
            
        } catch (Exception e) {
            logger.error("Failed to set leverage for {}", symbol, e);
            throw new RuntimeException("Failed to set leverage for " + symbol, e);
        }
    }
    
    @Override
    public void enterLongPosition(String symbol, double tradeAmount) {
        placeOrder(symbol, "Buy", tradeAmount, false);
    }
    
    @Override
    public void enterShortPosition(String symbol, double tradeAmount) {
        placeOrder(symbol, "Sell", tradeAmount, false);
    }
    
    @Override
    public void exitLongPosition(String symbol, double tradeAmount) {
        placeOrder(symbol, "Sell", tradeAmount, true);
    }
    
    @Override
    public void exitShortPosition(String symbol, double tradeAmount) {
        placeOrder(symbol, "Buy", tradeAmount, true);
    }
    
    private void placeOrder(String symbol, String side, double quantity, boolean reduceOnly) {
        try {
            logger.info("Placing {} order: {} units of {} (reduceOnly: {})", 
                side, quantity, symbol, reduceOnly);
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            String body = String.format(
                "{\"category\":\"linear\",\"symbol\":\"%s\",\"side\":\"%s\",\"orderType\":\"Market\",\"qty\":\"%s\",\"positionIdx\":0%s}",
                symbol, side, String.valueOf(quantity), reduceOnly ? ",\"reduceOnly\":true" : ""
            );
            String signature = generateSignature(timestamp, body);
            
            String url = baseUrl + "/v5/order/create";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("X-BAPI-API-KEY", apiKey);
            headers.set("X-BAPI-SIGN", signature);
            headers.set("X-BAPI-TIMESTAMP", timestamp);
            headers.set("X-BAPI-RECV-WINDOW", "5000");
            
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.get("retCode").asInt() == 0) {
                String orderId = root.get("result").get("orderId").asText();
                logger.info("Order placed successfully. ID: {}", orderId);
            } else {
                logger.error("Order failed: {}", response.getBody());
                throw new RuntimeException("Order failed: " + root.get("retMsg").asText());
            }
            
        } catch (Exception e) {
            logger.error("Failed to place order", e);
            throw new RuntimeException("Failed to place order: " + e.getMessage(), e);
        }
    }
    
    private String generateSignature(String timestamp, String params) {
        try {
            String signaturePayload = timestamp + apiKey + "5000" + params;
            Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSHA256.init(secretKey);
            byte[] hash = hmacSHA256.doFinal(signaturePayload.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }
    
    private String convertTimeframe(String timeframe) {
        return switch (timeframe.toUpperCase()) {
            case "1M", "1m" -> "1";
            case "3M", "3m" -> "3";
            case "5M", "5m" -> "5";
            case "15M", "15m" -> "15";
            case "30M", "30m" -> "30";
            case "1H", "1h" -> "60";
            case "2H", "2h" -> "120";
            case "4H", "4h" -> "240";
            case "6H", "6h" -> "360";
            case "12H", "12h" -> "720";
            case "1D", "1d" -> "D";
            case "1W", "1w" -> "W";
            case "1MON" -> "M";
            default -> {
                logger.warn("Unknown timeframe {}, defaulting to 1h", timeframe);
                yield "60";
            }
        };
    }
    
    private long getIntervalMillis(String interval) {
        return switch (interval) {
            case "1" -> 60_000L;
            case "3" -> 180_000L;
            case "5" -> 300_000L;
            case "15" -> 900_000L;
            case "30" -> 1_800_000L;
            case "60" -> 3_600_000L;
            case "120" -> 7_200_000L;
            case "240" -> 14_400_000L;
            case "360" -> 21_600_000L;
            case "720" -> 43_200_000L;
            case "D" -> 86_400_000L;
            case "W" -> 604_800_000L;
            case "M" -> 2_592_000_000L;
            default -> 3_600_000L;
        };
    }
}
