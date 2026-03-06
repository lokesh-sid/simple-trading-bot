graph TB
    %% External Systems
    subgraph "External Systems"
        BINANCE[Binance API<br/>Cryptocurrency Exchange]
        KAFKA[(Apache Kafka<br/>Event Streaming)]
        REDIS[(Redis Cache<br/>Market Data)]
        PROMETHEUS[(Prometheus<br/>Metrics)]
        GRAFANA[Grafana<br/>Dashboards]
    end

    %% Client Layer
    subgraph "Client Layer"
        WEB[Web Dashboard<br/>React/Vue/Angular]
        MOBILE[Mobile App<br/>iOS/Android]
        API_CLIENTS[API Clients<br/>Postman, Scripts]
    end

    %% Presentation Layer
    subgraph "Presentation Layer"
        subgraph "REST API Gateway"
            TBC[TradingBotController<br/>/api/simple-trading-bot/*]
            EDC[EventDemoController<br/>Event Demonstrations]
            RC[ResilienceController<br/>Circuit Breaker Demo]
        end

        subgraph "API Components"
            DTO_REQ[Request DTOs<br/>BotStartRequest,<br/>LeverageUpdateRequest]
            DTO_RESP[Response DTOs<br/>BotStartResponse,<br/>BotStopResponse]
            VALID[Jakarta Validation<br/>@Valid, @NotNull]
            EXCEPTIONS[Custom Exceptions<br/>BotNotInitializedException,<br/>BotAlreadyRunningException]
        end
    end

    %% Application Layer
    subgraph "Application Layer"
        subgraph "Trading Engine"
            FTB[FuturesTradingBot<br/>Main Trading Agent]
            TES[TradeExecutionService<br/>Order Management]
            RAS[RiskAssessmentService<br/>Risk Evaluation]
        end

        subgraph "Analysis Engine"
            IC[IndicatorCalculator<br/>TA4J Integration]
            SA[SentimentAnalyzer<br/>Market Sentiment]
            TST[TrailingStopTracker<br/>Dynamic Stop Loss]
        end

        subgraph "Exchange Integration"
            BFS[BinanceFuturesService<br/>Live Trading]
            PFS[PaperFuturesExchangeService<br/>Paper Trading]
            RBS[RateLimitedBinanceFuturesService<br/>API Rate Limiting]
        end
    end

    %% Domain Layer
    subgraph "Domain Layer"
        subgraph "Domain Events"
            TSE[TradeSignalEvent<br/>Entry/Exit Signals]
            TEE[TradeExecutionEvent<br/>Order Confirmations]
            RE[RiskEvent<br/>Risk Alerts]
            BSE[BotStatusEvent<br/>Bot Lifecycle]
            MDE[MarketDataEvent<br/>Price Updates]
        end

        subgraph "Business Rules"
            TD[TradeDirection<br/>LONG/SHORT]
            TC[TradingConfig<br/>Bot Configuration]
            RS[RiskStrategy<br/>Risk Parameters]
        end
    end

    %% Infrastructure Layer
    subgraph "Infrastructure Layer"
        subgraph "Event Infrastructure"
            EP[EventPublisher<br/>Kafka Producer]
            EC[EventConsumer<br/>Kafka Consumer]
            EW[EventWrapper<br/>Type-Safe Events]
        end

        subgraph "Data Infrastructure"
            RCACHE[Redis Cache<br/>Technical Indicators]
            RDB[(PostgreSQL<br/>Trade History)]
        end

        subgraph "Configuration"
            KCONF[KafkaConfig<br/>Event Streaming]
            RCONF[RedisConfig<br/>Caching]
            RECONF[ResilienceConfig<br/>Circuit Breaker]
            OAC[OpenApiConfig<br/>API Documentation]
        end

        subgraph "Monitoring"
            SA[Spring Actuator<br/>Health Checks]
            METRICS[Custom Metrics<br/>Performance Monitoring]
        end
    end

    %% Deployment Layer
    subgraph "Deployment Layer"
        subgraph "Container Orchestration"
            DOCKER[Docker Container<br/>Java 21 + Alpine]
            K8S[Kubernetes<br/>Pod Deployment]
            ECS[AWS ECS<br/>Container Service]
        end

        subgraph "Infrastructure as Code"
            HELM[Helm Charts<br/>K8s Packaging]
            TERRAFORM[Terraform<br/>AWS Resources]
        end
    end

    %% Flow Connections
    WEB --> TBC
    MOBILE --> TBC
    API_CLIENTS --> TBC

    TBC --> FTB
    TBC --> TES
    TBC --> RAS

    FTB --> IC
    FTB --> SA
    FTB --> TST

    FTB --> BFS
    FTB --> PFS
    FTB --> RBS

    IC --> TSE
    SA --> TSE
    TST --> TSE

    TSE --> EP
    TEE --> EP
    RE --> EP
    BSE --> EP
    MDE --> EP

    EP --> EW
    EW --> KAFKA

    KAFKA --> EC
    EC --> TES
    EC --> RAS

    BFS --> BINANCE
    RBS --> BINANCE

    IC --> REDIS
    SA --> REDIS

    SA --> METRICS
    METRICS --> PROMETHEUS
    PROMETHEUS --> GRAFANA

    DOCKER --> K8S
    DOCKER --> ECS
    K8S --> HELM
    ECS --> TERRAFORM

    %% Styling
    classDef presentation fill:#e1f5fe,stroke:#01579b,stroke-width:2px
    classDef application fill:#f3e5f5,stroke:#4a148c,stroke-width:2px
    classDef domain fill:#e8f5e8,stroke:#1b5e20,stroke-width:2px
    classDef infrastructure fill:#fff3e0,stroke:#e65100,stroke-width:2px
    classDef deployment fill:#fce4ec,stroke:#880e4f,stroke-width:2px
    classDef external fill:#f5f5f5,stroke:#424242,stroke-width:2px

    class TBC,EDC,RC,DTO_REQ,DTO_RESP,VALID,EXCEPTIONS presentation
    class FTB,TES,RAS,IC,SA,TST,BFS,PFS,RBS application
    class TSE,TEE,RE,BSE,MDE,TD,TC,RS domain
    class EP,EC,EW,RCACHE,RDB,KCONF,RCONF,RECONF,OAC,SA,METRICS infrastructure
    class DOCKER,K8S,ECS,HELM,TERRAFORM deployment
    class BINANCE,KAFKA,REDIS,PROMETHEUS,GRAFANA,WEB,MOBILE,API_CLIENTS external