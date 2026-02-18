package tradingbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.service.PaperFuturesExchangeService;
import tradingbot.bot.service.RateLimitedBinanceFuturesService;
import tradingbot.bot.service.RateLimitedBybitFuturesService;

/**
 * Configuration for exchange services with rate limiting.
 * Central registration point for all supported exchange providers.
 */
@Configuration
public class ExchangeServiceConfig {

    private static final String TESTNET_DOMAIN_VALUE = "TESTNET_DOMAIN";

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
     * Primary exchange service bean with rate limiting.
     * This will be used throughout the application instead of direct BinanceFuturesService.
     */
    @Bean
    @Primary
    FuturesExchangeService futuresExchangeService(EventPublisher eventPublisher) {
        return switch (provider.toLowerCase()) {
            case "paper" -> new PaperFuturesExchangeService();
            case "bybit" -> {
                String baseUrl = TESTNET_DOMAIN_VALUE.equals(bybitDomain)
                    ? "https://api-testnet.bybit.com"
                    : "https://api.bybit.com";
                yield new RateLimitedBybitFuturesService(
                    bybitApiKey, bybitApiSecret, baseUrl, eventPublisher);
            }
            case "binance" -> new RateLimitedBinanceFuturesService(
                binanceApiKey, binanceApiSecret, eventPublisher);
            // TODO [Phase 3]: Add dYdX v4, OKX, Gate.io via XChange adapter
            default -> throw new IllegalArgumentException(
                "Unknown exchange provider: " + provider
                + ". Valid values: binance, bybit, paper");
        };
    }
}
