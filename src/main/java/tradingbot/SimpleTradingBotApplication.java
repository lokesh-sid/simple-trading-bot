package tradingbot;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.FuturesTradingBot.BotParams;
import tradingbot.bot.events.BotStatusEvent;
import tradingbot.bot.TradeDirection;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.messaging.EventTopic;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.service.RateLimitedBinanceFuturesService;
import tradingbot.bot.service.RateLimitedBybitFuturesService;
import tradingbot.bot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.bot.strategy.calculator.IndicatorCalculator;
import tradingbot.bot.strategy.exit.LiquidationRiskExit;
import tradingbot.bot.strategy.exit.MACDExit;
import tradingbot.bot.strategy.exit.PositionExitCondition;
import tradingbot.bot.strategy.exit.TrailingStopExit;
import tradingbot.bot.strategy.indicator.BollingerBandsIndicator;
import tradingbot.bot.strategy.indicator.MACDTechnicalIndicator;
import tradingbot.bot.strategy.indicator.RSITechnicalIndicator;
import tradingbot.bot.strategy.indicator.TechnicalIndicator;
import tradingbot.bot.strategy.tracker.TrailingStopTracker;
import tradingbot.config.InstanceConfig;
import tradingbot.config.TradingConfig;

@SpringBootApplication(exclude = {
    net.devh.boot.grpc.server.autoconfigure.GrpcServerSecurityAutoConfiguration.class
})
@EnableKafka  // Enable Kafka support
@EnableAsync  // Enable async processing for CompletableFuture
@EnableScheduling
@EnableCaching

