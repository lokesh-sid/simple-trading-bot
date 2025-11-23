package tradingbot.config;

import java.util.List;

import org.mockito.Mockito;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import tradingbot.agent.factory.AgentFactory;
import tradingbot.agent.manager.AgentManager;
import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.controller.TradingBotController;
import tradingbot.bot.controller.exception.GlobalExceptionHandler;
import tradingbot.bot.service.PaperFuturesExchangeService;
import tradingbot.bot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.bot.strategy.calculator.IndicatorCalculator;
import tradingbot.bot.strategy.calculator.IndicatorValues;
import tradingbot.bot.strategy.exit.PositionExitCondition;
import tradingbot.bot.strategy.tracker.TrailingStopTracker;

/**
 * Test configuration for Futures Trading Bot Integration Tests.
 * Provides minimal Spring context with in-memory implementations.
 */
@Configuration
@TestPropertySource(properties = {
    // Use H2 in-memory database for testing
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    // Disable Kafka for integration tests
    "spring.kafka.bootstrap-servers=",
    // Use paper trading for safe testing
    "trading.exchange.provider=paper",
    // Mock API key for testing
    "trading.binance.api.key=test-api-key"
})
@EnableAutoConfiguration(exclude = {
    KafkaAutoConfiguration.class,
    RedisAutoConfiguration.class,
    SecurityAutoConfiguration.class,
    ManagementWebSecurityAutoConfiguration.class
})
@ComponentScan(
    basePackages = {"tradingbot.bot.controller", "tradingbot.bot.service"},
    useDefaultFilters = false,
    includeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {
                TradingBotController.class,
                GlobalExceptionHandler.class
            }
        )
    }
)
@Import({InstanceConfig.class, AgentManager.class, AgentFactory.class})
@EnableJpaRepositories(basePackages = "tradingbot.agent.persistence")
@EntityScan(basePackages = "tradingbot.agent.persistence")
public class FuturesTradingBotIntegrationTestConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public TradingConfig tradingConfig() {
        return new TradingConfig(
            "BTCUSDT",           // symbol
            0.001,              // tradeAmount
            1,                  // leverage
            1.0,                // trailingStopPercent
            14,                 // lookbackPeriodRsi
            30.0,               // rsiOversoldThreshold
            70.0,               // rsiOverboughtThreshold
            12,                 // macdFastPeriod
            26,                 // macdSlowPeriod
            9,                  // macdSignalPeriod
            20,                 // bbPeriod
            2.0,                // bbStandardDeviation
            900                 // interval
        );
    }

    @Bean
    public FuturesTradingBot tradingBot() {
        // Create a proper mock with required dependencies
        FuturesTradingBot mockBot = Mockito.mock(FuturesTradingBot.class);
        
        // Mock the required dependencies that createBot() method uses
        Mockito.when(mockBot.getExchangeService()).thenReturn(new PaperFuturesExchangeService());
        Mockito.when(mockBot.getIndicatorCalculator()).thenReturn(Mockito.mock(IndicatorCalculator.class));
        Mockito.when(mockBot.getTrailingStopTracker()).thenReturn(Mockito.mock(TrailingStopTracker.class));
        Mockito.when(mockBot.getSentimentAnalyzer()).thenReturn(Mockito.mock(SentimentAnalyzer.class));
        
        // Create a simple mock exit condition that never triggers
        PositionExitCondition mockExitCondition = Mockito.mock(PositionExitCondition.class);
        Mockito.when(mockExitCondition.shouldExit()).thenReturn(false);
        Mockito.when(mockBot.getExitConditions()).thenReturn(List.of(mockExitCondition)); // Non-empty list
        
        Mockito.when(mockBot.getConfig()).thenReturn(tradingConfig());
        
        return mockBot;
    }

    @Bean
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, IndicatorValues> redisTemplate() {
        return Mockito.mock(RedisTemplate.class);
    }
}