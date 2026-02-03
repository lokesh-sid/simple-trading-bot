package tradingbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.service.RateLimitedBinanceFuturesService;

/**
 * Configuration for exchange services with rate limiting
 */
@Configuration
public class ExchangeServiceConfig {

    @Value("${trading.binance.api.key:YOUR_BINANCE_API_KEY}")
    private String binanceApiKey;

    @Value("${trading.binance.api.secret:YOUR_BINANCE_API_SECRET}")
    private String binanceApiSecret;

    @Value("${trading.exchange.provider:binance}")
    private String provider;

    @Value("${trading.bybit.api.key:}")
    private String bybitApiKey;

    @Value("${trading.bybit.api.secret:}")
    private String bybitApiSecret;

    @Value("${trading.bybit.domain:MAINNET_DOMAIN}")
    private String bybitDomain;

    /**
     * Primary exchange service bean with rate limiting
     * This will be used throughout the application instead of direct BinanceFuturesService
     */
    @Bean
    @Primary
    FuturesExchangeService futuresExchangeService() {
        if ("paper".equalsIgnoreCase(provider)) {
            return new tradingbot.bot.service.PaperFuturesExchangeService();
        }
        if ("bybit".equalsIgnoreCase(provider)) {
             String baseUrl = "TESTNET_DOMAIN".equals(bybitDomain) 
                    ? "https://api-testnet.bybit.com"
                    : "https://api.bybit.com";
            return new tradingbot.bot.service.RateLimitedBybitFuturesService(bybitApiKey, bybitApiSecret, baseUrl);
        }
        return new RateLimitedBinanceFuturesService(binanceApiKey, binanceApiSecret);
    }
}
