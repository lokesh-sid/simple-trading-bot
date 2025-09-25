package tradingbot.messaging.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import tradingbot.bot.TradeDirection;
import tradingbot.events.BotStatusEvent;
import tradingbot.events.MarketDataEvent;
import tradingbot.events.RiskEvent;
import tradingbot.events.TradeExecutionEvent;
import tradingbot.events.TradeSignalEvent;
import tradingbot.messaging.EventPublisher;

/**
 * Integration tests for Kafka event publishing.
 * 
 * Uses Spring Boot's embedded Kafka for testing event publishing
 * without requiring external Kafka infrastructure.
 */
@Disabled("Temporarily disabled while debugging embedded Kafka issues")
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {
        "trading.signals",
        "trading.executions", 
        "trading.risk",
        "trading.market-data",
        "trading.bot-status"
    }
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.group-id=test-group",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
@DisplayName("Kafka Integration Tests")
class KafkaIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Test
    @DisplayName("Should publish trade signal event successfully")
    void shouldPublishTradeSignalEvent() {
        // Given
        TradeSignalEvent event = new TradeSignalEvent("integration-bot", "BTCUSDT", TradeDirection.LONG);
        event.setStrength(0.8);

        // When
        CompletableFuture<Void> result = eventPublisher.publishTradeSignal(event);
        
        // Then
        assertNotNull(result);
        assertDoesNotThrow(() -> result.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should publish trade execution event successfully")
    void shouldPublishTradeExecutionEvent() {
        // Given
        TradeExecutionEvent event = new TradeExecutionEvent("integration-bot", "order-456", "ETHUSDT");
        event.setSide("SELL");
        event.setQuantity(0.5);
        event.setPrice(3000.0);
        event.setStatus("FILLED");

        // When
        CompletableFuture<Void> publishResult = eventPublisher.publishTradeExecution(event);

        // Then
        assertNotNull(publishResult);
        assertDoesNotThrow(() -> publishResult.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should publish risk event successfully")
    void shouldPublishRiskEvent() {
        // Given
        RiskEvent event = new RiskEvent("integration-bot", "STOP_LOSS_TRIGGERED", "BTCUSDT");
        event.setSeverity("CRITICAL");
        event.setAction("CLOSE_POSITION");
        event.setDescription("Stop loss triggered at 45000");
        event.setCurrentPrice(45000.0);

        // When
        CompletableFuture<Void> publishResult = eventPublisher.publishRiskEvent(event);

        // Then
        assertNotNull(publishResult);
        assertDoesNotThrow(() -> publishResult.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should publish market data event successfully")
    void shouldPublishMarketDataEvent() {
        // Given
        MarketDataEvent event = new MarketDataEvent("integration-bot", "BTCUSDT", 48000.0);
        event.setVolume(1500.0);

        // When
        CompletableFuture<Void> publishResult = eventPublisher.publishMarketData(event);

        // Then
        assertNotNull(publishResult);
        assertDoesNotThrow(() -> publishResult.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should publish bot status event successfully")
    void shouldPublishBotStatusEvent() {
        // Given
        BotStatusEvent event = new BotStatusEvent("integration-bot", "CONFIGURED");
        event.setMessage("Bot configuration updated");
        event.setRunning(true);
        event.setCurrentBalance(2500.0);
        event.setActivePosition("LONG");

        // When
        CompletableFuture<Void> publishResult = eventPublisher.publishBotStatus(event);

        // Then
        assertNotNull(publishResult);
        assertDoesNotThrow(() -> publishResult.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should publish multiple events successfully")
    void shouldPublishMultipleEvents() {
        // Given
        TradeSignalEvent event1 = new TradeSignalEvent("bot-1", "BTCUSDT", TradeDirection.LONG);
        TradeSignalEvent event2 = new TradeSignalEvent("bot-2", "ETHUSDT", TradeDirection.SHORT);
        TradeSignalEvent event3 = new TradeSignalEvent("bot-1", "ADAUSDT", TradeDirection.LONG);

        // When
        CompletableFuture<Void> allPublished = CompletableFuture.allOf(
            eventPublisher.publishTradeSignal(event1),
            eventPublisher.publishTradeSignal(event2),
            eventPublisher.publishTradeSignal(event3)
        );

        // Then
        assertNotNull(allPublished);
        assertDoesNotThrow(() -> allPublished.get(10, TimeUnit.SECONDS));
    }
}