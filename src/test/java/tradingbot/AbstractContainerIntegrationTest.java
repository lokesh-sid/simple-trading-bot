package tradingbot;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Abstract base class for integration tests that rely on real infrastructure containers.
 *
 * <p>Starts a PostgreSQL 16 container and a Kafka (Confluent) container once per test
 * run and wires their connection details into the Spring application context via
 * {@link DynamicPropertySource} before the context is refreshed.
 *
 * <p>Concrete subclasses need only to declare the {@code @SpringBootTest} annotation
 * (and any additional test-specific configuration) – the containers are managed here.
 *
 * <pre>{@code
 * @SpringBootTest(
 *     classes = ContainerIntegrationTestConfig.class,
 *     webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
 * )
 * class MyContainerTest extends AbstractContainerIntegrationTest {
 *     // your tests
 * }
 * }</pre>
 */
@Testcontainers
public abstract class AbstractContainerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("trading_bot")
                    .withUsername("tradingbot")
                    .withPassword("tradingbot123");

    @Container
    static final ConfluentKafkaContainer kafka =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    /**
     * Injects container-specific connection properties into the Spring
     * {@link org.springframework.core.env.Environment} before the application
     * context is started.
     */
    @DynamicPropertySource
    static void configureContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
