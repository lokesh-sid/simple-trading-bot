package tradingbot.messaging;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tradingbot.bot.messaging.EventConsumer;

/**
 * Unit tests for the Kafka EventConsumer.
 * 
 * Tests the message consumption and processing logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventConsumer Tests")
class EventConsumerTest {

    @InjectMocks
    private EventConsumer eventConsumer;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        // Set up log capture for testing log output
        logger = (Logger) LoggerFactory.getLogger(EventConsumer.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        logger.setLevel(Level.DEBUG);
    }

    @Test
    @DisplayName("Should handle trade signal event successfully")
    void shouldHandleTradeSignalEvent() {
        // Given
        Map<String, Object> payload = createEventPayload("trade-signal-123", "TradeSignalEvent");
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("signal", "LONG");
        eventData.put("symbol", "BTCUSDT");
        eventData.put("strength", 0.8);
        payload.put("data", eventData);

        // When
        assertDoesNotThrow(() -> {
            eventConsumer.handleTradeSignal(payload, 0, 100L, "bot-1");
        });

        // Then
        assertTrue(logAppender.list.stream()
            .anyMatch(event -> event.getFormattedMessage().contains("Received trade signal event")));
        assertTrue(logAppender.list.stream()
            .anyMatch(event -> event.getFormattedMessage().contains("Processing TradeSignalEvent event trade-signal-123")));
    }

    @Test
    @DisplayName("Should handle trade execution event successfully")
    void shouldHandleTradeExecutionEvent() {
        // Given
        Map<String, Object> payload = createEventPayload("trade-execution-456", "TradeExecutionEvent");
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("side", "BUY");
        eventData.put("quantity", 1.5);
        eventData.put("price", 45000.0);
        payload.put("data", eventData);

        // When
        assertDoesNotThrow(() -> {
            eventConsumer.handleTradeExecution(payload, 0, 101L, "bot-1");
        });

        // Then
        assertTrue(logAppender.list.stream()
            .anyMatch(event -> event.getFormattedMessage().contains("Received trade execution event")));
        assertTrue(logAppender.list.stream()
            .anyMatch(event -> event.getFormattedMessage().contains("Processing TradeExecutionEvent event trade-execution-456")));
    }

    @Test
    @DisplayName("Should handle risk event successfully")
    void shouldHandleRiskEvent() {
        // Given
        Map<String, Object> payload = createEventPayload("risk-event-789", "RiskEvent");
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("riskType", "POSITION_SIZE_EXCEEDED");
        eventData.put("severity", "HIGH");
        eventData.put("action", "REDUCE_POSITION");
        payload.put("data", eventData);

        // When
        assertDoesNotThrow(() -> {
            eventConsumer.handleRiskEvent(payload, 0, 102L, "bot-1");
        });

        // Then
        assertTrue(logAppender.list.stream()
            .anyMatch(event -> event.getFormattedMessage().contains("Received risk event") && event.getLevel() == Level.WARN));
        assertTrue(logAppender.list.stream()
            .anyMatch(event -> event.getFormattedMessage().contains("Processing RiskEvent risk event risk-event-789")));
    }

    @Test
    @DisplayName("Should handle market data event successfully")
    void shouldHandleMarketDataEvent() {
        // Given
        Map<String, Object> payload = createEventPayload("market-data-321", "MarketDataEvent");
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("symbol", "BTCUSDT");
        eventData.put("price", 45000.0);
        eventData.put("volume", 1000.0);
        payload.put("data", eventData);

        // When
        assertDoesNotThrow(() -> {
            eventConsumer.handleMarketData(payload, 0, 103L, "BTCUSDT");
        });

        // Then
        assertTrue(logAppender.list.stream()
            .anyMatch(event -> event.getFormattedMessage().contains("Received market data event") && event.getLevel() == Level.DEBUG));
        assertTrue(logAppender.list.stream()
            .anyMatch(event -> event.getFormattedMessage().contains("Processing market data event market-data-321")));
    }

    @Test
    @DisplayName("Should handle bot status event successfully")
    void shouldHandleBotStatusEvent() {
        // Given
        Map<String, Object> payload = createEventPayload("bot-status-654", "BotStatusEvent");
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("status", "RUNNING");
        eventData.put("message", "Bot is healthy");
        payload.put("data", eventData);

        // When
        assertDoesNotThrow(() -> {
            eventConsumer.handleBotStatus(payload, 0, 104L, "bot-1");
        });

        // Then
        assertTrue(logAppender.list.stream()
            .anyMatch(event -> event.getFormattedMessage().contains("Received bot status event")));
        assertTrue(logAppender.list.stream()
            .anyMatch(event -> event.getFormattedMessage().contains("Processing BotStatusEvent status event bot-status-654")));
    }

    /**
     * Helper method to create basic event payload structure.
     */
    private Map<String, Object> createEventPayload(String eventId, String eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("eventType", eventType);
        payload.put("timestamp", Instant.now());
        payload.put("partitionKey", "test-key");
        return payload;
    }
}