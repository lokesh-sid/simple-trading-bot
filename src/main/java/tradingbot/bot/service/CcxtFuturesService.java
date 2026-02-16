package tradingbot.bot.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.service.BinanceFuturesService.Candle;
import tradingbot.bot.service.OrderResult.OrderStatus;
import tradingbot.exchange.ccxt.CcxtApiClient;
import tradingbot.exchange.ccxt.dto.CcxtOrder;
import tradingbot.exchange.ccxt.dto.CcxtTicker;

/**
 * CCXT-based implementation of FuturesExchangeService.
 * Routes requests through CCXT-REST sidecar for multi-exchange support.
 */
@Service("ccxtFuturesService")
public class CcxtFuturesService implements FuturesExchangeService {

    private static final Logger log = LoggerFactory.getLogger(CcxtFuturesService.class);
    private static final String SYMBOL_NULL_ERROR = "Symbol cannot be null";
    private static final String ORDER_TYPE_MARKET = "market";
    
    private final CcxtApiClient apiClient;
    private final String exchangeId;
    private final String quoteCurrency;
    private final EventPublisher eventPublisher;

    public CcxtFuturesService(CcxtApiClient apiClient, 
                              @Value("${trading.ccxt.exchange-id:binance}") String exchangeId,
                              @Value("${trading.ccxt.quote-currency:USDT}") String quoteCurrency,
                              EventPublisher eventPublisher) {
        this.apiClient = apiClient;
        this.exchangeId = exchangeId;
        this.quoteCurrency = quoteCurrency;
        this.eventPublisher = eventPublisher;
        log.info("Initialized CCXT Futures Service for exchange: {} with quote currency: {}", exchangeId, quoteCurrency);
    }

    @Override
    public List<Candle> fetchOhlcv(String symbol, String timeframe, int limit) {
        return apiClient.fetchOhlcv(exchangeId, symbol, timeframe, limit);
    }

    @Override
    public double getCurrentPrice(String symbol) {
        Objects.requireNonNull(symbol, SYMBOL_NULL_ERROR);
        
        try {
            CcxtTicker ticker = apiClient.fetchTicker(exchangeId, symbol);
            if (ticker == null || ticker.last() == null) {
                log.error("Received null ticker or last price for {}", symbol);
                throw new IllegalStateException("Invalid ticker data received");
            }
            return ticker.last().doubleValue();
        } catch (RestClientException e) {
            log.error("Failed to get current price for {}: {}", symbol, e.getMessage());
            throw e;
        }
    }

