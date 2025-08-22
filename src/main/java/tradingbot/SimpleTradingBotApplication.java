package tradingbot;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import tradingbot.bot.LongFuturesTradingBot;
import tradingbot.config.TradingConfig;
import tradingbot.service.BinanceFuturesService;
import tradingbot.service.FuturesExchangeService;
import tradingbot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.strategy.calculator.IndicatorCalculator;
import tradingbot.strategy.exit.LiquidationRiskExit;
import tradingbot.strategy.exit.MACDExit;
import tradingbot.strategy.exit.PositionExitCondition;
import tradingbot.strategy.exit.RSIExit;
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
    public LongFuturesTradingBot tradingBot(FuturesExchangeService exchangeService) {
        TradingConfig config = new TradingConfig();
        TechnicalIndicator rsiIndicator = new RSITechnicalIndicator(config.getLookbackPeriodRsi());
        TechnicalIndicator macdIndicator = new MACDTechnicalIndicator(config.getMacdFastPeriod(), config.getMacdSlowPeriod(), config.getMacdSignalPeriod(), false);
        TechnicalIndicator macdSignalIndicator = new MACDTechnicalIndicator(config.getMacdFastPeriod(), config.getMacdSlowPeriod(), config.getMacdSignalPeriod(), true);
        TechnicalIndicator bbLowerIndicator = new BollingerBandsIndicator(config.getBbPeriod(), config.getBbStandardDeviation(), true);
        TechnicalIndicator bbUpperIndicator = new BollingerBandsIndicator(config.getBbPeriod(), config.getBbStandardDeviation(), false);
        IndicatorCalculator indicatorCalculator = new IndicatorCalculator(exchangeService, rsiIndicator, macdIndicator, macdSignalIndicator, bbLowerIndicator, bbUpperIndicator);
        TrailingStopTracker trailingStopTracker = new TrailingStopTracker(exchangeService, config.getTrailingStopPercent());
        SentimentAnalyzer sentimentAnalyzer = new SentimentAnalyzer(restTemplate());
        List<PositionExitCondition> exitConditions = Arrays.asList(
                new TrailingStopExit(trailingStopTracker),
                new RSIExit(indicatorCalculator, config.getRsiOverboughtThreshold()),
                new MACDExit(indicatorCalculator),
                new LiquidationRiskExit(exchangeService, trailingStopTracker, config)
        );
        return new LongFuturesTradingBot(exchangeService, indicatorCalculator, trailingStopTracker, sentimentAnalyzer, exitConditions, config);
    }
}