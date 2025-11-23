package tradingbot;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import tradingbot.config.FuturesTradingBotIntegrationTestConfig;

/**
 * Abstract base class for integration tests that test complete workflows
 * through REST API endpoints with real Spring context and in-memory services.
 *
 * Provides generic HTTP request utilities.
 */
@SpringBootTest(
    classes = FuturesTradingBotIntegrationTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
public abstract class AbstractIntegrationTest extends AbstractHttpTest {
    // Integration-specific setup inherited from AbstractHttpTest
}