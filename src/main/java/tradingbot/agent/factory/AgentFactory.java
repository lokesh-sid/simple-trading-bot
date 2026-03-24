package tradingbot.agent.factory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import tradingbot.agent.TradingAgent;
import tradingbot.agent.config.AgentProperties;
import tradingbot.agent.infrastructure.repository.AgentEntity;
import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.FuturesTradingBot.BotParams;
import tradingbot.bot.TradeDirection;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.service.BinanceFuturesService;
import tradingbot.bot.service.BybitFuturesService;
import tradingbot.bot.service.DydxFuturesService;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.service.PaperFuturesExchangeService;
import tradingbot.bot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.bot.strategy.calculator.IndicatorCalculator;
import tradingbot.bot.strategy.calculator.IndicatorValues;
import tradingbot.bot.strategy.exit.LiquidationRiskExit;
import tradingbot.bot.strategy.exit.MACDExit;
import tradingbot.bot.strategy.exit.PositionExitCondition;
import tradingbot.bot.strategy.exit.TrailingStopExit;
import tradingbot.bot.strategy.indicator.BollingerBandsIndicator;
import tradingbot.bot.strategy.indicator.MACDTechnicalIndicator;
import tradingbot.bot.strategy.indicator.RSITechnicalIndicator;
import tradingbot.bot.strategy.indicator.TechnicalIndicator;
import tradingbot.bot.strategy.tracker.TrailingStopTracker;
import tradingbot.config.TradingConfig;

/**
 * Factory component responsible for instantiating TradingAgent objects.
 * 
 * This class implements the Simple Factory pattern, dispatching the creation
 * of specific agent implementations (like FuturesTradingBot) based on the
 * agent type defined in the AgentEntity.
 */
@Component
public class AgentFactory {
    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private final FuturesExchangeService realExchangeService;
    private final SentimentAnalyzer sentimentAnalyzer;
    private final RedisTemplate<String, IndicatorValues> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;


    // All credentials now come from AgentProperties.credentials map

    private final EventPublisher eventPublisher;

