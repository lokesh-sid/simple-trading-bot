# Rate Limiting with Resilience4j Implementation Guide

## Overview

This implementation adds comprehensive rate limiting, circuit breaking, and retry mechanisms to the Simple Trading Bot using Resilience4j. This protects against hitting API rate limits and provides graceful degradation when the Binance API is experiencing issues.

## 🎯 Features Implemented

### 1. **Rate Limiting**
- **Trading Operations**: 8 requests per 10 seconds (orders, leverage changes)
- **Market Data**: 30 requests per second (price, OHLCV data)
- **Account Data**: 2 requests per second (balance, account info)

### 2. **Circuit Breaker**
- Opens when 50% of calls fail (minimum 5 calls)
- Stays open for 30 seconds before retrying
- Prevents cascading failures

### 3. **Retry Mechanism**
- Automatic retry up to 3 times
- 1-second initial wait time
- Retries on network issues and temporary API errors

### 4. **Monitoring & Metrics**
- Real-time metrics via REST endpoints
- Rate limiter status monitoring
- Circuit breaker health checks

## 📁 Files Added/Modified

### New Files Created

1. **`RateLimitedBinanceFuturesService.java`**
   - Wrapper service with rate limiting annotations
   - Implements all FuturesExchangeService methods
   - Includes fallback methods for circuit breaker

2. **`ResilienceConfig.java`**
   - Configuration for rate limiters, circuit breaker, and retry
   - Defines different rate limits for different operation types
   - Bean definitions for Spring injection

3. **`ExchangeServiceConfig.java`**
   - Factory for creating rate-limited exchange service
   - Primary bean configuration for dependency injection

4. **`ResilienceController.java`**
   - REST endpoints for monitoring rate limiting metrics
   - Health check endpoints
   - Real-time status monitoring

### Modified Files

1. **`build.gradle`**
   - Added Resilience4j dependencies
   - Added Spring Boot AOP support

2. **`application.properties`**
   - Added Resilience4j configuration properties
   - Added Binance API key configuration
   - Comprehensive rate limiting settings

## 🚀 Configuration Details

### Rate Limiter Configuration

#### Trading Operations Rate Limiter
```properties
resilience4j.ratelimiter.instances.binance-trading.limit-for-period=8
resilience4j.ratelimiter.instances.binance-trading.limit-refresh-period=10s
resilience4j.ratelimiter.instances.binance-trading.timeout-duration=5s
```

#### Market Data Rate Limiter
```properties
resilience4j.ratelimiter.instances.binance-market.limit-for-period=30
resilience4j.ratelimiter.instances.binance-market.limit-refresh-period=1s
resilience4j.ratelimiter.instances.binance-market.timeout-duration=3s
```

#### Account Data Rate Limiter
```properties
resilience4j.ratelimiter.instances.binance-account.limit-for-period=2
resilience4j.ratelimiter.instances.binance-account.limit-refresh-period=1s
resilience4j.ratelimiter.instances.binance-account.timeout-duration=5s
```

### Circuit Breaker Configuration
```properties
resilience4j.circuitbreaker.instances.binance-api.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.binance-api.minimum-number-of-calls=5
resilience4j.circuitbreaker.instances.binance-api.sliding-window-type=count_based
resilience4j.circuitbreaker.instances.binance-api.sliding-window-size=10
resilience4j.circuitbreaker.instances.binance-api.wait-duration-in-open-state=30s
resilience4j.circuitbreaker.instances.binance-api.permitted-number-of-calls-in-half-open-state=3
```

## 📊 Monitoring Endpoints

### Rate Limiter Metrics
```http
GET /api/resilience/rate-limiters
```
Returns current rate limiter status including available permissions and waiting threads.

### Circuit Breaker Metrics
```http
GET /api/resilience/circuit-breaker
```
Returns circuit breaker state, failure rate, and call statistics.

### Retry Metrics
```http
GET /api/resilience/retry
```
Returns retry attempt statistics and success/failure counts.

### All Metrics
```http
GET /api/resilience/metrics
```
Returns comprehensive metrics for all resilience components.

### Health Check
```http
GET /api/resilience/health
```
Returns health status of all resilience components.

