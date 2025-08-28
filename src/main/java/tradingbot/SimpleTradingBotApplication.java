package tradingbot;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.FuturesTradingBot.BotParams;
import tradingbot.bot.TradeDirection;
import tradingbot.config.TradingConfig;
import tradingbot.service.BinanceFuturesService;
import tradingbot.service.FuturesExchangeService;
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
    public FuturesExchangeService exchangeService() {
        String exchange = System.getProperty("exchange", "binance"); // Default to Binance
        if ("binance".equals(exchange)) {
            return new BinanceFuturesService("YOUR_BINANCE_API_KEY", "YOUR_BINANCE_API_SECRET");
        } else if ("bybit".equals(exchange)) {
            // return new BybitFuturesService("YOUR_BYBIT_API_KEY", "YOUR_BYBIT_API_SECRET");
            return null;
        } else {
            throw new IllegalArgumentException("Unsupported exchange: " + exchange);
        }
    }

    @Bean
    public SentimentAnalyzer sentimentAnalyzer(RestTemplate restTemplate) {
        return new SentimentAnalyzer(restTemplate);
    }

    @Bean
    public FuturesTradingBot tradingBot(FuturesExchangeService exchangeService, SentimentAnalyzer sentimentAnalyzer) {
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
        IndicatorCalculator indicatorCalculator = new IndicatorCalculator(exchangeService, indicators);
        TrailingStopTracker trailingStopTracker = new TrailingStopTracker(exchangeService, config.getTrailingStopPercent());
        List<PositionExitCondition> exitConditions = Arrays.asList(
                new TrailingStopExit(trailingStopTracker),
                new MACDExit(indicatorCalculator),
                new LiquidationRiskExit(exchangeService, trailingStopTracker, config)
        );
        // Use BotParams to pass all required parameters
        BotParams botParams = new BotParams.Builder()
            .exchangeService(exchangeService)
            .indicatorCalculator(indicatorCalculator)
            .trailingStopTracker(trailingStopTracker)
            .sentimentAnalyzer(sentimentAnalyzer)
            .exitConditions(exitConditions)
            .config(config)
            .tradeDirection(TradeDirection.LONG)
            .testMode(false) // or true, depending on your needs
            .build();
        return new FuturesTradingBot(botParams);
    }
}