package tradingbot.infrastructure.marketdata.bybit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bybit.api.client.service.BybitApiClientFactory;
import com.bybit.api.client.websocket.httpclient.WebsocketStreamClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tradingbot.domain.market.BookTickerPayload;
import tradingbot.domain.market.StreamMarketDataEvent;
import tradingbot.domain.market.StreamMarketDataEvent.EventType;
import tradingbot.infrastructure.marketdata.ExchangeWebSocketClient;

/**
 * Reactive WebSocket client for Bybit V5 (Linear) using official SDK wrapper.
 * Uses Sinks.many().replay().limit(1000) for hot observable sharing.
 */
@Service
public class BybitWebSocketAdapter implements ExchangeWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(BybitWebSocketAdapter.class);
    
    private final Map<String, Sinks.Many<StreamMarketDataEvent>> tradeStreams = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<StreamMarketDataEvent>> tickerStreams = new ConcurrentHashMap<>();
    
    private WebsocketStreamClient wsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${exchange.bybit.use-testnet:false}")
    private boolean useTestnet;

    public BybitWebSocketAdapter() {
    }

    @PostConstruct
    public void init() {
        log.info("Initializing Bybit WebSocket Client [testnet={}]", useTestnet);
        if (useTestnet) {
            // Base URL is set by the factory flag usually
            this.wsClient = BybitApiClientFactory.newInstance("BybitLinear", true).newWebsocketClient();
        } else {
             this.wsClient = BybitApiClientFactory.newInstance("BybitLinear", false).newWebsocketClient();
        }
        
        this.wsClient.setMessageHandler(this::handleMessage);
    }

    private void handleMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if (!root.has("topic") || !root.has("data")) {
                return;
            }

            String topic = root.get("topic").asText();
            JsonNode data = root.get("data");
            long ts = root.has("ts") ? root.get("ts").asLong() : System.currentTimeMillis();
            
            if (topic.startsWith("publicTrade.")) {
                String symbol = topic.split("\\.")[1]; 
                Sinks.Many<StreamMarketDataEvent> sink = tradeStreams.get(symbol);
                if (sink != null) {
                   parseTrades(data, symbol, ts, sink, message);
                }
            } else if (topic.startsWith("orderbook.")) {
                String symbol = topic.split("\\.")[2]; 
                Sinks.Many<StreamMarketDataEvent> sink = tickerStreams.get(symbol);
                if (sink != null) {
                    parseOrderBook(data, symbol, ts, sink, message);
                }
            }

        } catch (Exception e) {
            log.error("Error handling Bybit WS message", e);
        }
    }
    
    private void parseTrades(JsonNode data, String symbol, long ts, Sinks.Many<StreamMarketDataEvent> sink, String raw) {
        if (data.isArray()) {
            for (JsonNode trade : data) {
                StreamMarketDataEvent event = new StreamMarketDataEvent(
                    "BYBIT_LINEAR",
                    symbol,
                    EventType.TRADE,
                    new BigDecimal(trade.get("p").asText()),
                    new BigDecimal(trade.get("v").asText()),
                    Instant.ofEpochMilli(trade.has("T") ? trade.get("T").asLong() : ts),
                    raw
                );
                sink.tryEmitNext(event);
            }
        }
    }

    private void parseOrderBook(JsonNode data, String symbol, long ts, Sinks.Many<StreamMarketDataEvent> sink, String raw) {
        BigDecimal bestAsk = BigDecimal.ZERO;
        if (data.has("a") && data.get("a").isArray() && data.get("a").size() > 0) {
            bestAsk = new BigDecimal(data.get("a").get(0).get(0).asText());
        }
        BigDecimal bestBid = BigDecimal.ZERO;
        if (data.has("b") && data.get("b").isArray() && data.get("b").size() > 0) {
            bestBid = new BigDecimal(data.get("b").get(0).get(0).asText());
        }

        if (bestAsk.signum() > 0) {
            // price = ask; both sides preserved in payload for direction-aware fills
            StreamMarketDataEvent event = new StreamMarketDataEvent(
                    "BYBIT_LINEAR",
                    symbol,
                    EventType.BOOK_TICKER,
                    bestAsk,
                    BigDecimal.ZERO,
                    Instant.ofEpochMilli(ts),
                    new BookTickerPayload(bestBid, bestAsk)
            );
            sink.tryEmitNext(event);
        }
    }

    @Override
    public Flux<StreamMarketDataEvent> streamTrades(String symbol) {
        return tradeStreams.computeIfAbsent(symbol, s -> {
            Sinks.Many<StreamMarketDataEvent> sink = Sinks.many().replay().limit(1000);
            // Pass subscription args and operation
            wsClient.getPublicChannelStream(java.util.List.of("publicTrade." + s), "subscribe");
            return sink;
        }).asFlux();
    }

    @Override
    public Flux<StreamMarketDataEvent> streamBookTicker(String symbol) {
        return tickerStreams.computeIfAbsent(symbol, s -> {
             Sinks.Many<StreamMarketDataEvent> sink = Sinks.many().replay().limit(1000);
             wsClient.getPublicChannelStream(java.util.List.of("orderbook.1." + s), "subscribe");
             return sink;
        }).asFlux();
    }
    
    @PreDestroy
    public void cleanup() {
        // SDK manages its own WS connection lifecycle; no explicit close needed here.
        // If the SDK adds a close() method in future versions it should be called here.
        log.info("BybitWebSocketAdapter shutting down");
    }
}
