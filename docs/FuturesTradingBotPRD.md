# Product Requirements Document (PRD): Futures Trading Bot with Leverage, Trailing Stop-Loss, Short/Long, and Paper Trading

## 1. Overview
### 1.1 Product Context
This PRD documents the **FuturesTradingBot** component and its ecosystem within the **SimpleTradingBotApplication**. While the application uses "Simple" in its name to indicate ease of use, this document focuses on the technical futures trading implementation details.

**Component Hierarchy:**
- `SimpleTradingBotApplication`: Main Spring Boot application (user-facing)
- `FuturesTradingBot`: Core trading engine (technical implementation)
- `TradingBotController`: REST API layer
- Supporting services and components

### 1.2 Purpose
The Futures Trading Bot is agent-based: each bot instance implements the `TradingAgent` interface and can be managed by an `AgentManager` for multi-bot deployments. The bot executes both long and short positions in cryptocurrency futures markets (e.g., BTC/USDT on Binance Futures) using configurable leverage (default 3x). It capitalizes on bullish and bearish price movements using technical indicators (RSI, MACD, Bollinger Bands) across daily and weekly timeframes, with a trailing stop-loss to protect profits during favorable trends. The bot supports dynamic leverage adjustments, optional sentiment analysis from X posts, and paper trading mode for safe strategy testing. All features are exposed via RESTful APIs using Spring Boot for remote management. A Redis-based caching mechanism optimizes performance by reducing API calls while ensuring data freshness through event-based invalidation. AWS deployment is supported via Docker and ECS task definition. The architecture is extensible and testable.

### 1.3 Target Audience
- Cryptocurrency traders with experience in futures trading.
- Developers comfortable with configuring API keys, running Spring Boot applications, and using REST clients.
- Users seeking automated trading with technical and sentiment-based strategies, and safe paper trading.

### 1.4 Key Features
- Agent-based: Each bot is an agent, enabling multi-symbol and multi-exchange deployments.
- Execute long and short positions with configurable leverage to amplify returns while managing risk.
- Use daily and weekly technical indicators to identify high-probability entry points.
- Implement a trailing stop-loss to maximize profits during uptrends and downtrends.
- Provide RESTful APIs for starting, stopping, configuring, and monitoring the bot.
- Support optional sentiment analysis from X posts to enhance entry decisions.
- Optimize performance with Redis caching and event-based cache invalidation.
- Support multiple exchanges (Binance Futures) for flexibility.
- Paper trading mode for safe strategy development and testing.
- Extensible indicator architecture for easy addition of new technical indicators.
- Ensure robust error handling, logging, and margin management.
- AWS-ready: Dockerfile and ECS task definition for cloud deployment.

## 2. Features and Requirements
### 2.1 Core Functionality (Agent-based)
#### 2.1.1 Trading Environment
- Exchange: Binance Futures, configurable via system property (e.g., -Dexchange=binance).
- Market Type: Futures market with USDT-margined contracts (e.g., BTC/USDT).
- Leverage: Configurable, default 3x, adjustable via REST API (1x to 125x, per exchange limits).
- Trade Amount: Configurable amount of base currency (e.g., 0.001 BTC) per trade.
- API Integration: Use Binance Futures Connector Java for futures trading, with rate limiting enabled.
- AgentManager: Supports registration and lifecycle management of multiple agents for multi-symbol or multi-exchange trading.
- Paper Trading: Simulate trades and margin using in-memory logic for safe testing and strategy development.

#### 2.1.2 Entry Conditions
Indicators and Timeframes:
- Daily Timeframe ("1d"):
  - RSI: ≤ 30 (oversold) for long, ≥ 70 (overbought) for short.
  - MACD: Bullish crossover (MACD > signal line) for long, bearish crossover (MACD < signal line) for short.
  - Bollinger Bands: Price ≤ 101% of the lower band for long, ≥ 99% of the upper band for short.
- Weekly Timeframe ("1w"):
  - RSI: < 70 (not overbought) for long, > 30 (not oversold) for short.

Sentiment Analysis (Optional):
- When enabled via REST API, require positive sentiment from X posts (score > 0.6) for long, negative sentiment (score < 0.4) for short.

Execution: Place market buy/sell orders for fast execution (or simulate in paper mode).

#### 2.1.3 Exit Conditions
- Trailing Stop-Loss:
  - Close position if price falls 1% below the highest price since entry (long) or rises 1% above the lowest price since entry (short).
- Other Exit Conditions:
  - Daily RSI ≥ 50 (neutral) for long, ≤ 50 for short.
  - Daily MACD bearish crossover (MACD < signal line) for long, bullish crossover (MACD > signal line) for short.
  - High liquidation risk (price within 5% of estimated liquidation price).

#### 2.1.4 Monitoring and Risk Management
- Interval: Check conditions every 15 minutes (900 seconds).
- Margin Balance Check: Verify sufficient USDT margin before trades.
- Liquidation Risk Check: Monitor price against estimated liquidation price.
- Error Handling: Log and handle API errors, insufficient data, and network issues.

