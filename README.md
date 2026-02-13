
# Agentic AI Trading Bot

A Spring Boot-based automated trading bot that combines **Agentic AI (LLMs)** with traditional quantitative strategies for cryptocurrency futures markets.

## 🚀 Key Features

### 🧠 Agentic AI Core
- **Autonomous Agents**: Uses **Grok (X.AI)** or OpenAI for market reasoning and decision making.
- **RAG Architecture**: Retrieves market context and historical patterns using **Pinecone** vector database.
- **Sentiment Analysis**: Analyzes market sentiment (currently simulated/placeholder).
- **Natural Language Orders**: Parses trading instructions directly from LLM reasoning (e.g., "Buy 0.5 BTC with 5x leverage").

### 📉 Trading Capabilities
- **Multi-Exchange Support**:
  - **Bybit Futures** (✅ **Recommended for Testing**): Full V5 API support, Real Execution, Testnet Ready.
  - **Binance Futures**: Mainnet support (Production only).
  - **dYdX v4**: Market data enabled, Trade execution is mocked/simulated (Safe for testing navigation).
  - **Paper Trading**: Fully simulated local execution for strategy development.
- **Advanced Exit Strategies**:
  - Trailing Stop-Loss
  - Liquidation Risk Guard
  - MACD Reversal Exit
  - RSI Overbought/Oversold Exit

### 🛠 Technical Architecture
- **Tech Stack**: Java 21, Spring Boot 3.2
- **Event-Driven**: Apache Kafka for asynchronous event processing.
- **Data & Caching**: PostgreSQL for persistence, Redis for high-performance caching.
- **Resilience**: Circuit Breakers & Rate Limiters (Resilience4j) for all exchange interactions.

---

## 🚦 Project Status

| Component | Status | Notes |
|-----------|--------|-------|
| **Bybit Integration** | 🟢 **Ready** | Fully functional. Supports Mainnet & Testnet V5 API. |
| **Binance Integration** | 🟡 **Mainnet Only** | Functional logic, but hardcoded to Mainnet URLs. |
| **dYdX Integration** | 🟠 **Partial** | Reads market data/balance. Trade execution is **Mocked** (no gas signing). |
| **AI / RAG Agents** | 🟢 **Beta** | RAG pipeline and Grok integration implemented. |
| **Sentiment Analysis** | 🔴 **Mocked** | Placeholder logic. Needs real API credentials. |

---

## ⚡ Quick Start (Testnet)

The project is pre-configured for **Bybit Testnet**.

### 1. Prerequisites
- Java 21 LTS
- Docker & Docker Compose (for Kafka, Redis, Postgres)
- A Bybit Testnet Account (Ensure "One-Way Mode" is enabled)

### 2. Configuration
Edit `src/main/resources/application.properties`:

```properties
# Select Bybit as the provider
trading.exchange.provider=bybit

# Enable Testnet
trading.bybit.domain=TESTNET_DOMAIN

# Credentials
trading.bybit.api.key=YOUR_TESTNET_KEY
trading.bybit.api.secret=YOUR_TESTNET_SECRET
```

### 3. Run Dependencies
```bash
docker-compose up -d
```

### 4. Start the Bot
```bash
./gradlew bootRun
```

---

## 🧪 Testing

### API Tests
The `api-tests/` directory contains HTTP scripts for direct testing.

- **Gateway Tests**: `api-tests/gateway-api-tests.http`
- **Agent Tests**: `api-tests/agent-api-tests.http`

### Unit & Integration Tests
```bash
./gradlew test
```

---

## 📚 Documentation

