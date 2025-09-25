package tradingbot.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import tradingbot.bot.TradeDirection;
import tradingbot.events.TradeSignalEvent;

/**
 * Simple test for EventPublisher without requiring actual Kafka infrastructure
 */
@ExtendWith(MockitoExtension.class)
class SimpleKafkaTest {

    @Mock
    private KafkaTemplate<String, Object> mockKafkaTemplate;
    
    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new EventPublisher(mockKafkaTemplate);
        
        // Mock successful send
        CompletableFuture<SendResult<String, Object>> successFuture = CompletableFuture.completedFuture(null);
        when(mockKafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture);
    }

    @Test
    void shouldCreateEventPublisher() {
        assertThat(eventPublisher).isNotNull();
    }

    @Test
    void shouldPublishTradeSignalWithoutError() {
        // Given
        TradeSignalEvent event = new TradeSignalEvent("bot-1", "BTCUSDT", TradeDirection.LONG);

        // When & Then - Should not throw exception
        CompletableFuture<Void> result = eventPublisher.publishTradeSignal(event);
        
        assertThat(result)
            .isNotNull()
            .succeedsWithin(java.time.Duration.ofSeconds(5));
    }
}