#### 2.1.5 RESTful APIs
All endpoints use structured JSON request/response DTOs with method-specific response types for better type safety and API clarity.

**Request DTOs:**
- `BotStartRequest`: Contains direction (LONG/SHORT) and paper trading mode
- `LeverageUpdateRequest`: Contains leverage value with validation (1-100)
- `SentimentUpdateRequest`: Contains boolean enable/disable flag
- `TradingConfig`: Full trading configuration object

**Response DTOs:**
- `StartBotResponse`: Returns bot status, mode, direction, and timestamp
- `StopBotResponse`: Returns stop confirmation, final position status, and timing
- `BotStatusResponse`: Returns complete bot status information
- `ConfigUpdateResponse`: Returns updated configuration summary
- `LeverageUpdateResponse`: Returns old/new leverage values and update time
- `SentimentUpdateResponse`: Returns previous/current sentiment status
- `ErrorResponse`: Standardized error structure with error codes and details

**Endpoints:**
- **POST** `/api/simple-trading-bot/start`: Start the bot with `BotStartRequest` JSON body
- **PUT** `/api/simple-trading-bot/stop`: Stop the bot and close open positions
- **GET** `/api/simple-trading-bot/status`: Get complete bot status
- **POST** `/api/simple-trading-bot/configure`: Update configuration with `TradingConfig` JSON body
- **POST** `/api/simple-trading-bot/leverage`: Set dynamic leverage with `LeverageUpdateRequest` JSON body
- **POST** `/api/simple-trading-bot/sentiment`: Enable/disable sentiment analysis with `SentimentUpdateRequest` JSON body

**API Features:**
- Jakarta Bean Validation for request validation
- Swagger/OpenAPI 3 documentation with auto-generated schemas
- Method-specific response types for better type safety
- Standardized error handling with structured error codes
- Content-Type: application/json for all requests/responses

AgentManager APIs (future): Register, start, stop, and monitor multiple agents.

#### 2.1.6 Caching
- Mechanism: Use Redis to cache indicator values (RSI, MACD, Bollinger Bands) with a hybrid approach:
  - TTL: 2 minutes for daily indicators, 30 minutes for weekly indicators.
  - Event-Based Invalidation: Invalidate cache on new candle formation or significant price change (>1%).

#### 2.1.7 Logging
- Log metrics: price, daily RSI, MACD, signal, Bollinger Bands, weekly RSI, highest/lowest price.
- Log actions: trade entries/exits, profit/loss, errors, cache invalidations.

### 2.2 API Architecture and Data Transfer Objects (DTOs)

#### 2.2.1 Request DTOs
**Package:** `tradingbot.bot.controller.dto.request`
- **BotStartRequest**: 
  - `direction`: TradeDirection enum (LONG/SHORT) with validation
  - `paper`: Boolean flag for paper trading mode
- **LeverageUpdateRequest**: 
  - `leverage`: Double with range validation (1.0-100.0)
- **SentimentUpdateRequest**: 
  - `enable`: Boolean flag for sentiment analysis toggle

#### 2.2.2 Response DTOs  
**Package:** `tradingbot.bot.controller.dto.response`
- **StartBotResponse**: Returns startup confirmation with bot status, mode, direction, timestamp
- **StopBotResponse**: Returns stop confirmation with final position status and timing info
- **BotStatusResponse**: Complete bot status including running state, position, leverage, sentiment
- **ConfigUpdateResponse**: Configuration update confirmation with updated values
- **LeverageUpdateResponse**: Leverage change details with old/new values and timestamp
- **SentimentUpdateResponse**: Sentiment toggle confirmation with previous/current status
- **ErrorResponse**: Standardized error structure with error code, message, details, timestamp

#### 2.2.3 Validation and Documentation
- **Jakarta Bean Validation**: Request DTOs include validation annotations (@NotNull, @DecimalMin, @DecimalMax)
- **Jackson Serialization**: All DTOs properly annotated for JSON serialization/deserialization
- **Swagger/OpenAPI**: Auto-generated API documentation from DTO schemas with examples
- **Type Safety**: Method-specific response types eliminate generic wrapper complexity

### 2.3 Technical Indicators
- RSI: Period 14, daily ≤ 30 for long entry, ≥ 70 for short entry, ≥ 50 for long exit, ≤ 50 for short exit, weekly < 70 for long, > 30 for short.
- MACD: Fast EMA 12, Slow EMA 26, Signal 9; bullish crossover for long entry, bearish for short entry, bearish for long exit, bullish for short exit.
- Bollinger Bands: Period 20, 2.0 standard deviations; price ≤ 101% of lower band for long entry, ≥ 99% of upper band for short entry.
- Data Source: Fetch 100 candles per timeframe ("1d" or "1w").
- Library: Use TA4J for indicator calculations.
- Extensible: Add new indicators by implementing `TechnicalIndicator` and registering in `IndicatorCalculator`.

