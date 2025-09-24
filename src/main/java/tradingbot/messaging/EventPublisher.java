package tradingbot.messaging;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import tradingbot.events.BotStatusEvent;
import tradingbot.events.MarketDataEvent;
import tradingbot.events.RiskEvent;
import tradingbot.events.TradeExecutionEvent;
import tradingbot.events.TradeSignalEvent;
import tradingbot.events.TradingEvent;

/**
 * Simplified Event Publisher for trading events.
 * 
 * This demonstration version uses Redis for storage but is designed to show
 * the event publishing patterns. In production, replace with Kafka for:
 * - Better durability and scalability  
 * - Proper partitioning and consumer groups
 * - Built-in monitoring and management tools
 * 
 * Key insight: You don't need both Kafka AND Redis Streams. 
 * Kafka alone can handle all use cases with different topic configurations.
 */
@Service
public class EventPublisher {
    
    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // Simplified topic naming (production would be: trading.signals, trading.executions, etc.)
    private static final String TRADE_SIGNALS_TOPIC = "trade-signals";
    private static final String TRADE_EXECUTION_TOPIC = "trade-execution";
    private static final String RISK_EVENTS_TOPIC = "risk-events";
    private static final String MARKET_DATA_TOPIC = "market-data";
    private static final String BOT_STATUS_TOPIC = "bot-status";
    
    public EventPublisher(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Publishes a trade signal event.
     */
    public CompletableFuture<Void> publishTradeSignal(TradeSignalEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                publishToTopic(TRADE_SIGNALS_TOPIC, event.getBotId(), event);
                log.info("Published trade signal: {} for {} with strength {}", 
                    event.getSignal(), event.getSymbol(), event.getStrength());
            } catch (Exception ex) {
                log.error("Failed to publish trade signal event", ex);
                throw new EventPublishingException("Event publishing failed", ex);
            }
        });
    }
    
    /**
     * Publishes a trade execution event.
     */
    public CompletableFuture<Void> publishTradeExecution(TradeExecutionEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                publishToTopic(TRADE_EXECUTION_TOPIC, event.getBotId(), event);
                log.info("Published trade execution: {} {} {} at ${}", 
                    event.getSide(), event.getQuantity(), event.getSymbol(), event.getPrice());
            } catch (Exception ex) {
                log.error("Failed to publish trade execution event", ex);
                throw new EventPublishingException("Event publishing failed", ex);
            }
        });
    }
    
    /**
     * Publishes a risk assessment event.
     */
    public CompletableFuture<Void> publishRiskEvent(RiskEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                publishToTopic(RISK_EVENTS_TOPIC, event.getBotId(), event);
                log.info("Published risk event: {} - {}", event.getRiskType(), event.getDescription());
            } catch (Exception ex) {
                log.error("Failed to publish risk event", ex);
                throw new EventPublishingException("Event publishing failed", ex);
            }
        });
    }
    
    /**
     * Publishes market data updates.
     */
    public CompletableFuture<Void> publishMarketData(MarketDataEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                publishToTopic(MARKET_DATA_TOPIC, event.getSymbol(), event);
                log.debug("Published market data for {}: ${}", event.getSymbol(), event.getPrice());
            } catch (Exception ex) {
                log.error("Failed to publish market data event", ex);
                throw new EventPublishingException("Event publishing failed", ex);
            }
        });
    }
    
    /**
     * Publishes bot status updates.
     */
    public CompletableFuture<Void> publishBotStatus(BotStatusEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                publishToTopic(BOT_STATUS_TOPIC, event.getBotId(), event);
                log.info("Published bot status: {} - {}", event.getStatus(), event.getMessage());
            } catch (Exception ex) {
                log.error("Failed to publish bot status event", ex);
                throw new EventPublishingException("Event publishing failed", ex);
            }
        });
    }
    
    /**
     * Core method to publish events to Redis topics.
     * In production, this would be replaced with Kafka producer calls.
     * 
     * @param topic The topic name
     * @param key The partition key (botId, symbol, etc.)
     * @param event The event to publish
     */
    private void publishToTopic(String topic, String key, TradingEvent event) {
        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("eventId", event.getEventId());
            eventData.put("timestamp", event.getTimestamp());
            eventData.put("eventType", event.getClass().getSimpleName());
            eventData.put("data", event);
            eventData.put("key", key);
            
            // Redis implementation - store in hash with timestamp-based key
            String redisKey = topic + ":" + System.currentTimeMillis() + ":" + event.getEventId();
            redisTemplate.opsForHash().putAll(redisKey, eventData);
            
            // Set expiration to avoid infinite growth
            redisTemplate.expire(redisKey, java.time.Duration.ofHours(24));
            
            log.debug("Event published to topic {} with key {} and id {}", topic, key, event.getEventId());
            
        } catch (Exception ex) {
            log.error("Failed to publish event to topic {}", topic, ex);
            throw new RuntimeException("Topic publishing failed for: " + topic, ex);
        }
    }
    
    /**
     * Gets the current count of events in a topic (for monitoring).
     */
    public long getTopicEventCount(String topic) {
        try {
            return redisTemplate.keys(topic + ":*").size();
        } catch (Exception ex) {
            log.warn("Failed to get event count for topic {}", topic, ex);
            return 0;
        }
    }
    
    /**
     * Health check method to verify the publisher is working.
     */
    public boolean isHealthy() {
        try {
            // Test basic Redis connectivity
            redisTemplate.opsForValue().set("health-check", Instant.now().toString());
            return true;
        } catch (Exception ex) {
            log.error("Event publisher health check failed", ex);
            return false;
        }
    }
    
    /**
     * Custom exception for event publishing failures.
     */
    public static class EventPublishingException extends RuntimeException {
        public EventPublishingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}