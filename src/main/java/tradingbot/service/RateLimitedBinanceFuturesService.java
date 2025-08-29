package tradingbot.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Rate-limited wrapper for BinanceFuturesService using Resilience4j
 * 
 * This service implements different rate limiting strategies for different types of operations:
 * - Trading operations (orders, leverage): 8 requests per 10 seconds
 * - Market data operations: 30 requests per second  
 * - Account data operations: 2 requests per second
 * 
 * Also includes circuit breaker and retry mechanisms for enhanced reliability.
 */
public class RateLimitedBinanceFuturesService implements FuturesExchangeService {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitedBinanceFuturesService.class);
    
    private final BinanceFuturesService binanceService;
    
    public RateLimitedBinanceFuturesService(String apiKey, String apiSecret) {
        this.binanceService = new BinanceFuturesService(apiKey, apiSecret);
        logger.info("Rate-limited Binance Futures service initialized");
    }

    /**
     * Fetch OHLCV data with market data rate limiting
     */
    @Override
    @RateLimiter(name = "binance-market")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackFetchOhlcv")
    @Retry(name = "binance-api")
    public List<BinanceFuturesService.Candle> fetchOhlcv(String symbol, String timeframe, int limit) {
        logger.debug("Fetching OHLCV data for {} with timeframe {} and limit {}", symbol, timeframe, limit);
        return binanceService.fetchOhlcv(symbol, timeframe, limit);
    }

    /**
     * Get current price with market data rate limiting
     */
    @Override
    @RateLimiter(name = "binance-market")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackGetCurrentPrice")
    @Retry(name = "binance-api")
    public double getCurrentPrice(String symbol) {
        logger.debug("Fetching current price for {}", symbol);
        return binanceService.getCurrentPrice(symbol);
    }

    /**
     * Get margin balance with account data rate limiting
     */
    @Override
    @RateLimiter(name = "binance-account")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackGetMarginBalance")
    @Retry(name = "binance-api")
    public double getMarginBalance() {
        logger.debug("Fetching margin balance");
        return binanceService.getMarginBalance();
    }

    /**
     * Set leverage with trading operations rate limiting
     */
    @Override
    @RateLimiter(name = "binance-trading")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackSetLeverage")
    @Retry(name = "binance-api")
    public void setLeverage(String symbol, int leverage) {
        logger.info("Setting leverage to {}x for {}", leverage, symbol);
        binanceService.setLeverage(symbol, leverage);
    }

    /**
     * Enter long position with trading operations rate limiting
     */
    @Override
    @RateLimiter(name = "binance-trading")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackEnterLongPosition")
    @Retry(name = "binance-api")
    public void enterLongPosition(String symbol, double tradeAmount) {
        logger.info("Entering long position: {} {} at market price", tradeAmount, symbol);
        binanceService.enterLongPosition(symbol, tradeAmount);
    }

    /**
     * Enter short position with trading operations rate limiting
     */
    @Override
    @RateLimiter(name = "binance-trading")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackEnterShortPosition")
    @Retry(name = "binance-api")
    public void enterShortPosition(String symbol, double tradeAmount) {
        logger.info("Entering short position: {} {} at market price", tradeAmount, symbol);
        binanceService.enterShortPosition(symbol, tradeAmount);
    }

    /**
     * Exit long position with trading operations rate limiting
     */
    @Override
    @RateLimiter(name = "binance-trading")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackExitLongPosition")
    @Retry(name = "binance-api")
    public void exitLongPosition(String symbol, double tradeAmount) {
        logger.info("Exiting long position: {} {} at market price", tradeAmount, symbol);
        binanceService.exitLongPosition(symbol, tradeAmount);
    }

    /**
     * Exit short position with trading operations rate limiting
     */
    @Override
    @RateLimiter(name = "binance-trading")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackExitShortPosition")
    @Retry(name = "binance-api")
    public void exitShortPosition(String symbol, double tradeAmount) {
        logger.info("Exiting short position: {} {} at market price", tradeAmount, symbol);
        binanceService.exitShortPosition(symbol, tradeAmount);
    }

    // Fallback methods for circuit breaker

    /**
     * Fallback method for fetchOhlcv when circuit breaker is open
     */
    public List<BinanceFuturesService.Candle> fallbackFetchOhlcv(String symbol, String timeframe, int limit, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to fetch OHLCV data for {} - {}", symbol, ex.getMessage());
        throw new RuntimeException("Binance API is currently unavailable. Please try again later.", ex);
    }

    /**
     * Fallback method for getCurrentPrice when circuit breaker is open
     */
    public double fallbackGetCurrentPrice(String symbol, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to fetch current price for {} - {}", symbol, ex.getMessage());
        throw new RuntimeException("Binance API is currently unavailable. Please try again later.", ex);
    }

    /**
     * Fallback method for getMarginBalance when circuit breaker is open
     */
    public double fallbackGetMarginBalance(Exception ex) {
        logger.error("Circuit breaker fallback: Failed to fetch margin balance - {}", ex.getMessage());
        throw new RuntimeException("Binance API is currently unavailable. Please try again later.", ex);
    }

    /**
     * Fallback method for setLeverage when circuit breaker is open
     */
    public void fallbackSetLeverage(String symbol, int leverage, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to set leverage for {} - {}", symbol, ex.getMessage());
        throw new RuntimeException("Unable to set leverage. Binance API is currently unavailable.", ex);
    }

    /**
     * Fallback method for enterLongPosition when circuit breaker is open
     */
    public void fallbackEnterLongPosition(String symbol, double tradeAmount, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to enter long position for {} - {}", symbol, ex.getMessage());
        throw new RuntimeException("Unable to enter long position. Binance API is currently unavailable.", ex);
    }

    /**
     * Fallback method for enterShortPosition when circuit breaker is open
     */
    public void fallbackEnterShortPosition(String symbol, double tradeAmount, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to enter short position for {} - {}", symbol, ex.getMessage());
        throw new RuntimeException("Unable to enter short position. Binance API is currently unavailable.", ex);
    }

    /**
     * Fallback method for exitLongPosition when circuit breaker is open
     */
    public void fallbackExitLongPosition(String symbol, double tradeAmount, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to exit long position for {} - {}", symbol, ex.getMessage());
        throw new RuntimeException("Unable to exit long position. Binance API is currently unavailable.", ex);
    }

    /**
     * Fallback method for exitShortPosition when circuit breaker is open
     */
    public void fallbackExitShortPosition(String symbol, double tradeAmount, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to exit short position for {} - {}", symbol, ex.getMessage());
        throw new RuntimeException("Unable to exit short position. Binance API is currently unavailable.", ex);
    }
}