### 2.4 Configuration Parameters
- Exchange: Default "binance", configurable via system property (e.g., -Dexchange=binance).
- Symbol: Default "BTCUSDT".
- Trade Amount: Default 0.001 BTC.
- Leverage: Default 3x, adjustable via API.
- Trailing Stop Percent: Default 1%.
- RSI Parameters: Period (14), oversold (30), overbought (70).
- MACD Parameters: Fast (12), Slow (26), Signal (9).
- Bollinger Bands Parameters: Period (20), Standard Deviation (2.0).
- Interval: Default 900 seconds.
- Sentiment Analysis: Disabled by default, enabled via API.
- Paper Trading: Disabled by default, enabled via API.

### 2.5 Non-Functional Requirements
- Reliability: Handle API failures and network issues gracefully.
- Security: Restrict API keys to trading permissions; disable withdrawals.
- Performance: Minimize API calls using Redis caching with TTL (2 min daily, 30 min weekly) and event-based invalidation on new candles or >1% price changes.
- Scalability: AgentManager supports multiple symbols and exchanges via configuration and agent registration.
- Extensibility: Easily add new indicators and strategies via configuration or code.
- Testability: All dependencies are injected for easy mocking and unit testing.

## 3. Technical Requirements
- Runtime: Java 21, Spring Boot 3.2.0.
- Dependencies:
  - spring-boot-starter-web: For RESTful services with method-specific DTOs.
  - spring-boot-starter-validation: For Jakarta Bean Validation.
  - spring-boot-starter-data-redis: For Redis caching.
  - springdoc-openapi-starter-webmvc-ui: For Swagger/OpenAPI 3 documentation.
  - binance-futures-connector-java: For Binance futures trading API.
  - ta4j-core: For technical indicators.
  - jackson-databind: For JSON parsing and DTO serialization.
  - spring-boot-starter-test, junit-jupiter, mockito-core: For testing.
- API Architecture:
  - Request/Response DTOs organized in separate packages.
  - Method-specific response types for each endpoint.
  - Standardized error handling with structured error codes.
  - Swagger/OpenAPI documentation auto-generated from DTO schemas.
- API Credentials: Valid API key/secret with trading permissions for the chosen exchange.
- Timeframes: Support for "1d" and "1w" OHLCV data.
- Redis: Redis server for caching indicator values.

## 4. Risks and Mitigation
- Risk: Liquidation due to volatility.
  - Mitigation: Use conservative leverage, monitor liquidation risk, trailing stop-loss.
- Risk: API rate limits or connectivity issues.
  - Mitigation: Enable rate limiting, use Redis caching, handle errors with retries.
- Risk: Stale cached data affecting trading decisions.
  - Mitigation: Hybrid caching with TTL and event-based invalidation (new candles, >1% price change).
- Risk: False signals from indicators or sentiment.
  - Mitigation: Combine daily/weekly indicators and optional sentiment analysis.
- Risk: Exchange-specific API differences causing inconsistencies.
  - Mitigation: Abstract exchange interactions through FuturesExchangeService interface.

## 5. Recent API Evolution

### 5.1 Migration from Query Parameters to JSON DTOs
The API has evolved from query parameter-based endpoints to structured JSON request/response DTOs:

**Previous (Query Parameters):**
```
POST /api/simple-trading-bot/start?direction=LONG&paper=true
POST /api/simple-trading-bot/leverage?leverage=10
```

**Current (JSON DTOs):**
```json
POST /api/simple-trading-bot/start
Content-Type: application/json
{
  "direction": "LONG",
  "paper": true  
}

POST /api/simple-trading-bot/leverage  
Content-Type: application/json
{
  "leverage": 10.0
}
```

### 5.2 Method-Specific Response Types
Replaced generic wrapper responses with method-specific response DTOs for better type safety, clearer API contracts, and improved Swagger documentation generation.

## 6. Future Enhancements
- Support COIN-margined contracts or other exchanges.
- Enhance sentiment analysis with real X API integration.
- Add dynamic trailing stop percentages or additional indicators.
- Implement WebSocket for real-time price updates.
- Expand AgentManager APIs for agent orchestration and monitoring.
- Add more advanced paper trading analytics and reporting.

## 7. Testing and Validation
- Test Environment: Binance Futures Testnet.
- Validation:
  - Verify entry/exit logic with historical data on the exchange.
  - Test leverage adjustments and sentiment toggling via REST APIs.
  - Validate Redis caching and invalidation (new candles, price changes).
  - Ensure no API errors during 24-hour runs.
- Success Criteria:
  - Correct trade execution based on technical and sentiment conditions.
  - Cache invalidation on new candles or significant price changes.
  - Robust error handling and continuous operation.

## 8. Disclaimer
This bot is for educational purposes only. Leveraged trading carries significant risks, including total loss of capital. Test thoroughly on a testnet and use at your own risk. This is not financial advice.