    @Override
    public double getMarginBalance() {
        try {
            Map<String, Object> balance = apiClient.fetchBalance(exchangeId);
            if (balance == null || balance.isEmpty()) {
                log.warn("Received empty balance data");
                return 0.0;
            }
            
            if (balance.containsKey("total")) {
                Object totalObj = balance.get("total");
                if (totalObj instanceof Map) {
                    Map<?, ?> total = (Map<?, ?>) totalObj;
                    if (total.containsKey(quoteCurrency)) {
                        Number value = (Number) total.get(quoteCurrency);
                        return value != null ? value.doubleValue() : 0.0;
                    }
                }
            }
            
            log.warn("Could not find {} balance in response", quoteCurrency);
            return 0.0;
        } catch (Exception e) {
            log.error("Error fetching balance: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    @Override
    public Ticker24hrStats get24HourStats(String symbol) {
        Objects.requireNonNull(symbol, SYMBOL_NULL_ERROR);
        
        try {
            CcxtTicker ticker = apiClient.fetchTicker(exchangeId, symbol);
            
            return Ticker24hrStats.builder()
                    .symbol(symbol)
                    .lastPrice(ticker.last() != null ? ticker.last().doubleValue() : 0.0)
                    .highPrice(ticker.high() != null ? ticker.high().doubleValue() : 0.0)
                    .lowPrice(ticker.low() != null ? ticker.low().doubleValue() : 0.0)
                    .volume(ticker.baseVolume() != null ? ticker.baseVolume().doubleValue() : 0.0)
                    .quoteVolume(ticker.quoteVolume() != null ? ticker.quoteVolume().doubleValue() : 0.0)
                    .priceChangePercent(ticker.percentage() != null ? ticker.percentage().doubleValue() : 0.0)
                    .priceChange(ticker.change() != null ? ticker.change().doubleValue() : 0.0)
                    .openPrice(ticker.open() != null ? ticker.open().doubleValue() : 0.0)
                    .build();
        } catch (RestClientException e) {
            log.error("Failed to get 24h stats for {}: {}", symbol, e.getMessage());
            throw e;
        }
    }

    @Override
    public void setLeverage(String symbol, int leverage) {
        Objects.requireNonNull(symbol, SYMBOL_NULL_ERROR);
        
        try {
            apiClient.setLeverage(exchangeId, symbol, leverage);
        } catch (Exception e) {
            log.error("Failed to set leverage for {}: {}", symbol, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public OrderResult enterLongPosition(String symbol, double tradeAmount) {
        return executeOrder(symbol, ORDER_TYPE_MARKET, "buy", tradeAmount, null, null);
    }

    @Override
    public OrderResult exitLongPosition(String symbol, double tradeAmount) {
        return executeOrder(symbol, ORDER_TYPE_MARKET, "sell", tradeAmount, null, Map.of("reduceOnly", true));
    }

    @Override
    public OrderResult enterShortPosition(String symbol, double tradeAmount) {
        return executeOrder(symbol, ORDER_TYPE_MARKET, "sell", tradeAmount, null, null);
    }

    @Override
    public OrderResult exitShortPosition(String symbol, double tradeAmount) {
        return executeOrder(symbol, ORDER_TYPE_MARKET, "buy", tradeAmount, null, Map.of("reduceOnly", true));
    }

    @Override
    public OrderResult placeStopLossOrder(String symbol, String side, double quantity, double stopPrice) {
        Map<String, Object> params = Map.of("stopPrice", stopPrice);
        return executeOrder(symbol, "stop_market", side.toLowerCase(), quantity, null, params);
    }

    @Override
    public OrderResult placeTakeProfitOrder(String symbol, String side, double quantity, double takeProfitPrice) {
        Map<String, Object> params = Map.of("stopPrice", takeProfitPrice);
        return executeOrder(symbol, "take_profit_market", side.toLowerCase(), quantity, null, params);
    }

    private OrderResult executeOrder(String symbol, String type, String side, double amount, Double price, Map<String, Object> params) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Order amount must be positive, got: " + amount);
        }
        
        try {
            BigDecimal amountBd = BigDecimal.valueOf(amount);
            BigDecimal priceBd = price != null ? BigDecimal.valueOf(price) : null;
            
            CcxtOrder order = apiClient.createOrder(exchangeId, symbol, type, side, amountBd, priceBd, params);
            
            OrderStatus status = OrderStatus.NEW;
            String lowerStatus = order.status() != null ? order.status().toLowerCase() : "open";
            
            if ("closed".equals(lowerStatus)) status = OrderStatus.FILLED;
            else if ("canceled".equals(lowerStatus)) status = OrderStatus.CANCELED;
            else if ("rejected".equals(lowerStatus)) status = OrderStatus.REJECTED;
            
            double filled = order.filled() != null ? order.filled().doubleValue() : 0.0;
            double avgPrice = order.price() != null ? order.price().doubleValue() : 0.0;
            if (avgPrice == 0.0 && order.cost() != null && filled > 0) {
                 avgPrice = order.cost().doubleValue() / filled;
            }

            OrderResult result = OrderResult.builder()
                    .exchangeOrderId(order.id())
                    .symbol(symbol)
                    .side(side.toUpperCase())
                    .status(status)
                    .orderedQuantity(amount)
                    .filledQuantity(filled)
                    .avgFillPrice(avgPrice)
                    .build();
            
            if (status == OrderStatus.FILLED) {
                publishTradeExecutionEvent(symbol, side.toUpperCase(), filled, avgPrice, order.id());
            }

            return result;
                    
        } catch (RestClientException e) {
            log.error("Order execution failed for {} {}/{}: {}", side, symbol, amount, e.getMessage(), e);
            return OrderResult.builder()
                    .symbol(symbol)
                    .status(OrderStatus.REJECTED)
                    .side(side.toUpperCase())
                    .orderedQuantity(amount)
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error executing order: {}", e.getMessage(), e);
            return OrderResult.builder()
                    .symbol(symbol)
                    .status(OrderStatus.REJECTED)
                    .side(side.toUpperCase())
                    .orderedQuantity(amount)
                    .build();
        }
    }
    
    private void publishTradeExecutionEvent(String symbol, String side, double quantity, double price, String orderId) {
        if (eventPublisher != null) {
            try {
                TradeExecutionEvent event = new TradeExecutionEvent();
                event.setBotId("ccxt-bot-" + exchangeId);
                event.setOrderId(orderId);
                event.setSymbol(symbol);
                event.setSide(side);
                event.setQuantity(quantity);
                event.setPrice(price);
                event.setStatus("FILLED");
                
                eventPublisher.publishTradeExecution(event);
                log.info("Published trade execution event for order {}", orderId);
            } catch (Exception e) {
                log.error("Failed to publish trade execution event for order {}: {}", orderId, e.getMessage());
            }
        }
    }
}
