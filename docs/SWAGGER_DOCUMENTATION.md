# Swagger API Documentation

## Overview

The Simple Trading Bot API provides comprehensive REST endpoints for managing a cryptocurrency futures trading bot with rate limiting and resilience features. The API includes Swagger/OpenAPI documentation for easy testing and integration.

## Access Swagger Documentation

Once the application is running, you can access the Swagger UI at:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs
- **OpenAPI YAML**: http://localhost:8080/api-docs.yaml

## API Endpoints

### Trading Bot Controller (`/api/simple-trading-bot`)

#### 1. Start Trading Bot
- **Endpoint**: `POST /api/simple-trading-bot/start`
- **Description**: Starts the trading bot with specified direction and trading mode
- **Parameters**:
  - `direction` (required): Trading direction - `LONG` or `SHORT`
  - `paper` (optional): Enable paper trading mode - `true` or `false` (default: false)

**Example Request:**
```bash
curl -X POST "http://localhost:8080/api/simple-trading-bot/start?direction=LONG&paper=true"
```

**Example Response:**
```json
"Trading bot started in LONG mode (paper)"
```

#### 2. Stop Trading Bot
- **Endpoint**: `POST /api/simple-trading-bot/stop`
- **Description**: Stops the currently running trading bot

**Example Request:**
```bash
curl -X POST "http://localhost:8080/api/simple-trading-bot/stop"
```

**Example Response:**
```json
"Trading bot stopped"
```

#### 3. Get Bot Status
- **Endpoint**: `GET /api/simple-trading-bot/status`
- **Description**: Returns the current status of the trading bot

**Example Request:**
```bash
curl -X GET "http://localhost:8080/api/simple-trading-bot/status"
```

**Example Response:**
```json
"Bot Status: Running - LONG mode on BTCUSDT"
```

#### 4. Configure Bot
- **Endpoint**: `PUT /api/simple-trading-bot/configure`
- **Description**: Updates the trading bot configuration with new parameters
- **Request Body**: TradingConfig JSON object

**Example Request:**
```bash
curl -X POST "http://localhost:8080/api/simple-trading-bot/configure" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "BTCUSDT",
    "leverage": 5,
    "trailingStopPercent": 2.0,
    "riskPercentage": 1.0,
    "lookbackPeriodRsi": 14,
    "macdFastPeriod": 12,
    "macdSlowPeriod": 26,
    "macdSignalPeriod": 9
  }'
```

**Example Response:**
```json
"Configuration updated"
```

#### 5. Set Dynamic Leverage
- **Endpoint**: `POST /api/simple-trading-bot/leverage`
- **Description**: Updates the trading bot's leverage multiplier
- **Parameters**:
  - `leverage` (required): Leverage multiplier (1-100)

**Example Request:**
```bash
curl -X POST "http://localhost:8080/api/simple-trading-bot/leverage?leverage=10"
```

**Example Response:**
```json
"Leverage set to 10x"
```

#### 6. Enable/Disable Sentiment Analysis
- **Endpoint**: `POST /api/simple-trading-bot/sentiment`
- **Description**: Toggles sentiment analysis feature for the trading bot
- **Parameters**:
  - `enable` (required): Enable or disable sentiment analysis - `true` or `false`

**Example Request:**
```bash
curl -X POST "http://localhost:8080/api/simple-trading-bot/sentiment?enable=true"
```

**Example Response:**
```json
"Sentiment analysis enabled"
```

### Resilience Controller (`/api/resilience`)

#### 1. Get Rate Limiter Metrics
- **Endpoint**: `GET /api/resilience/rate-limiters`
- **Description**: Returns current metrics for all rate limiters (trading, market, account)

**Example Request:**
```bash
curl -X GET "http://localhost:8080/api/resilience/rate-limiters"
```

**Example Response:**
```json
{
  "trading": {
    "availablePermissions": 8,
    "numberOfWaitingThreads": 0
  },
  "market": {
    "availablePermissions": 30,
    "numberOfWaitingThreads": 0
  },
  "account": {
    "availablePermissions": 2,
    "numberOfWaitingThreads": 0
  }
}
```

#### 2. Get Circuit Breaker Metrics
- **Endpoint**: `GET /api/resilience/circuit-breaker`
- **Description**: Returns circuit breaker status and metrics

**Example Request:**
```bash
curl -X GET "http://localhost:8080/api/resilience/circuit-breaker"
```

