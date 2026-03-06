# System Architecture Overview

## Mermaid Diagram

```mermaid
graph TD
    subgraph "Gateway_&_API"
        GW["API Gateway / Login"]
    end
    subgraph "Orchestration"
        ORCH["AgentOrchestrator"]
        MAN["AgentManager"]
        STRAT["AgentStrategy\n(LangChain4j, RAG, Legacy)"]
    end
    subgraph "Agent_Layer"
        AGT["AgenticTradingAgent"]
        FACT["AgentFactory"]
        LLM["LLMProvider\n(OpenAI, Grok, etc)"]
    end
    subgraph "Messaging_&_Events"
        EVT["EventPublisher"]
        KAFKA["Kafka"]
    end
    subgraph "Market_Data"
        MKT["MarketDataService"]
        WS["ExchangeWebSocketClient"]
        EXC["Binance/CCXT"]
    end
    subgraph "Persistence"
        DB[("PostgreSQL/JPA")]
        REDIS[("Redis")]
        REPO["AgentRepository, OrderRepository, etc"]
    end
    subgraph "Security"
        AUTH["AuthService"]
        JWT["JwtService"]
        USER["CustomUserDetailsService"]
    end
    GW --> ORCH
    GW --> AUTH
    AUTH --> JWT
    AUTH --> USER
    ORCH --> MAN
    ORCH --> STRAT
    ORCH --> AGT
    AGT --> FACT
    AGT --> LLM
    AGT --> EVT
    EVT --> KAFKA
    MKT --> WS
    MKT --> EXC
    MKT --> EVT
    ORCH --> REPO
    AGT --> REPO
    REPO --> DB
    REPO --> REDIS
```

## Component Breakdown

- **Gateway & API**
  - API Gateway / Login: Entry point for user authentication and API requests.

- **Orchestration**
  - AgentOrchestrator: Coordinates agent lifecycle and event dispatch.
  - AgentManager: Manages agent instances and state.
  - AgentStrategy: Pluggable strategies (LangChain4j, RAG, Legacy).

- **Agent Layer**
  - AgenticTradingAgent: Core trading logic and decision-making.
  - AgentFactory: Creates agent instances.
  - LLMProvider: Integrates with LLMs (OpenAI, Grok, etc).

- **Messaging & Events**
  - EventPublisher: Publishes events to Kafka and internal listeners.
  - Kafka: Message broker for event-driven processing.

- **Market Data**
  - MarketDataService: Fetches and processes live market data.
  - ExchangeWebSocketClient: Real-time data from exchanges.
  - Binance/CCXT: External exchange integration.

- **Persistence**
  - AgentRepository, OrderRepository, etc: Data access layers.
  - PostgreSQL/JPA: Main relational database.
  - Redis: Caching and fast data access.

- **Security**
  - AuthService: Handles authentication logic.
  - JwtService: Issues and validates JWT tokens.
  - CustomUserDetailsService: User details for Spring Security.