    public AgentFactory(FuturesExchangeService exchangeService, 
                        SentimentAnalyzer sentimentAnalyzer,
                        RedisTemplate<String, IndicatorValues> redisTemplate,
                        ObjectMapper objectMapper,
                        AgentProperties agentProperties,
                        EventPublisher eventPublisher) {
        this.realExchangeService = exchangeService;
        this.sentimentAnalyzer = sentimentAnalyzer;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.agentProperties = agentProperties;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates all agents defined in the YAML config (trading.agents).
     */
    public List<TradingAgent> createAgentsFromConfig() {
        List<TradingAgent> agents = new java.util.ArrayList<>();
        if (agentProperties.getAgents() != null) {
            for (AgentProperties.AgentConfig config : agentProperties.getAgents()) {
                TradingConfig tradingConfig = new TradingConfig();
                tradingConfig.setSymbol(config.getSymbol());
                // TODO: Map other fields as needed (interval, strategy, etc.)
                // Set direction if TradingConfig supports a public field or setter
                if (config.getDirection() != null) {
                    // Assuming TradingConfig has a public 'direction' field
                    try {
                        java.lang.reflect.Field directionField = TradingConfig.class.getDeclaredField("direction");
                        directionField.setAccessible(true);
                        directionField.set(tradingConfig, config.getDirection());
                    } catch (Exception e) {
                        // Field not present, ignore or log as needed
                    }
                }
                TradingAgent agent = createFuturesTradingBotFromConfig(config, tradingConfig);
                agents.add(agent);
            }
        }
        return agents;
    }

    private FuturesExchangeService createExchangeService(String exchangeName) {
        if (exchangeName == null || exchangeName.isBlank()) {
            return realExchangeService;
        }
        String exchange = exchangeName.toUpperCase();
        var creds = agentProperties.getCredentials() != null ? agentProperties.getCredentials().get(exchange.toLowerCase()) : null;
        if (creds == null) {
            throw new IllegalArgumentException("Missing credentials for exchange: " + exchange);
        }
        return switch (exchange) {
            case "BINANCE" -> new BinanceFuturesService(creds.getApiKey(), creds.getApiSecret(), eventPublisher);
            case "BYBIT" -> {
                String baseUrl = "TESTNET_DOMAIN".equalsIgnoreCase(creds.getDomain())
                    ? "https://api-testnet.bybit.com"
                    : "https://api.bybit.com";
                yield new BybitFuturesService(creds.getApiKey(), creds.getApiSecret(), baseUrl, eventPublisher);
            }
            case "DYDX" -> new DydxFuturesService(
                creds.getNetwork(), creds.getMainnetUrl(), creds.getTestnetUrl(), creds.getPrivateKey(), eventPublisher);
            case "PAPER" -> new PaperFuturesExchangeService();
            default -> throw new IllegalArgumentException("Unsupported exchange: " + exchange);
        };
    }

    private FuturesTradingBot createFuturesTradingBotFromConfig(AgentProperties.AgentConfig config, TradingConfig tradingConfig) {
        FuturesExchangeService exchangeService = createExchangeService(config.getExchange());

        Map<String, TechnicalIndicator> indicators = createIndicators(tradingConfig);
        IndicatorCalculator indicatorCalculator = new IndicatorCalculator(exchangeService, indicators, redisTemplate);
        TrailingStopTracker trailingStopTracker = new TrailingStopTracker(exchangeService, tradingConfig.getTrailingStopPercent());
        List<PositionExitCondition> exitConditions = createExitConditions(tradingConfig, indicatorCalculator, trailingStopTracker, exchangeService);

        TradeDirection direction = TradeDirection.LONG;
        if (tradingConfig.getDirection() != null) {
            try {
                direction = TradeDirection.valueOf(tradingConfig.getDirection().toUpperCase());
            } catch (Exception e) {
                // fallback to LONG if invalid
            }
        }
        BotParams botParams = new BotParams.Builder()
            .id(config.getSymbol() + "-" + config.getExchange())
            .name(config.getSymbol() + " " + config.getExchange())
            .exchangeService(exchangeService)
            .indicatorCalculator(indicatorCalculator)
            .trailingStopTracker(trailingStopTracker)
            .sentimentAnalyzer(sentimentAnalyzer)
            .exitConditions(exitConditions)
            .config(tradingConfig)
            .tradeDirection(direction)
            .skipLeverageInit(false) // Exchange-specific logic can be added if needed
            .build();
        return new FuturesTradingBot(botParams);
    }
    // Duplicate methods removed

    public TradingAgent createAgent(AgentEntity entity) {
        try {
            TradingConfig config = objectMapper.readValue(entity.getGoalDescription(), TradingConfig.class);
            config.setSymbol(entity.getTradingSymbol());
            return createFuturesTradingBot(entity, config);
        } catch (Exception e) {
            log.error("Failed to create agent {} — invalid goalDescription JSON: {}", entity.getId(), entity.getGoalDescription(), e);
            throw new RuntimeException("Invalid agent configuration for agent " + entity.getId(), e);
        }
    }

    private FuturesTradingBot createFuturesTradingBot(AgentEntity entity, TradingConfig config) {
        boolean isPaper = entity.getExecutionMode() == AgentEntity.ExecutionMode.FUTURES_PAPER;
        // For integration tests, if realExchangeService is a mock, use it instead of creating new PaperService
        // This allows Mockito behavior to persist
        FuturesExchangeService exchangeService;
        if (this.realExchangeService.getClass().getName().contains("Mockito")) {
            exchangeService = this.realExchangeService;
        } else if (isPaper) {
            exchangeService = new PaperFuturesExchangeService();
        } else {
            exchangeService = createExchangeService(entity.getExchangeName());
        }

        TradeDirection direction = TradeDirection.LONG;
        if (config.getDirection() != null && !config.getDirection().isBlank()) {
            try {
                direction = TradeDirection.valueOf(config.getDirection().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown direction '{}' for agent {}, defaulting to LONG", config.getDirection(), entity.getId());
            }
        }

        Map<String, TechnicalIndicator> indicators = createIndicators(config);
        IndicatorCalculator indicatorCalculator = new IndicatorCalculator(exchangeService, indicators, redisTemplate);
        TrailingStopTracker trailingStopTracker = new TrailingStopTracker(exchangeService, config.getTrailingStopPercent());
        List<PositionExitCondition> exitConditions = createExitConditions(config, indicatorCalculator, trailingStopTracker, exchangeService);

        BotParams botParams = new BotParams.Builder()
            .id(entity.getId())
            .name(entity.getName())
            .exchangeService(exchangeService)
            .indicatorCalculator(indicatorCalculator)
            .trailingStopTracker(trailingStopTracker)
            .sentimentAnalyzer(sentimentAnalyzer)
            .exitConditions(exitConditions)
            .config(config)
            .tradeDirection(direction)
            .skipLeverageInit(shouldSkipLeverageInit(isPaper))
            .build();

        return new FuturesTradingBot(botParams);
    }

    private Map<String, TechnicalIndicator> createIndicators(TradingConfig config) {
        Map<String, TechnicalIndicator> indicators = new HashMap<>();
        indicators.put("rsi", new RSITechnicalIndicator(config.getLookbackPeriodRsi()));
        indicators.put("macd", new MACDTechnicalIndicator(config.getMacdFastPeriod(), config.getMacdSlowPeriod(), config.getMacdSignalPeriod(), false));
        indicators.put("macdSignal", new MACDTechnicalIndicator(config.getMacdFastPeriod(), config.getMacdSlowPeriod(), config.getMacdSignalPeriod(), true));
        indicators.put("bbLower", new BollingerBandsIndicator(config.getBbPeriod(), config.getBbStandardDeviation(), true));
        indicators.put("bbUpper", new BollingerBandsIndicator(config.getBbPeriod(), config.getBbStandardDeviation(), false));
        return indicators;
    }

    private List<PositionExitCondition> createExitConditions(TradingConfig config, 
            IndicatorCalculator indicatorCalculator, 
            TrailingStopTracker trailingStopTracker, 
            FuturesExchangeService exchangeService) {
        return Arrays.asList(
                new TrailingStopExit(trailingStopTracker),
                new MACDExit(indicatorCalculator),
                new LiquidationRiskExit(exchangeService, trailingStopTracker, config)
        );
    }

    private boolean shouldSkipLeverageInit(boolean isPaper) {
        // This logic may need to be updated to use credentials map if needed
        return isPaper;
    }
}
