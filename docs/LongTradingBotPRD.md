Product Requirements Document (PRD): Long Trading Bot with Leverage and Trailing Stop-Loss
1. Overview
1.1 Purpose
The Long Trading Bot is now agent-based: each bot instance implements the `TradingAgent` interface and can be managed by an `AgentManager` for multi-bot deployments. The bot executes long positions in cryptocurrency futures markets (e.g., BTC/USDT on Binance Futures) using configurable leverage (default 3x). It capitalizes on bullish price movements using technical indicators (RSI, MACD, Bollinger Bands) across daily and weekly timeframes, with a trailing stop-loss to protect profits during favorable trends. The bot supports dynamic leverage adjustments and optional sentiment analysis from X posts, exposed via RESTful APIs using Spring Boot for remote management. A Redis-based caching mechanism optimizes performance by reducing API calls while ensuring data freshness through event-based invalidation. AWS deployment is supported via Docker and ECS task definition.
1.2 Target Audience

Cryptocurrency traders with experience in futures trading.
Developers comfortable with configuring API keys, running Spring Boot applications, and using REST clients.
Users seeking automated trading with technical and sentiment-based strategies.

1.3 Objectives

- Agent-based: Each bot is an agent, enabling multi-symbol and multi-exchange deployments.
- Execute long positions with configurable leverage to amplify returns while managing risk.
- Use daily and weekly technical indicators to identify high-probability entry points.
- Implement a trailing stop-loss to maximize profits during uptrends.
- Provide RESTful APIs for starting, stopping, configuring, and monitoring the bot.
- Support optional sentiment analysis from X posts to enhance entry decisions.
- Optimize performance with Redis caching and event-based cache invalidation.
- Support multiple exchanges (Binance Futures) for flexibility.
- Ensure robust error handling, logging, and margin management.
- AWS-ready: Dockerfile and ECS task definition for cloud deployment.

2. Features and Requirements
2.1 Core Functionality (Agent-based)
2.1.1 Trading Environment

- Exchange: Binance Futures, configurable via system property (e.g., -Dexchange=binance).
- Market Type: Futures market with USDT-margined contracts (e.g., BTC/USDT).
- Leverage: Configurable, default 3x, adjustable via REST API (1x to 125x, per exchange limits).
- Trade Amount: Configurable amount of base currency (e.g., 0.001 BTC) per trade.
- API Integration: Use Binance Futures Connector Java for futures trading, with rate limiting enabled.
- AgentManager: Supports registration and lifecycle management of multiple agents for multi-symbol or multi-exchange trading.

2.1.2 Entry Conditions

Indicators and Timeframes:
Daily Timeframe ("1d"):
RSI: ≤ 30 (oversold) to identify buying opportunities.
MACD: Bullish crossover (MACD > signal line) to confirm upward momentum.
Bollinger Bands: Price ≤ 101% of the lower band to target price dips.


Weekly Timeframe ("1w"):
RSI: < 70 (not overbought) to avoid strong downtrends.


Sentiment Analysis (Optional):
When enabled via REST API, require positive sentiment from X posts (score > 0.6).
Rationale: Sentiment strengthens technical signals by incorporating market sentiment.


Rationale: Daily indicators capture short-term opportunities; weekly RSI ensures trend alignment.


Execution: Place market buy orders for fast execution.

2.1.3 Exit Conditions

Trailing Stop-Loss:
Close position if price falls 1% below the highest price since entry.
Rationale: Maximizes profits during uptrends while protecting against reversals.


Other Exit Conditions:
Daily RSI ≥ 50 (neutral), indicating weakening momentum.
Daily MACD bearish crossover (MACD < signal line), signaling trend reversal.
High liquidation risk (price within 5% of estimated liquidation price: entry_price * (1 - 1/leverage)).


Execution: Place market sell orders to close positions quickly.

2.1.4 Monitoring and Risk Management

Interval: Check conditions every 15 minutes (900 seconds).
Rationale: Balances responsiveness with slow-updating daily/weekly candles.


Margin Balance Check: Verify sufficient USDT margin before trades.
Liquidation Risk Check: Monitor price against estimated liquidation price.
Error Handling: Log and handle API errors, insufficient data, and network issues.

2.1.5 RESTful APIs

Endpoints:
- POST /api/simple-trading-bot/start: Start the bot.
- POST /api/simple-trading-bot/stop: Stop the bot and close open positions.
- GET /api/simple-trading-bot/status: Get bot status (running/stopped, position).
- POST /api/simple-trading-bot/configure: Update configuration (e.g., trade amount, RSI thresholds).
- POST /api/simple-trading-bot/leverage: Set dynamic leverage.
- POST /api/simple-trading-bot/sentiment: Enable/disable sentiment analysis.

