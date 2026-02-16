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
    FuturesExchangeService futuresExchangeService(tradingbot.bot.messaging.EventPublisher eventPublisher,
                                                  @org.springframework.beans.factory.annotation.Autowired(required = false) 
                                                  tradingbot.bot.service.CcxtFuturesService ccxtService) {
        if ("ccxt".equalsIgnoreCase(provider)) {
            if (ccxtService != null) {
                return ccxtService;
            }
            // Fallback or throw error if bean not found
            throw new IllegalStateException("Provider is 'ccxt' but CcxtFuturesService bean is missing");
        }
        if ("paper".equalsIgnoreCase(provider)) {
            return new tradingbot.bot.service.PaperFuturesExchangeService();
        }
        if ("bybit".equalsIgnoreCase(provider)) {
             String baseUrl = "TESTNET_DOMAIN".equals(bybitDomain) 
                    ? "https://api-testnet.bybit.com"
                    : "https://api.bybit.com";
            return new tradingbot.bot.service.RateLimitedBybitFuturesService(bybitApiKey, bybitApiSecret, baseUrl, eventPublisher);
        }
        return new RateLimitedBinanceFuturesService(binanceApiKey, binanceApiSecret, eventPublisher);
    }
}
