package tradingbot.bot.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Rate-limited wrapper for BybitFuturesService using Resilience4j
 * 
 * Applies:
 * - Rate limiting to comply with Bybit API limits
 * - Circuit breaker to prevent cascading failures
 * - Retry logic for transient errors
 * 
 * Bybit Rate Limits (V5 Unified Account):
 * - Trading: 10 requests per second
 * - Market Data: 50 requests per second
 * - Account: 5 requests per second
 */
public class RateLimitedBybitFuturesService implements FuturesExchangeService {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitedBybitFuturesService.class);
    
    private final BybitFuturesService delegate;
    
    public RateLimitedBybitFuturesService(String apiKey, String apiSecret) {
        this.delegate = new BybitFuturesService(apiKey, apiSecret);
        logger.info("Rate-limited Bybit Futures Service initialized");
    }
    
    public RateLimitedBybitFuturesService(String apiKey, String apiSecret, String baseDomain) {
        this.delegate = new BybitFuturesService(apiKey, apiSecret, baseDomain);
        logger.info("Rate-limited Bybit Futures Service initialized (domain: {})", baseDomain);
    }
    
    @Override
    @RateLimiter(name = "bybit-market", fallbackMethod = "fetchOhlcvFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-market")
    public List<BinanceFuturesService.Candle> fetchOhlcv(String symbol, String timeframe, int limit) {
        logger.debug("Rate-limited OHLCV fetch for {}", symbol);
        return delegate.fetchOhlcv(symbol, timeframe, limit);
    }
    
    @Override
    @RateLimiter(name = "bybit-market", fallbackMethod = "getCurrentPriceFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-market")
    public double getCurrentPrice(String symbol) {
        logger.debug("Rate-limited price fetch for {}", symbol);
        return delegate.getCurrentPrice(symbol);
    }
    
    @Override
    @RateLimiter(name = "bybit-account", fallbackMethod = "getMarginBalanceFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-account")
    public double getMarginBalance() {
        logger.debug("Rate-limited balance fetch");
        return delegate.getMarginBalance();
    }
    
    @Override
    @RateLimiter(name = "bybit-account", fallbackMethod = "setLeverageFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-account")
    public void setLeverage(String symbol, int leverage) {
        logger.info("Rate-limited leverage setting: {}x for {}", leverage, symbol);
        delegate.setLeverage(symbol, leverage);
    }
    
    @Override
    @RateLimiter(name = "bybit-trading", fallbackMethod = "enterLongPositionFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-trading")
    public void enterLongPosition(String symbol, double tradeAmount) {
        logger.info("Rate-limited LONG entry: {} units of {}", tradeAmount, symbol);
        delegate.enterLongPosition(symbol, tradeAmount);
    }
    
    @Override
    @RateLimiter(name = "bybit-trading", fallbackMethod = "enterShortPositionFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-trading")
    public void enterShortPosition(String symbol, double tradeAmount) {
        logger.info("Rate-limited SHORT entry: {} units of {}", tradeAmount, symbol);
        delegate.enterShortPosition(symbol, tradeAmount);
    }
    
    @Override
    @RateLimiter(name = "bybit-trading", fallbackMethod = "exitLongPositionFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-trading")
    public void exitLongPosition(String symbol, double tradeAmount) {
        logger.info("Rate-limited LONG exit: {} units of {}", tradeAmount, symbol);
        delegate.exitLongPosition(symbol, tradeAmount);
    }
    
    @Override
    @RateLimiter(name = "bybit-trading", fallbackMethod = "exitShortPositionFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-trading")
    public void exitShortPosition(String symbol, double tradeAmount) {
        logger.info("Rate-limited SHORT exit: {} units of {}", tradeAmount, symbol);
        delegate.exitShortPosition(symbol, tradeAmount);
    }
    
    // ==================== Fallback Methods ====================
    
    @SuppressWarnings("unused")
    private List<BinanceFuturesService.Candle> fetchOhlcvFallback(String symbol, String timeframe, int limit, Throwable t) {
        logger.error("Fallback triggered for fetchOhlcv: {}", t.getMessage());
        throw new RuntimeException("Failed to fetch OHLCV after retries: " + t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private double getCurrentPriceFallback(String symbol, Throwable t) {
        logger.error("Fallback triggered for getCurrentPrice: {}", t.getMessage());
        throw new RuntimeException("Failed to fetch price after retries: " + t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private double getMarginBalanceFallback(Throwable t) {
        logger.error("Fallback triggered for getMarginBalance: {}", t.getMessage());
        throw new RuntimeException("Failed to fetch balance after retries: " + t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private void setLeverageFallback(String symbol, int leverage, Throwable t) {
        logger.error("Fallback triggered for setLeverage: {}", t.getMessage());
        throw new RuntimeException("Failed to set leverage after retries: " + t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private void enterLongPositionFallback(String symbol, double tradeAmount, Throwable t) {
        logger.error("Fallback triggered for enterLongPosition: {}", t.getMessage());
        throw new RuntimeException("Failed to enter LONG position after retries: " + t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private void enterShortPositionFallback(String symbol, double tradeAmount, Throwable t) {
        logger.error("Fallback triggered for enterShortPosition: {}", t.getMessage());
        throw new RuntimeException("Failed to enter SHORT position after retries: " + t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private void exitLongPositionFallback(String symbol, double tradeAmount, Throwable t) {
        logger.error("Fallback triggered for exitLongPosition: {}", t.getMessage());
        throw new RuntimeException("Failed to exit LONG position after retries: " + t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private void exitShortPositionFallback(String symbol, double tradeAmount, Throwable t) {
        logger.error("Fallback triggered for exitShortPosition: {}", t.getMessage());
        throw new RuntimeException("Failed to exit SHORT position after retries: " + t.getMessage(), t);
    }
}
