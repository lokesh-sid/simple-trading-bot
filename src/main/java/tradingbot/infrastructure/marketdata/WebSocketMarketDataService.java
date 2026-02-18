package tradingbot.infrastructure.marketdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import tradingbot.bot.messaging.EventTopic;
import tradingbot.domain.market.StreamMarketDataEvent;
import tradingbot.infrastructure.marketdata.binance.BinanceWebSocketAdapter;
import tradingbot.infrastructure.marketdata.bybit.BybitWebSocketAdapter;

/**
 * Composite WebSocket service that routes requests to the appropriate exchange adapter
 * and publishes events to Kafka.
 */
@Service
@Primary
public class WebSocketMarketDataService implements ExchangeWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(WebSocketMarketDataService.class);
    private final BinanceWebSocketAdapter binanceAdapter;
    private final BybitWebSocketAdapter bybitAdapter;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public WebSocketMarketDataService(
            BinanceWebSocketAdapter binanceAdapter, 
            BybitWebSocketAdapter bybitAdapter,
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.binanceAdapter = binanceAdapter;
        this.bybitAdapter = bybitAdapter;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public Flux<StreamMarketDataEvent> streamTrades(String symbol) {
        // Use Binance as primary, fallback to Bybit
        return binanceAdapter.streamTrades(symbol)
                .onErrorResume(e -> {
                    log.warn("Binance stream failed for {}, failing over to Bybit", symbol);
                    return bybitAdapter.streamTrades(symbol);
                })
                .doOnNext(this::publishToKafka);
    }

    @Override
    public Flux<StreamMarketDataEvent> streamBookTicker(String symbol) {
        return binanceAdapter.streamBookTicker(symbol)
                .onErrorResume(e -> {
                    log.warn("Binance bookTicker failed for {}, failing over to Bybit", symbol);
                    return bybitAdapter.streamBookTicker(symbol);
                })
                .doOnNext(this::publishToKafka);
    }
    
    private void publishToKafka(StreamMarketDataEvent event) {
        try {
            kafkaTemplate.send(EventTopic.MARKET_DATA.getTopicName(), event.symbol(), event);
        } catch (Exception e) {
            log.error("Failed to publish market data to Kafka", e);
        }
    }
}