**Example Response:**
```json
{
  "state": "CLOSED",
  "failureRate": 0.0,
  "numberOfBufferedCalls": 5,
  "numberOfFailedCalls": 0,
  "numberOfSuccessfulCalls": 5,
  "numberOfNotPermittedCalls": 0
}
```

#### 3. Get Retry Metrics
- **Endpoint**: `GET /api/resilience/retry`
- **Description**: Returns retry mechanism metrics

**Example Request:**
```bash
curl -X GET "http://localhost:8080/api/resilience/retry"
```

**Example Response:**
```json
{
  "numberOfSuccessfulCallsWithoutRetryAttempt": 10,
  "numberOfSuccessfulCallsWithRetryAttempt": 2,
  "numberOfFailedCallsWithRetryAttempt": 0,
  "numberOfFailedCallsWithoutRetryAttempt": 0
}
```

#### 4. Get All Metrics
- **Endpoint**: `GET /api/resilience/metrics`
- **Description**: Returns all resilience metrics in one call

**Example Request:**
```bash
curl -X GET "http://localhost:8080/api/resilience/metrics"
```

#### 5. Health Check
- **Endpoint**: `GET /api/resilience/health`
- **Description**: Returns health status for resilience components

**Example Request:**
```bash
curl -X GET "http://localhost:8080/api/resilience/health"
```

**Example Response:**
```json
{
  "circuitBreaker": {
    "healthy": true,
    "state": "CLOSED"
  },
  "rateLimiters": {
    "healthy": true
  },
  "overall": {
    "healthy": true
  }
}
```

## Response Codes

### Success Codes
- **200 OK**: Request successful
- **201 Created**: Resource created successfully

### Error Codes
- **400 Bad Request**: Invalid request parameters or body
- **404 Not Found**: Endpoint not found
- **500 Internal Server Error**: Server error occurred

## Configuration

### OpenAPI Configuration
The API documentation can be customized via `application.properties`:

```properties
# OpenAPI/Swagger Documentation Configuration
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operationsSorter=method
springdoc.swagger-ui.tagsSorter=alpha
springdoc.swagger-ui.tryItOutEnabled=true
springdoc.show-actuator=false
```

## Rate Limiting

All API endpoints are protected by rate limiting:

- **Trading Operations**: 8 requests per 10 seconds
- **Market Data**: 30 requests per second  
- **Account Data**: 2 requests per second

Rate limiting headers are included in responses:
- `X-RateLimit-Remaining`: Remaining requests in current window
- `X-RateLimit-Reset`: Time when rate limit resets

## Authentication

Currently, the API does not require authentication for management endpoints. In production, consider adding:

- **API Keys**: For programmatic access
- **JWT Tokens**: For user session management
- **Rate Limiting per User**: Based on authentication

## Testing with Swagger UI

1. **Start the application**: `./gradlew bootRun`
2. **Open Swagger UI**: http://localhost:8080/swagger-ui.html
3. **Explore endpoints**: Browse available operations
4. **Try it out**: Use the interactive interface to test API calls
5. **View responses**: See real-time response data and status codes

## Integration Examples

### Python
```python
import requests

# Start bot in paper trading mode
response = requests.put(
    "http://localhost:8080/api/simple-trading-bot/start",
    params={"direction": "LONG", "paper": True}
)
print(response.text)

# Get current status
status = requests.get("http://localhost:8080/api/simple-trading-bot/status")
print(status.text)
```

### JavaScript
```javascript
// Start bot in live trading mode
fetch('http://localhost:8080/api/simple-trading-bot/start?direction=SHORT&paper=false', {
  method: 'PUT'
})
.then(response => response.text())
.then(data => console.log(data));

// Get rate limiter metrics
fetch('http://localhost:8080/api/resilience/rate-limiters')
.then(response => response.json())
.then(data => console.log(data));
```

### Java
```java
// Using Spring RestTemplate
RestTemplate restTemplate = new RestTemplate();

// Start bot
String result = restTemplate.exchange(
    "http://localhost:8080/api/simple-trading-bot/start?direction=LONG&paper=true",
    HttpMethod.PUT,
    null,
    String.class
).getBody();

// Get metrics
Map<String, Object> metrics = restTemplate.getForObject(
    "http://localhost:8080/api/resilience/metrics",
    Map.class
);
```

---
*Generated by SpringDoc OpenAPI*
*Last Updated: August 29, 2025*
