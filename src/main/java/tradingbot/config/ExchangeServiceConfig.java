package tradingbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import tradingbot.service.FuturesExchangeService;
import tradingbot.service.RateLimitedBinanceFuturesService;

/**
 * Configuration for exchange services with rate limiting
 */
@Configuration
public class ExchangeServiceConfig {

    @Value("${trading.binance.api.key:YOUR_BINANCE_API_KEY}")
    private String binanceApiKey;

    @Value("${trading.binance.api.secret:YOUR_BINANCE_API_SECRET}")
    private String binanceApiSecret;

    /**
     * Primary exchange service bean with rate limiting
     * This will be used throughout the application instead of direct BinanceFuturesService
     */
    @Bean
    @Primary
    FuturesExchangeService futuresExchangeService() {
        return new RateLimitedBinanceFuturesService(binanceApiKey, binanceApiSecret);
    }
}
