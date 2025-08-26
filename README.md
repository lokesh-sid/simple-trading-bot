SimpleTradingBot
A Spring Boot-based automated trading bot for long positions in cryptocurrency futures markets (e.g., BTC/USDT on Binance Futures or Bybit Futures) with configurable leverage, trailing stop-loss, and optional sentiment analysis from X posts. Uses Redis caching to optimize performance.
Features

Executes long positions using technical indicators (RSI, MACD, Bollinger Bands) on daily ("1d") and weekly ("1w") timeframes.
Configurable leverage (default 3x), adjustable via REST API.
Trailing stop-loss (1%) to maximize profits during uptrends.
Optional sentiment analysis from X posts for entry decisions.
RESTful APIs for starting, stopping, configuring, and monitoring the bot.
Redis caching with hybrid TTL and event-based invalidation for performance.
Support for Binance Futures  configurable via system property.
Built with SOLID and Clean Code principles for maintainability.

Prerequisites

Java 17
Maven 3.8+
Binance Futures account with API key/secret (trading permissions only)
X API access for sentiment analysis (optional)
Redis server (e.g., localhost:6379)

Setup

Clone the repository:
git clone <repository-url>
cd LeverageBot


Configure application.properties with Redis settings and X API credentials (if using sentiment analysis).

Set exchange (Binance by default):
java -Dexchange=binance -jar simple-trading-bot-1.0-SNAPSHOT.jar


Build and run:
mvn clean install
mvn spring-boot:run



Usage

Start Bot: POST /api/simple-trading-bot/start
Stop Bot: POST /api/simple-trading-bot/stop
Get Status: GET /api/simple-trading-bot/status
Update Config: POST /api/simple-trading-bot/configure with JSON body (e.g., {"symbol":"BTCUSDT","tradeAmount":0.001,...})
Set Leverage: POST /api/simple-trading-bot/leverage?leverage=5
Enable/Disable Sentiment: POST /api/simple-trading-bot/sentiment?enable=true

Testing
Run unit tests:
mvn test

Redis Setup

Install Redis (e.g., sudo apt install redis-server on Ubuntu).
Start Redis: redis-server.
Configure spring.data.redis.host and port in application.properties.

Disclaimer
This is for educational purposes only. Leveraged trading is risky and may result in total loss of capital. Test on Binance Futures Testnet. Not financial advice.
Documentation
See docs/LongTradingBotPRD.md for detailed requirements.