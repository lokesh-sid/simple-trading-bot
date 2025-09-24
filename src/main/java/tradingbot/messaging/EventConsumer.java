package tradingbot.messaging;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka event consumer for trading events.
 * 
 * This service demonstrates how to consume events from Kafka topics.
 * In production, you would have dedicated consumer services for each
 * event type with proper error handling and dead letter queues.
 */
@Service
public class EventConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);
    
    /**
     * Consumes trade signal events from Kafka.
     * 
     * @param payload The event payload
     * @param partition The Kafka partition
     * @param offset The message offset
     * @param key The partition key
     */
    @KafkaListener(topics = "trading.signals", groupId = "trading-bot-signals")
    public void handleTradeSignal(@Payload Map<String, Object> payload,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset,
                                  @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        try {
            log.info("Received trade signal event: key={}, partition={}, offset={}", key, partition, offset);
            log.debug("Trade signal payload: {}", payload);
            
            // Extract event data
            String eventId = (String) payload.get("eventId");
            String eventType = (String) payload.get("eventType");
            Map<String, Object> eventData = (Map<String, Object>) payload.get("data");
            
            log.info("Processing {} event {} with data: {}", eventType, eventId, eventData);
            
            // Here you would process the trade signal
            // For now, just log the event
            
        } catch (Exception ex) {
            log.error("Failed to process trade signal event from partition {} offset {}", partition, offset, ex);
            // In production, you would send to a dead letter queue
        }
    }
    
    /**
     * Consumes trade execution events from Kafka.
     */
    @KafkaListener(topics = "trading.executions", groupId = "trading-bot-executions")
    public void handleTradeExecution(@Payload Map<String, Object> payload,
                                     @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                     @Header(KafkaHeaders.OFFSET) long offset,
                                     @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        try {
            log.info("Received trade execution event: key={}, partition={}, offset={}", key, partition, offset);
            
            String eventId = (String) payload.get("eventId");
            String eventType = (String) payload.get("eventType");
            
            log.info("Processing {} event {}", eventType, eventId);
            
            // Process trade execution event
            // Update portfolios, send notifications, etc.
            
        } catch (Exception ex) {
            log.error("Failed to process trade execution event from partition {} offset {}", partition, offset, ex);
        }
    }
    
    /**
     * Consumes risk events from Kafka.
     */
    @KafkaListener(topics = "trading.risk", groupId = "trading-bot-risk")
    public void handleRiskEvent(@Payload Map<String, Object> payload,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                @Header(KafkaHeaders.OFFSET) long offset,
                                @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        try {
            log.warn("Received risk event: key={}, partition={}, offset={}", key, partition, offset);
            
            String eventId = (String) payload.get("eventId");
            String eventType = (String) payload.get("eventType");
            Map<String, Object> eventData = (Map<String, Object>) payload.get("data");
            
            log.warn("Processing {} risk event {} with data: {}", eventType, eventId, eventData);
            
            // Process risk event - may need to take immediate action
            // Close positions, send alerts, etc.
            
        } catch (Exception ex) {
            log.error("Failed to process risk event from partition {} offset {}", partition, offset, ex);
        }
    }
    
    /**
     * Consumes market data events from Kafka.
     */
    @KafkaListener(topics = "trading.market-data", groupId = "trading-bot-market-data")
    public void handleMarketData(@Payload Map<String, Object> payload,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset,
                                 @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        try {
            log.debug("Received market data event: key={}, partition={}, offset={}", key, partition, offset);
            
            // Market data events are high-frequency, so we use debug level
            String eventId = (String) payload.get("eventId");
            
            log.debug("Processing market data event {}", eventId);
            
            // Process market data - update indicators, trigger signals, etc.
            
        } catch (Exception ex) {
            log.error("Failed to process market data event from partition {} offset {}", partition, offset, ex);
        }
    }
    
    /**
     * Consumes bot status events from Kafka.
     */
    @KafkaListener(topics = "trading.bot-status", groupId = "trading-bot-status")
    public void handleBotStatus(@Payload Map<String, Object> payload,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                @Header(KafkaHeaders.OFFSET) long offset,
                                @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        try {
            log.info("Received bot status event: key={}, partition={}, offset={}", key, partition, offset);
            
            String eventId = (String) payload.get("eventId");
            String eventType = (String) payload.get("eventType");
            
            log.info("Processing {} status event {}", eventType, eventId);
            
            // Process bot status change
            // Update dashboards, send notifications, etc.
            
        } catch (Exception ex) {
            log.error("Failed to process bot status event from partition {} offset {}", partition, offset, ex);
        }
    }
}