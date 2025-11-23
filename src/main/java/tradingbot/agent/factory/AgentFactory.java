package tradingbot.agent.factory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import tradingbot.agent.TradingAgent;
import tradingbot.agent.persistence.AgentEntity;
import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.FuturesTradingBot.BotParams;
import tradingbot.bot.TradeDirection;
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

    private final FuturesExchangeService realExchangeService;
    private final SentimentAnalyzer sentimentAnalyzer;
    private final RedisTemplate<String, IndicatorValues> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${trading.binance.api.key}")
    private String apiKey;

    public AgentFactory(FuturesExchangeService exchangeService, 
                        SentimentAnalyzer sentimentAnalyzer,
                        RedisTemplate<String, IndicatorValues> redisTemplate,
                        ObjectMapper objectMapper) {
        this.realExchangeService = exchangeService;
        this.sentimentAnalyzer = sentimentAnalyzer;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public TradingAgent createAgent(AgentEntity entity) {
        try {
            TradingConfig config = objectMapper.readValue(entity.getConfigurationJson(), TradingConfig.class);
            
            // Override symbol from entity if needed, or ensure they match
            config.setSymbol(entity.getSymbol());

            // Dispatch based on agent type (Simple Factory Pattern)
            String type = entity.getType() != null ? entity.getType().toUpperCase() : "UNKNOWN";
            
            return switch (type) {
                case "FUTURES", "FUTURES_PAPER" -> createFuturesTradingBot(entity, config);
                default -> throw new IllegalArgumentException("Unsupported agent type: " + type);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create agent from entity: " + entity.getId(), e);
        }
    }

    private FuturesTradingBot createFuturesTradingBot(AgentEntity entity, TradingConfig config) {
        boolean isPaper = "FUTURES_PAPER".equalsIgnoreCase(entity.getType());
        FuturesExchangeService exchangeService = isPaper ? new PaperFuturesExchangeService() : this.realExchangeService;

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
            .tradeDirection(TradeDirection.valueOf(entity.getDirection()))
            .skipLeverageInit(shouldSkipLeverageInit(isPaper))
            .build();
            
        FuturesTradingBot bot = new FuturesTradingBot(botParams);
        if (entity.isSentimentEnabled()) {
            bot.enableSentimentAnalysis(true);
        }
        return bot;
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
        return isPaper || "YOUR_BINANCE_API_KEY".equals(apiKey) 
            || "your-binance-api-key-here".equals(apiKey)
            || apiKey == null 
            || apiKey.trim().isEmpty();
    }
}
