package tradingbot;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.FuturesTradingBot.BotParams;
import tradingbot.bot.TradeDirection;
import tradingbot.config.TradingConfig;
import tradingbot.service.FuturesExchangeService;
import tradingbot.service.RateLimitedBinanceFuturesService;
import tradingbot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.strategy.calculator.IndicatorCalculator;
import tradingbot.strategy.exit.LiquidationRiskExit;
import tradingbot.strategy.exit.MACDExit;
import tradingbot.strategy.exit.PositionExitCondition;
import tradingbot.strategy.exit.TrailingStopExit;
import tradingbot.strategy.indicator.BollingerBandsIndicator;
import tradingbot.strategy.indicator.MACDTechnicalIndicator;
import tradingbot.strategy.indicator.RSITechnicalIndicator;
import tradingbot.strategy.indicator.TechnicalIndicator;
import tradingbot.strategy.tracker.TrailingStopTracker;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class SimpleTradingBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimpleTradingBotApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public FuturesExchangeService exchangeService(
            @Value("${trading.binance.api.key}") String apiKey,
            @Value("${trading.binance.api.secret}") String apiSecret) {
        String exchange = System.getProperty("exchange", "binance"); // Default to Binance
        if ("binance".equals(exchange)) {
            return new RateLimitedBinanceFuturesService(apiKey, apiSecret);
        } else {
            throw new IllegalArgumentException("Unsupported exchange: " + exchange);
        }
    }

    @Bean
    public SentimentAnalyzer sentimentAnalyzer(RestTemplate restTemplate) {
        return new SentimentAnalyzer(restTemplate);
    }

    @Bean
    public FuturesTradingBot tradingBot(
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
        boolean skipLeverageInit = "YOUR_BINANCE_API_KEY".equals(apiKey) || apiKey == null || apiKey.trim().isEmpty();
        
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
}