## 🔧 Usage Examples

### Automatic Rate Limiting
The rate limiting is transparent to the existing code. The `RateLimitedBinanceFuturesService` automatically applies rate limits:

```java
// This call is automatically rate-limited
double price = futuresExchangeService.getCurrentPrice("BTCUSDT");

// This call is also automatically rate-limited with trading operation limits
futuresExchangeService.enterLongPosition("BTCUSDT", 0.001);
```

### Monitoring Rate Limits
```bash
# Check current rate limiter status
curl http://localhost:8080/api/resilience/rate-limiters

# Check circuit breaker health
curl http://localhost:8080/api/resilience/circuit-breaker

# Overall health check
curl http://localhost:8080/api/resilience/health
```

## 🎯 Benefits

### 1. **API Protection**
- Prevents hitting Binance rate limits
- Reduces risk of IP bans
- Ensures consistent trading performance

### 2. **Reliability**
- Circuit breaker prevents cascading failures
- Automatic retries handle temporary issues
- Graceful degradation during API problems

### 3. **Monitoring**
- Real-time visibility into API usage
- Early warning for rate limit issues
- Performance metrics and health checks

### 4. **Performance**
- Optimized rate limits for different operation types
- Prevents unnecessary waiting for light operations
- Maintains trading speed while staying within limits

## 🛠️ Configuration Guidelines

### Binance API Rate Limits Reference
- **Order endpoints**: 100 requests per 10 seconds per IP
- **Market data endpoints**: 2400 requests per minute per IP (40 requests/second)
- **Account endpoints**: 180 requests per minute per IP (3 requests/second)

### Recommended Settings
Our configuration uses conservative limits (75% of Binance limits) to provide safety margin:

1. **Trading Operations**: 8/10s (vs Binance 100/10s)
2. **Market Data**: 30/s (vs Binance 40/s)
3. **Account Data**: 2/s (vs Binance 3/s)

### Customization
To adjust rate limits, modify the values in `application.properties`:

```properties
# Increase trading rate limit for high-frequency trading
resilience4j.ratelimiter.instances.binance-trading.limit-for-period=12

# Decrease market data rate limit for conservative approach
resilience4j.ratelimiter.instances.binance-market.limit-for-period=20
```

## 🚨 Error Handling

### Rate Limit Exceeded
When rate limits are exceeded:
1. Requests wait in queue (up to timeout duration)
2. After timeout, `RateLimiterRequestNotPermitted` exception is thrown
3. Circuit breaker may open if failures persist

### Circuit Breaker Open
When circuit breaker is open:
1. Requests immediately fail with fallback response
2. Detailed error messages are logged
3. System automatically retries after wait duration

### Retry Exhausted
When all retry attempts are exhausted:
1. Original exception is propagated
2. Detailed retry metrics are available
3. Circuit breaker tracks the failure

## 🔍 Troubleshooting

### Common Issues

1. **Rate Limits Too Conservative**
   - Symptom: Slow trading performance
   - Solution: Increase rate limits in configuration

2. **Circuit Breaker Opens Frequently**
   - Symptom: Trading operations fail regularly
   - Solution: Check Binance API status, adjust failure threshold

3. **High Number of Retries**
   - Symptom: Slow response times
   - Solution: Check network connectivity, API status

### Monitoring Commands
```bash
# Check if rate limiters are healthy
curl http://localhost:8080/api/resilience/health | jq '.rateLimiters.healthy'

# Check circuit breaker state
curl http://localhost:8080/api/resilience/circuit-breaker | jq '.state'

# Monitor retry metrics
curl http://localhost:8080/api/resilience/retry | jq
```

## 🎉 Next Steps

1. **Environment-Specific Configuration**: Set different rate limits for development, staging, and production
2. **Alerting**: Integrate with monitoring systems for alerts on rate limit violations
3. **Metrics Dashboard**: Create a dashboard for visualizing rate limiting metrics
4. **Dynamic Configuration**: Allow runtime adjustment of rate limits via management endpoints

This implementation provides a robust foundation for reliable API interaction while maintaining optimal trading performance.