public class SimpleTradingBotApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleTradingBotApplication.class);
    private static final Logger log = LoggerFactory.getLogger(SimpleTradingBotApplication.class);
    
    private final EventPublisher eventPublisher;
    private final InstanceConfig instanceConfig;
    
    public SimpleTradingBotApplication(EventPublisher eventPublisher, InstanceConfig instanceConfig) {
        this.eventPublisher = eventPublisher;
        this.instanceConfig = instanceConfig;
    }
    
    public static void main(String[] args) {
        SpringApplication.run(SimpleTradingBotApplication.class, args);
    }

    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    FuturesExchangeService exchangeService(
            @Value("${trading.exchange.provider:binance}") String provider,
            @Value("${trading.binance.api.key}") String binanceApiKey,
            @Value("${trading.binance.api.secret}") String binanceApiSecret,
            @Value("${trading.bybit.api.key:}") String bybitApiKey,
            @Value("${trading.bybit.api.secret:}") String bybitApiSecret,
            @Value("${trading.bybit.domain:MAINNET_DOMAIN}") String bybitDomain) {
        
        logger.info("Initializing exchange service: {}", provider);
        
        return switch(provider.toLowerCase()) {
            case "binance" -> {
                logger.info("Using Binance Futures exchange");
                yield new RateLimitedBinanceFuturesService(binanceApiKey, binanceApiSecret);
            }
            case "bybit" -> {
                logger.info("Using Bybit Futures exchange (domain: {})", bybitDomain);
                String baseUrl = "TESTNET_DOMAIN".equals(bybitDomain) 
                    ? "https://api-testnet.bybit.com"
                    : "https://api.bybit.com";
                yield new RateLimitedBybitFuturesService(bybitApiKey, bybitApiSecret, baseUrl);
            }
            default -> throw new IllegalArgumentException(
                "Unsupported exchange: " + provider + ". Supported: binance, bybit"
            );
        };
    }

    @Bean
    SentimentAnalyzer sentimentAnalyzer(RestTemplate restTemplate) {
        return new SentimentAnalyzer(restTemplate);
    }

    @Bean
    FuturesTradingBot tradingBot(
            FuturesExchangeService exchangeService,
            SentimentAnalyzer sentimentAnalyzer,
            @Value("${trading.binance.api.key}") String apiKey) {
        TradingConfig config = new TradingConfig();
        TechnicalIndicator rsiIndicator = new RSITechnicalIndicator(config.getLookbackPeriodRsi());
        TechnicalIndicator macdIndicator = new MACDTechnicalIndicator(config.getMacdFastPeriod(), config.getMacdSlowPeriod(), config.getMacdSignalPeriod(), false);
        TechnicalIndicator macdSignalIndicator = new MACDTechnicalIndicator(config.getMacdFastPeriod(), config.getMacdSlowPeriod(), config.getMacdSignalPeriod(), true);
        TechnicalIndicator bbLowerIndicator = new BollingerBandsIndicator(config.getBbPeriod(), config.getBbStandardDeviation(), true);
        TechnicalIndicator bbUpperIndicator = new BollingerBandsIndicator(config.getBbPeriod(), config.getBbStandardDeviation(), false);
        java.util.Map<String, TechnicalIndicator> indicators = new java.util.HashMap<>();
        indicators.put("rsi", rsiIndicator);
        indicators.put("macd", macdIndicator);
        indicators.put("macdSignal", macdSignalIndicator);
        indicators.put("bbLower", bbLowerIndicator);
        indicators.put("bbUpper", bbUpperIndicator);
        IndicatorCalculator indicatorCalculator = new IndicatorCalculator(exchangeService, indicators, new RedisTemplate<>());
        TrailingStopTracker trailingStopTracker = new TrailingStopTracker(exchangeService, config.getTrailingStopPercent());
        List<PositionExitCondition> exitConditions = Arrays.asList(
                new TrailingStopExit(trailingStopTracker),
                new MACDExit(indicatorCalculator),
                new LiquidationRiskExit(exchangeService, trailingStopTracker, config)
        );
        
        // Skip leverage initialization if using placeholder API credentials
        boolean skipLeverageInit = "YOUR_BINANCE_API_KEY".equals(apiKey) 
            || "your-binance-api-key-here".equals(apiKey)
            || apiKey == null 
            || apiKey.trim().isEmpty();
        
        // Use BotParams to pass all required parameters
        BotParams botParams = new BotParams.Builder()
            .exchangeService(exchangeService)
            .indicatorCalculator(indicatorCalculator)
            .trailingStopTracker(trailingStopTracker)
            .sentimentAnalyzer(sentimentAnalyzer)
            .exitConditions(exitConditions)
            .config(config)
            .tradeDirection(TradeDirection.LONG)
            .skipLeverageInit(skipLeverageInit) // Skip if using placeholder credentials
            .build();
        return new FuturesTradingBot(botParams);
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("🚀 Simple Trading Bot started successfully!");
        log.info("� Instance ID: {}", instanceConfig.getInstanceId());
        log.info("🌍 Zone: {}", instanceConfig.getAvailabilityZone());
        log.info("�📡 Kafka Publisher Health: {}", eventPublisher.isHealthy() ? "✅ Healthy" : "❌ Unhealthy");
        
        // Log available topics
        log.info("📋 Available Kafka Topics:");
        for (EventTopic topic : EventTopic.values()) {
            log.info("  - {}", topic.getTopicName());
        }
        
        // Publish startup event
        publishStartupEvent();
        
        log.info("🎯 Trading Bot is ready to process requests!");
    }
    
    @EventListener(ContextClosedEvent.class)
    public void onApplicationShutdown() {
        log.info("🛑 Simple Trading Bot is shutting down...");
        
        // Publish shutdown event
        publishShutdownEvent();
        
        log.info("👋 Simple Trading Bot shutdown complete!");
    }
    
    private void publishStartupEvent() {
        try {
            BotStatusEvent startupEvent = new BotStatusEvent(instanceConfig.getInstanceId(), "STARTING");
            startupEvent.setMessage("Trading bot instance started successfully");
            
            eventPublisher.publishBotStatus(startupEvent);
            log.debug("📤 Published startup event");
        } catch (Exception e) {
            log.warn("⚠️ Failed to publish startup event: {}", e.getMessage());
        }
    }
    
    private void publishShutdownEvent() {
        try {
            BotStatusEvent shutdownEvent = new BotStatusEvent(instanceConfig.getInstanceId(), "STOPPING");
            shutdownEvent.setMessage("Trading bot instance shutting down gracefully");
            
            eventPublisher.publishBotStatus(shutdownEvent);
            log.debug("📤 Published shutdown event");
            
            // Give some time for the event to be published
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("⚠️ Interrupted while waiting for shutdown event to publish");
        } catch (Exception e) {
            log.warn("⚠️ Failed to publish shutdown event: {}", e.getMessage());
        }
    }
}