package tradingbot.messaging.integration;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import tradingbot.messaging.EventPublisher;

/**
 * Simple test to verify Kafka setup works without complex operations
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"test-topic"})
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.group-id=simple-test-group"
})
class SimpleKafkaIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Test
    void shouldLoadKafkaContext() {
        // This test just verifies that the Spring context loads with embedded Kafka
        assertThat(eventPublisher).isNotNull();
        assertThat(eventPublisher.isHealthy()).isTrue();
    }
}