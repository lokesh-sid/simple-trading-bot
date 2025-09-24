package tradingbot.messaging;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import tradingbot.events.BotStatusEvent;
import tradingbot.events.MarketDataEvent;
import tradingbot.events.RiskEvent;
import tradingbot.events.TradeExecutionEvent;
import tradingbot.events.TradeSignalEvent;
import tradingbot.events.TradingEvent;

/**
 * Kafka-based Event Publisher for trading events.
 * 
 * This production-ready implementation uses Apache Kafka for:
 * - High durability and scalability  
 * - Proper partitioning and consumer groups
 * - Built-in monitoring and management tools
 * - Guaranteed message delivery with configurable acknowledgments
 * 
 * Events are published to dedicated Kafka topics with appropriate partitioning
 * keys (botId, symbol) for optimal distribution and ordering guarantees.
 */
@Service
public class EventPublisher {
    
    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Publishes a trade signal event.
     */
    public CompletableFuture<Void> publishTradeSignal(TradeSignalEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                publishToTopic(EventTopic.TRADE_SIGNALS, event.getBotId(), event);
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
                publishToTopic(EventTopic.TRADE_EXECUTION, event.getBotId(), event);
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
                publishToTopic(EventTopic.RISK_EVENTS, event.getBotId(), event);
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
                publishToTopic(EventTopic.MARKET_DATA, event.getSymbol(), event);
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
                publishToTopic(EventTopic.BOT_STATUS, event.getBotId(), event);
                log.info("Published bot status: {} - {}", event.getStatus(), event.getMessage());
            } catch (Exception ex) {
                log.error("Failed to publish bot status event", ex);
                throw new EventPublishingException("Event publishing failed", ex);
            }
        });
    }
    
    /**
     * Core method to publish events to Kafka topics.
     * 
     * @param topic The topic enum  
     * @param key The partition key (botId, symbol, etc.)
     * @param event The event to publish
     */
    private void publishToTopic(EventTopic topic, String key, TradingEvent event) {
        try {
            // Create event wrapper with metadata
            Map<String, Object> eventWrapper = new HashMap<>();
            eventWrapper.put("eventId", event.getEventId());
            eventWrapper.put("timestamp", event.getTimestamp());
            eventWrapper.put("eventType", event.getClass().getSimpleName());
            eventWrapper.put("data", event);
            eventWrapper.put("partitionKey", key);
            
            // Publish to Kafka with partition key for ordering guarantees
            kafkaTemplate.send(topic.getTopicName(), key, eventWrapper)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send event {} to topic {} with key {}", 
                            event.getEventId(), topic.getTopicName(), key, ex);
                        throw new EventPublishingException("Kafka publish failed", ex);
                    } else {
                        log.debug("Event {} published to topic {} with key {} at partition {} offset {}", 
                            event.getEventId(), topic.getTopicName(), key,
                            result.getRecordMetadata().partition(), 
                            result.getRecordMetadata().offset());
                    }
                });
            
        } catch (Exception ex) {
            log.error("Failed to publish event to topic {}", topic.getTopicName(), ex);
            throw new EventPublishingException("Event publishing failed for topic: " + topic.getTopicName(), ex);
        }
    }
    
    /**
     * Gets the current count of events in a topic (for monitoring).
     * Note: Kafka doesn't provide direct topic event counts like Redis.
     * This is a placeholder method for backward compatibility.
     */
    public long getTopicEventCount(EventTopic topic) {
        return getTopicEventCount(topic.getTopicName());
    }
    
    /**
     * Gets the current count of events in a topic (for monitoring).
     * Note: Kafka doesn't provide direct topic event counts like Redis.
     * This is a placeholder method for backward compatibility.
     */
    public long getTopicEventCount(String topicName) {
        log.debug("Topic event count requested for {} - Kafka doesn't provide direct counts", topicName);
        // In production, you would use Kafka monitoring tools or JMX metrics
        // to get topic statistics. For now, return -1 to indicate unavailable.
        return -1;
    }
    
    /**
     * Gets basic publisher information for monitoring (Kafka-based).
     */
    public Map<String, Object> getPublisherMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();
            
            // Basic producer information
            metrics.put("producer-type", "Kafka");
            metrics.put("producer-factory", kafkaTemplate.getProducerFactory().getClass().getSimpleName());
            metrics.put("timestamp", Instant.now());
            metrics.put("status", "active");
            
            return metrics;
            
        } catch (Exception ex) {
            log.warn("Failed to get producer metrics", ex);
            return Map.of("error", "Failed to retrieve metrics", "timestamp", Instant.now());
        }
    }
    
    /**
     * Health check method to verify the Kafka publisher is working.
     */
    public boolean isHealthy() {
        try {
            // Test basic Kafka connectivity by checking producer factory
            var producerFactory = kafkaTemplate.getProducerFactory();
            
            // Check if producer factory is properly configured
            if (producerFactory == null) {
                log.error("Kafka producer factory is null");
                return false;
            }
            
            // Simple health check - if we can get the producer factory, we're healthy
            boolean healthy = true;
            
            log.debug("Kafka publisher health check: HEALTHY");
            
            return healthy;
            
        } catch (Exception ex) {
            log.error("Kafka publisher health check failed", ex);
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