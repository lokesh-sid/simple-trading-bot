package tradingbot.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.mockito.Mockito;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import jakarta.persistence.EntityManagerFactory;
import tradingbot.agent.api.dto.AgentMapper;
import tradingbot.agent.application.AgentService;
import tradingbot.agent.infrastructure.llm.LLMProvider;
import tradingbot.agent.manager.AgentManager;
import tradingbot.agent.persistence.AgentRepository;
import tradingbot.agent.TradingAgentFactory;
import tradingbot.bot.service.backtest.BacktestAgentExecutionService;
import tradingbot.bot.service.backtest.BacktestMetricsCalculator;
import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.controller.BotStateController;
import tradingbot.bot.controller.ResilienceController;
import tradingbot.bot.controller.TradingBotController;
import tradingbot.bot.controller.dto.BotState;
import tradingbot.bot.service.BotCacheService;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.service.PaperFuturesExchangeService;
import tradingbot.bot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.bot.strategy.calculator.IndicatorCalculator;
import tradingbot.bot.strategy.calculator.IndicatorValues;
import tradingbot.bot.strategy.exit.PositionExitCondition;
import tradingbot.bot.strategy.tracker.TrailingStopTracker;
import tradingbot.security.repository.UserRepository;

/**
 * Test configuration for Gateway Integration Tests.
 * Provides minimal Spring context with mocked agent dependencies.
 */
@TestConfiguration
@EnableAutoConfiguration(exclude = {
    KafkaAutoConfiguration.class,
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class,
    SecurityAutoConfiguration.class,
    ManagementWebSecurityAutoConfiguration.class
})
@TestPropertySource(properties = {
    // Use H2 in-memory database for testing
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.kafka.consumer.auto-startup=false",
    "spring.kafka.producer.bootstrap-servers=localhost:9999",
    "spring.kafka.bootstrap-servers=localhost:9999",
    "trading.exchange.provider=paper",
    "trading.binance.api.key=test-api-key"
})
@ComponentScan(
    basePackages = {
        "tradingbot.bot.controller",
        "tradingbot.bot.service",
        "tradingbot.gateway.controller",
        "tradingbot.gateway.service"
    },
    useDefaultFilters = true,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
            TradingBotController.class,
            BotStateController.class,
            ResilienceController.class,
            BotCacheService.class
        })
    }
)
@Import({InstanceConfig.class})
@EnableJpaRepositories(basePackages = {
    "tradingbot.bot.persistence.repository"
})
@EntityScan(basePackages = {
    "tradingbot.bot.persistence.entity",
    "tradingbot.agent.infrastructure.repository",
    "tradingbot.agent.infrastructure.persistence",
    "tradingbot.agent.persistence",
    "tradingbot.security.entity"
})
public class GatewayTestConfig {

    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan(
            "tradingbot.bot.persistence.entity",
            "tradingbot.agent.infrastructure.repository",
            "tradingbot.agent.infrastructure.persistence",
            "tradingbot.agent.persistence",
            "tradingbot.security.entity"
        );
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        em.setJpaPropertyMap(properties);

        return em;
    }

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

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
    public FuturesExchangeService exchangeService() {
        return Mockito.mock(FuturesExchangeService.class);
    }

    @Bean
    public SentimentAnalyzer sentimentAnalyzer() {
        return Mockito.mock(SentimentAnalyzer.class);
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    @Bean
    public LLMProvider llmProvider() {
        return Mockito.mock(LLMProvider.class);
    }

    @Bean
    public UserRepository userRepository() {
        return Mockito.mock(UserRepository.class);
    }

    @Bean
    public RestTemplate restTemplate() {
        return Mockito.mock(RestTemplate.class);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, IndicatorValues> redisTemplate() {
        return Mockito.mock(RedisTemplate.class);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, BotState> botStateRedisTemplate() {
        return Mockito.mock(RedisTemplate.class);
    }

    // Resilience4j beans for ResilienceController
    @Bean
    public RateLimiter binanceTradingRateLimiter() {
        return Mockito.mock(RateLimiter.class);
    }

    @Bean
    public RateLimiter binanceMarketRateLimiter() {
        return Mockito.mock(RateLimiter.class);
    }

    @Bean
    public RateLimiter binanceAccountRateLimiter() {
        return Mockito.mock(RateLimiter.class);
    }

    @Bean
    public CircuitBreaker binanceApiCircuitBreaker() {
        return Mockito.mock(CircuitBreaker.class);
    }

    @Bean
    public Retry binanceApiRetry() {
        return Mockito.mock(Retry.class);
    }

    @Bean
    public AgentManager agentManager() {
        return Mockito.mock(AgentManager.class);
    }

    @Bean
    public AgentRepository agentRepository() {
        return Mockito.mock(AgentRepository.class);
    }

    @Bean
    public AgentService agentService() {
        return Mockito.mock(AgentService.class);
    }

    @Bean
    @Primary
    public AgentMapper agentApiMapper() {
        return Mockito.mock(AgentMapper.class);
    }
}