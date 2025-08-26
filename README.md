# Simple-trading-bot

A Spring Boot-based automated trading bot for long positions in cryptocurrency futures markets (e.g., BTC/USDT on Binance Futures) with configurable leverage, trailing stop-loss, and optional sentiment analysis from X posts. Uses Redis caching to optimize performance.
Features

* Executes long positions using technical indicators (RSI, MACD, Bollinger Bands) on daily ("1d") and weekly ("1w") timeframes.  
* Configurable leverage (default 3x), adjustable via REST API.      
* Trailing stop-loss (1%) to maximize profits during uptrends.   
* Optional sentiment analysis from X posts for entry decisions.   
* RESTful APIs for starting, stopping, configuring, and monitoring the bot.   
* Redis caching with hybrid TTL and event-based invalidation for performance.    
* Support for Binance Futures, configurable via system property.   
* Built with SOLID and Clean Code principles for maintainability.  

### Prerequisites

* Java 17  
* Maven 3.8+  
* Binance Futures account with API key/secret (trading permissions only)    
* X API access for sentiment analysis (optional)    
* Redis server (e.g., localhost:6379)   
* Docker and Docker Compose for containerization

### Setup

1. Clone the repository:
    ```bash
    git clone <repository-url>
    cd simple-trading-bot
    ```


2. Configure application.properties with Redis settings and X API credentials (if using sentiment analysis).
3. Update API keys in TradingBotApplication.java (replace placeholders like "YOUR_BINANCE_API_KEY").
4. Set exchange (Binance by default):
    ```bash
    java -Dexchange=binance -jar LeverageBot-1.0-SNAPSHOT.jar
    ```
    
5. Build and run:
    ```bash
    mvn clean install
    mvn spring-boot:run
    ```

#### Docker Setup

Build the Docker image:    
   ```bash
   docker build -t simple-trading-bot .
   ```


Run with Docker (standalone, without Redis):
  ```bash
    docker run -p 8080:8080 --name simple-trading-bot simple-trading-bot
  ``` 


Note: This requires a separate Redis instance. Use -e SPRING_DATA_REDIS_HOST=host.docker.internal if Redis is on host.


Run with Docker Compose (includes Redis):
  ```bash
    docker-compose up --build
  ```


This starts the tradingbot and Redis services.      
Access the bot at http://localhost:8080.   
Set exchange with -e exchange=binance in docker-compose.yml or environment.

#### Kubernetes Setup

Apply Kubernetes manifests:
```bash
    kubectl apply -f redis-deployment.yaml
    kubectl apply -f redis-service.yaml
    kubectl apply -f deployment.yaml
    kubectl apply -f service.yaml 
```


Access the service (use LoadBalancer or port-forward for minikube):     

```bash
    kubectl port-forward service/simple-trading-bot 8080:8080.    
```      

Access at http://localhost:8080.  

### Usage

    Start Bot: POST /api/simple-trading-bot/start   
    Stop Bot: POST /api/simple-trading-bot/stop   
    Get Status: GET /api/simple-trading-bot/status    
    Update Config: POST /api/simple-trading-bot/configure with JSON body (e.g., {"symbol":"BTCUSDT","tradeAmount":0.001,...})     
    Set Leverage: POST /api/simple-trading-bot/leverage?leverage=5       
    Enable/Disable Sentiment: POST /api/simple-trading-bot/sentiment?enable=true

### Testing
  Run unit tests:
  ```bash
      mvn test
   ```

### Redis Setup

1. Install Redis (e.g., sudo apt install redis-server on Ubuntu).    
2. Start Redis: redis-server.    
3. Configure spring.data.redis.host and port in application.properties.

Disclaimer    
This is for educational purposes only. Leveraged trading is risky and may result in total loss of capital.    
Test on Binance Futures Testnet. Not financial advice.   

Documentation    
See docs/LongTradingBotPRD.md for detailed requirements.