AgentManager APIs (future): Register, start, stop, and monitor multiple agents.

Rationale: REST APIs enable remote management and monitoring. AgentManager enables multi-agent orchestration.

2.1.6 Caching

Mechanism: Use Redis to cache indicator values (RSI, MACD, Bollinger Bands) with a hybrid approach:
TTL: 2 minutes for daily indicators, 30 minutes for weekly indicators.
Event-Based Invalidation: Invalidate cache on new candle formation or significant price change (>1%).


Rationale: Reduces API calls to the exchange while ensuring fresh data for trading decisions.

2.1.7 Logging

Log metrics: price, daily RSI, MACD, signal, Bollinger Bands, weekly RSI, highest price.
Log actions: trade entries/exits, profit/loss, errors, cache invalidations.
Rationale: Ensures transparency and debugging capability.

2.2 Technical Indicators

RSI: Period 14, daily ≤ 30 for entry, ≥ 50 for exit, weekly < 70.
MACD: Fast EMA 12, Slow EMA 26, Signal 9; bullish crossover for entry, bearish for exit.
Bollinger Bands: Period 20, 2.0 standard deviations; price ≤ 101% of lower band for entry.
Data Source: Fetch 100 candles per timeframe ("1d" or "1w").
Library: Use TA4J for indicator calculations.

2.3 Configuration Parameters

Exchange: Default "binance", configurable via system property (e.g., -Dexchange=binance).
Symbol: Default "BTCUSDT".
Trade Amount: Default 0.001 BTC.
Leverage: Default 3x, adjustable via API.
Trailing Stop Percent: Default 1%.
RSI Parameters: Period (14), oversold (30), overbought (70).
MACD Parameters: Fast (12), Slow (26), Signal (9).
Bollinger Bands Parameters: Period (20), Standard Deviation (2.0).
Interval: Default 900 seconds.
Sentiment Analysis: Disabled by default, enabled via API.

2.4 Non-Functional Requirements

- Reliability: Handle API failures and network issues gracefully.
- Security: Restrict API keys to trading permissions; disable withdrawals.
- Performance: Minimize API calls using Redis caching with TTL (2 min daily, 30 min weekly) and event-based invalidation on new candles or >1% price changes.
- Scalability: AgentManager supports multiple symbols and exchanges via configuration and agent registration.

3. Technical Requirements

Runtime: Java 17, Spring Boot 3.2.0.
Dependencies:
spring-boot-starter-web: For RESTful services.
spring-boot-starter-data-redis: For Redis caching.
binance-futures-connector-java: For Binance futures trading API
ta4j-core: For technical indicators.
jackson-databind: For JSON parsing.
spring-boot-starter-test, junit-jupiter, mockito-core: For testing.


API Credentials: Valid API key/secret with trading permissions for the chosen exchange.
Timeframes: Support for "1d" and "1w" OHLCV data.
Redis: Redis server for caching indicator values.

5. Risks and Mitigation

Risk: Liquidation due to volatility.
Mitigation: Use conservative leverage, monitor liquidation risk, trailing stop-loss.


Risk: API rate limits or connectivity issues.
Mitigation: Enable rate limiting, use Redis caching, handle errors with retries.


Risk: Stale cached data affecting trading decisions.
Mitigation: Hybrid caching with TTL and event-based invalidation (new candles, >1% price change).


Risk: False signals from indicators or sentiment.
Mitigation: Combine daily/weekly indicators and optional sentiment analysis.


Risk: Exchange-specific API differences causing inconsistencies.
Mitigation: Abstract exchange interactions through FuturesExchangeService interface.



6. Future Enhancements

- Support COIN-margined contracts or other exchanges.
- Enhance sentiment analysis with real X API integration.
- Add dynamic trailing stop percentages or additional indicators.
- Implement WebSocket for real-time price updates.
- Expand AgentManager APIs for agent orchestration and monitoring.

7. Testing and Validation

Test Environment: Binance Futures Testnet .
Validation:
Verify entry/exit logic with historical data on both exchanges.
Test leverage adjustments and sentiment toggling via REST APIs.
Validate Redis caching and invalidation (new candles, price changes).
Ensure no API errors during 24-hour runs.


Success Criteria:
Correct trade execution based on technical and sentiment conditions.
Cache invalidation on new candles or significant price changes.
Robust error handling and continuous operation.



8. Disclaimer
This bot is for educational purposes only. Leveraged trading carries significant risks, including total loss of capital. Test thoroughly on a testnet and use at your own risk. This is not financial advice.