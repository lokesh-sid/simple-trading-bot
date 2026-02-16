package tradingbot.exchange.ccxt;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tradingbot.bot.service.BinanceFuturesService.Candle;
import tradingbot.exchange.ccxt.dto.CcxtOrder;
import tradingbot.exchange.ccxt.dto.CcxtTicker;

/**
 * REST client for communicating with CCXT-REST sidecar service.
 * Provides unified API access to 100+ cryptocurrency exchanges.
 */
@Component
public class CcxtApiClient {

    private static final Logger log = LoggerFactory.getLogger(CcxtApiClient.class);
    private static final String EXCHANGE_ID_NULL_ERROR = "Exchange ID cannot be null";
    private static final String SYMBOL_NULL_ERROR = "Symbol cannot be null";
    
    private final RestClient restClient;
    private final String apiKey;
    private final String apiSecret;

    public CcxtApiClient(RestClient.Builder builder, 
                         @Value("${trading.ccxt.base-url:http://ccxt-rest:3000}") String baseUrl,
                         @Value("${trading.ccxt.api.key:}") String apiKey,
                         @Value("${trading.ccxt.api.secret:}") String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    log.error("CCXT API error: {} - {}", response.getStatusCode(), response.getStatusText());
                    throw new RestClientException("CCXT API Error: " + response.getStatusCode() + " " + response.getStatusText());
                })
                .build();
        
        log.info("CCXT API Client initialized with base URL: {}", baseUrl);
    }

    /**
     * Fetch current ticker data for a symbol
     * @param exchangeId Exchange identifier (e.g., "binance", "kraken")
     * @param symbol Trading pair symbol (e.g., "BTC/USDT")
     * @return Ticker data
     * @throws IllegalArgumentException if inputs are invalid
     * @throws RestClientException if API call fails
     */
    public CcxtTicker fetchTicker(String exchangeId, String symbol) {
        Objects.requireNonNull(exchangeId, EXCHANGE_ID_NULL_ERROR);
        Objects.requireNonNull(symbol, SYMBOL_NULL_ERROR);
        
        log.debug("Fetching ticker for {}/{}", exchangeId, symbol);
        
        try {
            CcxtTicker ticker = restClient.get()
                    .uri("/exchange/{exchange_id}/ticker?symbol={symbol}", exchangeId, symbol)
                    .retrieve()
                    .body(CcxtTicker.class);
            
            if (ticker == null) {
                throw new RestClientException("Received null ticker response");
            }
            
            return ticker;
        } catch (RestClientException e) {
            log.error("Failed to fetch ticker for {}/{}: {}", exchangeId, symbol, e.getMessage());
            throw e;
        }
    }

    /**
     * Fetch OHLCV candle data
     * @param exchangeId Exchange identifier
     * @param symbol Trading pair symbol
     * @param timeframe Candle timeframe (e.g., "1m", "5m", "1h")
     * @param limit Number of candles to fetch
     * @return List of candles
     * @throws IllegalArgumentException if inputs are invalid
     * @throws RestClientException if API call fails
     */
    public List<Candle> fetchOhlcv(String exchangeId, String symbol, String timeframe, int limit) {
        Objects.requireNonNull(exchangeId, EXCHANGE_ID_NULL_ERROR);
        Objects.requireNonNull(symbol, SYMBOL_NULL_ERROR);
        Objects.requireNonNull(timeframe, "Timeframe cannot be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive, got: " + limit);
        }
        
        log.debug("Fetching {} OHLCV candles for {}/{} with timeframe {}", limit, exchangeId, symbol, timeframe);
        
        try {
            List<List<Object>> rawCandles = restClient.get()
                    .uri("/exchange/{exchange_id}/ohlcv?symbol={symbol}&timeframe={timeframe}&limit={limit}", 
                         exchangeId, symbol, timeframe, limit)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<List<Object>>>() {});

            if (rawCandles == null || rawCandles.isEmpty()) {
                log.warn("No OHLCV data returned for {}/{}", exchangeId, symbol);
                return List.of();
            }

            return rawCandles.stream()
                    .map(raw -> mapToCandle(raw, timeframe))
                    .toList();
        } catch (RestClientException e) {
            log.error("Failed to fetch OHLCV for {}/{}: {}", exchangeId, symbol, e.getMessage());
            throw e;
        }
    }

    /**
     * Fetch account balance (requires authentication)
     * @param exchangeId Exchange identifier
     * @return Balance map structure
     * @throws RestClientException if API call fails
     */
    public Map<String, Object> fetchBalance(String exchangeId) {
        Objects.requireNonNull(exchangeId, EXCHANGE_ID_NULL_ERROR);
        log.debug("Fetching balance for {}", exchangeId);
        
        try {
            Map<String, Object> balance = restClient.get()
                    .uri("/exchange/{exchange_id}/balance", exchangeId)
                    .headers(headers -> addAuthHeaders(headers, exchangeId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (balance == null) {
                log.warn("Received null balance response for {}", exchangeId);
                return Map.of();
            }
            
            return balance;
        } catch (RestClientException e) {
            log.error("Failed to fetch balance for {}: {}", exchangeId, e.getMessage());
            throw e;
        }
    }

    /**
     * Create an order (requires authentication)
     * @param exchangeId Exchange identifier
     * @param symbol Trading pair symbol
     * @param type Order type (market, limit, etc.)
     * @param side Order side (buy, sell)
     * @param amount Order amount
     * @param price Order price (null for market orders)
     * @param params Additional exchange-specific parameters
     * @return Order result
     * @throws RestClientException if API call fails
     */
    public CcxtOrder createOrder(String exchangeId, String symbol, String type, 
                                 String side, BigDecimal amount, BigDecimal price, 
                                 Map<String, Object> params) {
        Objects.requireNonNull(exchangeId, EXCHANGE_ID_NULL_ERROR);
        Objects.requireNonNull(symbol, SYMBOL_NULL_ERROR);
        Objects.requireNonNull(type, "Order type cannot be null");
        Objects.requireNonNull(side, "Order side cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive, got: " + amount);
        }
        
        log.info("Creating {} {} order for {} on {}: {} @ {}", side, type, symbol, exchangeId, amount, price);
        
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("symbol", symbol);
            requestBody.put("type", type);
            requestBody.put("side", side);
            requestBody.put("amount", amount);
            if (price != null) {
                requestBody.put("price", price);
            }
            if (params != null && !params.isEmpty()) {
                requestBody.put("params", params);
            }

            CcxtOrder order = restClient.post()
                    .uri("/exchange/{exchange_id}/order", exchangeId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> addAuthHeaders(headers, exchangeId))
                    .body(requestBody)
                    .retrieve()
                    .body(CcxtOrder.class);
            
            if (order == null) {
                throw new RestClientException("Received null order response");
            }
            
            log.info("Order created successfully: {}", order.id());
            return order;
        } catch (RestClientException e) {
            log.error("Failed to create order for {}/{}: {}", exchangeId, symbol, e.getMessage());
            throw e;
        }
    }

    /**
     * Set leverage for a symbol (requires authentication)
     * @param exchangeId Exchange identifier
     * @param symbol Trading pair symbol
     * @param leverage Leverage value (e.g., 10 for 10x)
     * @throws RestClientException if API call fails
     */
    public void setLeverage(String exchangeId, String symbol, int leverage) {
        Objects.requireNonNull(exchangeId, EXCHANGE_ID_NULL_ERROR);
        Objects.requireNonNull(symbol, SYMBOL_NULL_ERROR);
        if (leverage <= 0 || leverage > 125) {
            throw new IllegalArgumentException("Leverage must be between 1 and 125, got: " + leverage);
        }
        
        log.info("Setting leverage to {}x for {}/{}", leverage, exchangeId, symbol);
        
        try {
            List<Object> args = List.of(leverage, symbol);
            
            restClient.post()
                    .uri("/exchange/{exchange_id}/setLeverage", exchangeId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> addAuthHeaders(headers, exchangeId))
                    .body(args)
                    .retrieve()
                    .toBodilessEntity();
            
            log.info("Leverage set successfully");
        } catch (RestClientException e) {
            log.error("Failed to set leverage for {}/{}: {}", exchangeId, symbol, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Health check to verify CCXT-REST service is reachable
     * @return true if service is healthy
     */
    public boolean isHealthy() {
        try {
            restClient.get()
                    .uri("/exchanges")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("CCXT service health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Add authentication headers for API calls
     */
    private void addAuthHeaders(org.springframework.http.HttpHeaders headers, String exchangeId) {
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.add("X-CCXT-API-KEY", apiKey);
        }
        if (apiSecret != null && !apiSecret.isEmpty()) {
            headers.add("X-CCXT-API-SECRET", apiSecret);
        }
        headers.add("X-CCXT-EXCHANGE", exchangeId);
    }
    
    /**
     * Parse timeframe string to milliseconds
     */
    private long parseTimeframeToMillis(String timeframe) {
        if (timeframe == null || timeframe.isEmpty()) {
            return 60000; // Default 1m
        }
        
        String unit = timeframe.substring(timeframe.length() - 1);
        int value;
        try {
            value = Integer.parseInt(timeframe.substring(0, timeframe.length() - 1));
        } catch (NumberFormatException e) {
            log.warn("Invalid timeframe format: {}, defaulting to 1m", timeframe);
            return 60000;
        }
        
        return switch (unit.toLowerCase()) {
            case "s" -> value * 1000L;
            case "m" -> value * 60000L;
            case "h" -> value * 3600000L;
            case "d" -> value * 86400000L;
            case "w" -> value * 604800000L;
            default -> {
                log.warn("Unknown timeframe unit: {}, defaulting to 1m", unit);
                yield 60000L;
            }
        };
    }

    /**
     * Map raw CCXT OHLCV array to Candle object
     * @param raw Raw OHLCV array [timestamp, open, high, low, close, volume]
     * @param timeframe Timeframe string for calculating close time
     * @return Candle object
     */
    private Candle mapToCandle(List<Object> raw, String timeframe) {
        if (raw == null || raw.size() < 6) {
            throw new IllegalArgumentException("Invalid candle data: expected 6 elements, got: " + 
                    (raw == null ? "null" : raw.size()));
        }
        
        try {
            long openTime = ((Number) raw.get(0)).longValue();
            
            // Ensure values are handled as strings to avoid precision loss before BigDecimal conversion
            BigDecimal open = new BigDecimal(raw.get(1).toString());
            BigDecimal high = new BigDecimal(raw.get(2).toString());
            BigDecimal low = new BigDecimal(raw.get(3).toString());
            BigDecimal close = new BigDecimal(raw.get(4).toString());
            BigDecimal volume = new BigDecimal(raw.get(5).toString());
            
            // Calculate close time based on timeframe
            long timeframeMillis = parseTimeframeToMillis(timeframe);
            long closeTime = openTime + timeframeMillis;

            Candle candle = new Candle();
            candle.setOpenTime(openTime);
            candle.setOpen(open);
            candle.setHigh(high);
            candle.setLow(low);
            candle.setClose(close);
            candle.setVolume(volume);
            candle.setCloseTime(closeTime);
            return candle;
        } catch (Exception e) {
            log.error("Failed to map candle data: {}", raw, e);
            throw new IllegalArgumentException("Failed to parse candle data", e);
        }
    }
}