For detailed implementation guides:
- [Agentic AI Implementation](AGENTIC_AI_IMPLEMENTATION_SUMMARY.md) - Deep dive into the LLM/RAG system.
- [Binance Integration](BINANCE_API_INTEGRATION.md)
- [Architecture Diagram](AGENTIC_AI_IMPLEMENTATION_SUMMARY.md#architecture)

## 🏗️ Architecture Overview

This trading bot follows a **layered, event-driven architecture** built with Domain-Driven Design (DDD) principles and Spring Boot best practices.

### High-Level Architecture

```mermaid
graph TB
    %% Client Layer
    subgraph "Client Applications"
        WEB[Web Dashboard]
        API[REST API Clients]
        MONITOR[Monitoring Tools]
    end

    %% Presentation Layer
    subgraph "Presentation Layer<br/>REST API Gateway"
        CONTROLLER[TradingBotController<br/>/api/simple-trading-bot/*]
        DTO[Request/Response DTOs<br/>Type-Safe Contracts]
        VALIDATION[Jakarta Validation<br/>Input Validation]
        EXCEPTIONS[Global Exception Handler<br/>Error Responses]
    end

    %% Application Layer
    subgraph "Application Layer<br/>Business Logic"
        BOT[FuturesTradingBot<br/>Trading Agent]
        EXECUTION[TradeExecutionService<br/>Order Management]
        RISK[RiskAssessmentService<br/>Risk Controls]
        ANALYSIS[Technical Analysis<br/>Indicators & Signals]
    end

    %% Domain Layer
    subgraph "Domain Layer<br/>Business Rules & Events"
        EVENTS[Domain Events<br/>TradeSignalEvent,<br/>TradeExecutionEvent,<br/>RiskEvent]
        CONFIG[TradingConfig<br/>Business Configuration]
        STRATEGY[Trading Strategies<br/>Entry/Exit Logic]
    end

    %% Infrastructure Layer
    subgraph "Infrastructure Layer<br/>External Systems"
        KAFKA[(Apache Kafka<br/>Event Streaming)]
        REDIS[(Redis Cache<br/>Market Data)]
        EXCHANGE[Binance API<br/>Cryptocurrency Exchange]
        DATABASE[(PostgreSQL<br/>Trade History)]
    end

    %% Event Flow
    subgraph "Event-Driven Flow"
        PUBLISHER[EventPublisher<br/>Kafka Producer]
        CONSUMER[EventConsumer<br/>Kafka Consumer]
        WRAPPER[EventWrapper<br/>Type-Safe Events]
    end

    %% Deployment
    subgraph "Deployment & DevOps"
        DOCKER[Docker Container<br/>Java 21]
        KUBERNETES[Kubernetes<br/>Orchestration]
        MONITORING[Spring Actuator<br/>Health & Metrics]
    end

    %% Connections
    WEB --> CONTROLLER
    API --> CONTROLLER
    MONITOR --> MONITORING

    CONTROLLER --> BOT
    CONTROLLER --> EXECUTION
    CONTROLLER --> RISK

    BOT --> ANALYSIS
    ANALYSIS --> EVENTS

    EVENTS --> PUBLISHER
    PUBLISHER --> WRAPPER
    WRAPPER --> KAFKA

    KAFKA --> CONSUMER
    CONSUMER --> EXECUTION
    CONSUMER --> RISK

    BOT --> EXCHANGE
    EXECUTION --> EXCHANGE
    ANALYSIS --> REDIS

    EXECUTION --> DATABASE

    DOCKER --> KUBERNETES
    MONITORING --> KUBERNETES
```

### Architecture Layers

#### 🎨 **Presentation Layer**
- **REST API Controllers**: `TradingBotController` with method-specific DTOs
- **Request/Response DTOs**: Type-safe contracts with Jakarta validation
- **Global Exception Handling**: Spring `@ControllerAdvice` for consistent error responses
- **OpenAPI Documentation**: Auto-generated Swagger docs

#### 🚀 **Application Layer**
- **Trading Agents**: `FuturesTradingBot` implementing `TradingAgent` interface
- **Business Services**: Trade execution, risk assessment, technical analysis
- **Exchange Integration**: Live trading (Binance) and paper trading modes
- **Strategy Components**: Indicators, sentiment analysis, trailing stops

#### 🎯 **Domain Layer**
- **Domain Events**: `TradeSignalEvent`, `TradeExecutionEvent`, `RiskEvent`, etc.
- **Business Rules**: Trading configurations, risk parameters, strategies
- **Value Objects**: `TradeDirection`, immutable business data
- **Domain Services**: Pure business logic without external dependencies

#### 🔧 **Infrastructure Layer**
- **Event Streaming**: Apache Kafka with type-safe `EventWrapper`
- **Caching**: Redis for market data and technical indicators
- **External APIs**: Binance Futures API with rate limiting and circuit breakers
- **Persistence**: PostgreSQL for trade history and audit trails

### Key Architectural Patterns

#### 🏛️ **Domain-Driven Design (DDD)**
- **Clear separation** between transport (DTOs) and domain objects
- **Ubiquitous language** in domain events and business rules
- **Bounded contexts** for trading, risk management, and execution

#### 📡 **Event-Driven Architecture**
- **Asynchronous processing** with Kafka event streaming
- **Type-safe events** using `EventWrapper<T extends TradingEvent>`
- **Event sourcing** capabilities for audit trails

#### 🛡️ **Resilience Patterns**
- **Circuit Breaker**: Resilience4j for external API failures
- **Rate Limiting**: API quota management for exchanges
- **Retry Logic**: Exponential backoff for transient failures
- **Fallback Strategies**: Paper trading as circuit breaker fallback

## 🛠 Deployment

### Docker Support
The application is fully containerized.

1. **Build Image**:
   ```bash
   docker build -t simple-trading-bot .
   ```

2. **Run with Dependencies**:
   ```bash
   docker-compose up -d
   ```

### AWS Deployment
- **ECS**: Use `aws-ecs-task-definition.json` for Fargate deployment.
- **Logging**: Configured for CloudWatch compatibility.
- **Config**: Use AWS Parameter Store or Secrets Manager for API keys.

## 📝 Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `trading.exchange.provider` | `binance` | Exchange to use (`bybit`, `binance`, `dydx`, `paper`) |
| `trading.bybit.domain` | `MAINNET_DOMAIN` | Set to `TESTNET_DOMAIN` for testing |
| `rag.enabled` | `true` | Enable Agentic AI features |
| `rag.embedding.provider` | `openai` | Embedding provider (`openai`, `grok`, `local`) |

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request


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

Start Bot (Long/Short, Live/Paper):
```http
POST /api/simple-trading-bot/start?direction=LONG&paper=true
POST /api/simple-trading-bot/start?direction=SHORT&paper=false
```

Stop Bot:
```http
POST /api/simple-trading-bot/stop
```

Get Status:
```http
GET /api/simple-trading-bot/status
```

Update Config:
```http
POST /api/simple-trading-bot/configure
Body: {"symbol":"BTCUSDT","tradeAmount":0.001,...}
```

Set Leverage:
```http
POST /api/simple-trading-bot/leverage?leverage=5
```

Enable/Disable Sentiment:
```http
POST /api/simple-trading-bot/sentiment?enable=true
```

### Testing
  Run unit tests:
  ```bash
      ./gradlew test
   ```

   For continuous testing:
   ```bash
      ./gradlew test --continuous
   ```

### Redis Setup

1. Install Redis (e.g., sudo apt install redis-server on Ubuntu).    
2. Start Redis: redis-server.    
3. Configure spring.data.redis.host and port in application.properties.


### Paper Trading

To safely test strategies, start the bot in paper mode (`paper=true`). All trades and margin are simulated in memory. No real funds are used or at risk.

### Extending Indicators

Add new technical indicators by implementing the `TechnicalIndicator` interface and registering them in the `IndicatorCalculator` via code or configuration. The bot will automatically compute and use all registered indicators.

### Testability

All dependencies are injected via constructors, making it easy to mock services and indicators for unit testing.

---

Disclaimer    
This is for educational purposes only. Leveraged trading is risky and may result in total loss of capital.    
Test on Binance Futures Testnet. Not financial advice.   

Documentation    
See docs/LongTradingBotPRD.md for detailed requirements and agent architecture.
