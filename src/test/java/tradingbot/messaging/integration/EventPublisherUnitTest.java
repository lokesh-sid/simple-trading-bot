package tradingbot.messaging.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import tradingbot.bot.TradeDirection;
import tradingbot.bot.events.RiskEvent;
import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.events.TradeSignalEvent;
import tradingbot.bot.messaging.EventPublisher;

/**
 * Unit test for EventPublisher with embedded Kafka.
 * This test focuses solely on the messaging functionality without loading
 * the entire Spring Boot application context.
 */
@SpringJUnitConfig
@EmbeddedKafka(
    partitions = 1,
    controlledShutdown = true,
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:0",
        "port=0"
    },
    topics = {
        "trading.signals",
        "trading.executions", 
        "trading.risk",
        "trading.market-data",
        "trading.bot-status"
    }
)
@TestPropertySource(
    properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=unit-test-group-${random.uuid}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.producer.client-id=unit-test-producer-${random.uuid}",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=*",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer"
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("EventPublisher Unit Tests")
class EventPublisherUnitTest {

    @Autowired
    private EventPublisher eventPublisher;

    @TestConfiguration
    static class TestConfig {
        
        @Bean
        public KafkaTemplate<String, Object> kafkaTemplate(EmbeddedKafkaBroker embeddedKafka) {
            var producerProps = KafkaTestUtils.producerProps(embeddedKafka);
            producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            producerProps.put("value.serializer", "org.springframework.kafka.support.serializer.JsonSerializer");
            
            return new KafkaTemplate<>(
                new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(producerProps)
            );
        }
        
        @Bean
        public EventPublisher eventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
            return new EventPublisher(kafkaTemplate);
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("Should publish trade signal event successfully")
    void shouldPublishTradeSignalEvent() {
        // Given
        TradeSignalEvent event = new TradeSignalEvent("unit-test-bot", "BTCUSDT", TradeDirection.LONG);
        event.setStrength(0.8);

        // When
        CompletableFuture<Void> result = eventPublisher.publishTradeSignal(event);
        
        // Then
        assertNotNull(result, "Publisher should return a non-null CompletableFuture");
        assertDoesNotThrow(() -> {
            result.get(15, TimeUnit.SECONDS);
        }, "Event publishing should complete without throwing exceptions");
    }

    @Test
    @Timeout(30)
    @DisplayName("Should publish trade execution event successfully")
    void shouldPublishTradeExecutionEvent() {
        // Given
        TradeExecutionEvent event = new TradeExecutionEvent("unit-test-bot", "order-456", "ETHUSDT");
        event.setSide("SELL");
        event.setQuantity(0.5);
        event.setPrice(3000.0);
        event.setStatus("FILLED");

        // When
        CompletableFuture<Void> publishResult = eventPublisher.publishTradeExecution(event);

        // Then
        assertNotNull(publishResult, "Publisher should return a non-null CompletableFuture");
        assertDoesNotThrow(() -> {
            publishResult.get(15, TimeUnit.SECONDS);
        }, "Event publishing should complete without throwing exceptions");
    }

    @Test
    @Timeout(30)
    @DisplayName("Should publish risk event successfully")
    void shouldPublishRiskEvent() {
        // Given
        RiskEvent event = new RiskEvent("unit-test-bot", "STOP_LOSS_TRIGGERED", "BTCUSDT");
        event.setSeverity("CRITICAL");
        event.setAction("CLOSE_POSITION");
        event.setDescription("Stop loss triggered at 45000");
        event.setCurrentPrice(45000.0);

        // When
        CompletableFuture<Void> publishResult = eventPublisher.publishRiskEvent(event);

        // Then
        assertNotNull(publishResult, "Publisher should return a non-null CompletableFuture");
        assertDoesNotThrow(() -> {
            publishResult.get(15, TimeUnit.SECONDS);
        }, "Event publishing should complete without throwing exceptions");
    }

    @Test
    @Timeout(30)  
    @DisplayName("Should verify publisher health")
    void shouldVerifyPublisherHealth() {
        // When & Then
        assertTrue(eventPublisher.isHealthy(), "EventPublisher should be healthy");
    }
}