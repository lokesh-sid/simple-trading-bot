# Plan: Enterprise Java Trading Platform Upgrade
(Feb 20, 2026 – fully aligned with latest codebase analysis)

This plan transforms the bot into a production-grade platform, leveraging existing dependencies (`ta4j`) and standardizing around robust Java libraries (**XChange**) for exchanges without a first-party native SDK.

---

## Accurate Current State Snapshot

- **Backtesting**: `BacktestService` ✅ fully refactored. `TradingAgentFactory` → `LLMTradingAgent` (ta4j MACD+RSI + LLMProvider). `CsvBacktestAgentExecutionService` drives the replay loop. `StandardBacktestMetricsCalculator` computes Sharpe Ratio, Max Drawdown, Win Rate, Profit Factor, Equity Curve. `BacktestResult` (3 fields, `totalTrades` always 0) replaced by `BacktestMetrics` record (8 fields). `TradeDirection.LONG` hardcode removed. `MockSentimentAnalyzer` deleted. `FuturesTradingBot` no longer instantiated in backtest path.
- **Market Data**: ✅ WebSocket streaming implemented via `BinanceStreamingService` and `BybitStreamingService`. `@Scheduled` REST polling kept as configurable fallback.
- **Exchanges**:
  - Binance: Native connector (`binance-connector-java:3.3.0`) — WebSocket streaming ✅ + REST. Mainnet only.
  - Bybit: Native SDK (`bybit-java-api:1.2.6`) — `BybitFuturesService` handles REST execution. New `BybitStreamingService` handles WebSocket streaming ✅.
  - dYdX v4: Partial — market data reads work, trade execution is mocked.
- **Multi-Exchange Strategy**: **XChange** (`knowm/XChange`) adopted as the unified Java-native library for exchanges that lack a first-party SDK. Deferred to Phase 3.
- **Event Model**: `StreamMarketDataEvent` record confirmed as the normalized domain event. Contains `isCandleClosed`, `exchange`, `symbol`, `interval`, `open`, `high`, `low`, `close`, `volume`, `eventTime`.
- **Streaming Contract**: `ExchangeStreamService` interface defined. Both `BinanceStreamingService` and `BybitStreamingService` implement it ✅.
- **CachedGrokService**: ✅ Implemented and verified. SHA-256 prompt hashing → Redis L1 → File L2 cache. Synthetic fallback active in backtest profile. Cache MISS → file write → cache HIT confirmed end-to-end.
- **Kafka in Backtest**: ✅ Disabled via `trading.kafka.publish.enabled=false` + `spring.kafka.listener.auto-startup=false`. No 60s timeouts or NetworkClient flood.
- **Agentic Architecture**: ⚠️ `AgentOrchestrator` still uses `@Scheduled` as the **primary** path (legacy polling). A new `@KafkaListener` for `KlineClosedEvent` has been added (Option C — separate event type) but no concrete `AgenticTradingAgent` beans exist yet, so the listener is a no-op until Phase 2 implementations are registered. `AgenticTradingAgent` sub-interface ✅ defined. `AgentStatus` enum ✅ extracted. Bulkhead per agent ✅ wired.
- **Frontend**: Empty folder with only `README.md`.
- **CI/CD & DevOps**: Completely absent.
- **Strengths**: Excellent DDD + Kafka event bus + Resilience4j + AgentOrchestrator + full Agentic AI (LangChain4j + Grok) layer + Docker/K8s manifests.

**Total realistic effort for solo dev**: **250–350 hours** (reduced because ta4j, Bybit service, Backtest* classes, and Phase 1 streaming are already complete).

---

## Phase 0: Foundation & Hygiene ✅ COMPLETE
*Establish a professional repo structure to support future complexity.*

### Completed Steps
1. ✅ **Documentation & Standards**: `LICENSE` (MIT), `CONTRIBUTING.md`, `README` updated with badges and architecture diagrams.
2. ✅ **Branching**: `platform-upgrade` branch created.
3. ✅ **Tagging**: Tagged **v0.3.0** with "Platform Upgrade Roadmap" notes.
4. ✅ **Publish**: Released to GHCR.
5. ✅ **Docker Cleanup**: Orphaned containers removed via `docker-compose down --remove-orphans`.

### Data Flow (Phase 0 — Baseline State)
```
REST Poll (@Scheduled every 30s)
      │
      ▼
AgentOrchestrator
      │
      ├──► GrokClient (real API — every tick, no caching)
      │
      ├──► TechnicalIndicators (MACD / RSI / Bollinger)
      │
      └──► FuturesExchangeService (Bybit REST / Paper)
```
> ⚠️ Problems identified: REST polling latency, Grok API called every tick (costly),
> no streaming, no caching, no agent isolation.

---

## Phase 1: Real-Time Data Fabric ✅ COMPLETE
*Replace REST polling with low-latency WebSocket streaming.*

### Completed Steps
1. ✅ **Streaming Contract defined**:
   - `ExchangeStreamService` interface with `subscribeTicker(symbol)` and `subscribeCandle(symbol, interval)` returning `Flux<StreamMarketDataEvent>`.
   - `StreamMarketDataEvent` record confirmed with all required fields: `isCandleClosed`, `exchange`, `symbol`, `interval`, `open`, `high`, `low`, `close`, `volume`, `eventTime`.

2. ✅ **WebSocket Implementation**:
   - `BinanceStreamingService implements ExchangeStreamService` — uses `binance-connector-java:3.3.0` native WS connector.
   - `BybitStreamingService implements ExchangeStreamService` — new class using `bybit-java-api:1.2.6` WS capabilities.
   - `BybitFuturesService` retained for **order execution only** (REST).
   - `infrastructure/marketdata/` package created.

3. ✅ **Event Bus Integration**:
   - `WebSocketMarketDataService` publishes normalized `StreamMarketDataEvent`s to Kafka.
   - Fan-out: one WS event → Kafka topic `market-data.{symbol}` → each subscribed `FuturesTradingBot` consumes it.

4. ✅ **Resilience & Transition**:
   - `websocket.enabled=true` config toggle added.
   - `@Scheduled` REST polling retained as configurable fallback.
   - Resilience4j `CircuitBreaker` applied to WebSocket connections (auto-reconnect).

### ⚠️ Phase 1 Outstanding Items (Pre-Phase 2 Requirements)
These were identified during the agentic architecture evaluation and must be completed **before Phase 2 begins**:

| Item | Priority | Effort | Status |
|------|----------|--------|--------|
| Define `AgenticTradingAgent` sub-interface (Option C) | 🔴 Must-do | 30 min | ✅ Done — `tradingbot.agent.AgenticTradingAgent` |
| Create `KlineClosedEvent` record (Option C) | 🔴 Must-do | 20 min | ✅ Done — `tradingbot.domain.market.KlineClosedEvent` |
| Create `AgentDecision` record | 🔴 Must-do | 15 min | ✅ Done — `tradingbot.agent.domain.model.AgentDecision` |
| `LLMProvider` interface (port for `CachedGrokService`) | 🔴 Must-do | 15 min | ✅ Already existed — `tradingbot.agent.infrastructure.llm.LLMProvider` |
| Add `AgentOrchestrator` `@KafkaListener` for `KlineClosedEvent` | 🔴 Must-do | 2 hrs | ✅ Done — `onKlineClosedEvent()` + Bulkhead dispatch |
| Add `AgentStatus` standalone enum + lifecycle state machine | 🟡 Should-do | 1 hr | ✅ Done — `tradingbot.agent.domain.model.AgentStatus` |
| Add Resilience4j `Bulkhead` per agent | 🟡 Should-do | 1 hr | ✅ Done — `resilience4j-bulkhead` dep added; `dispatchWithBulkhead()` in orchestrator |
| Wire `BacktestService` → `AgentOrchestrator` (remove direct `FuturesTradingBot`) | 🟡 Should-do | 2 hrs | ✅ Done — `TradingAgentFactory` + `CsvBacktestAgentExecutionService`. `FuturesTradingBot` no longer instantiated in backtest path. |
| Remove `LegacyLLMStrategy` + consolidate into `TradingAgent` impl | 🟠 Can defer to Phase 2 | 2 hrs | 🔲 Deferred |

### Data Flow (Phase 1 — Completed State)
```
┌─────────────────────────────────────────────────────────┐
│                   Exchange Layer                         │
│  Binance WS (wss://fstream.binance.com)                 │
│  Bybit WS   (wss://stream.bybit.com)                    │
└──────────────┬──────────────────────────────────────────┘
               │ Raw JSON
               ▼
┌─────────────────────────────────────────────────────────┐
│              Infrastructure Layer                        │
│  BinanceStreamingService                                │
│  BybitStreamingService                                  │
│         │                                               │
│         └──► Normalize to StreamMarketDataEvent         │
│              (exchange, symbol, OHLCV,                  │
│               isCandleClosed, eventTime)                │
└──────────────┬──────────────────────────────────────────┘
               │ Flux<StreamMarketDataEvent>
               ▼
┌─────────────────────────────────────────────────────────┐
│                   Event Bus (Kafka)                      │
│  Topic: market-data.BTCUSDT                             │
│  Topic: market-data.ETHUSDT                             │
└──────────────┬──────────────────────────────────────────┘
               │ Kafka Consumer
               ▼
┌─────────────────────────────────────────────────────────┐
│        AgentOrchestrator ⚠️ (still @Scheduled)          │
│  ┌─── isCandleClosed? ──► ta4j BarSeries.addBar()      │
│  │                              │                       │
│  │                              └──► Strategy Signal    │
│  │                                        │             │
│  └─── isTick? ──► Update live PnL only   │             │
│                                           ▼             │
│                              RiskManagementService      │
│                                           │             │
│                                           ▼             │
│                              FuturesExchangeService     │
│                              (Bybit REST / Paper)       │
└─────────────────────────────────────────────────────────┘
```

### Sequence: Candle Close → Signal → Order
```
Exchange WS ──► Adapter ──► StreamMarketDataEvent(isCandleClosed=true)
                                        │
                                        ▼
                              AgentOrchestrator
                                        │
                              barSeries.addBar(OHLCV)
                                        │
                              strategy.shouldEnter(idx)
                                        │
                              ┌─────────┴──────────┐
                           ENTRY                 NO SIGNAL
                              │
                     RiskManagementService
                              │
                     placeMarketOrder(BUY/SELL)
                              │
                     FuturesExchangeService ──► Exchange REST API
```

---

## Phase 2: Professional Backtesting Engine (2–3 weeks) 🔲 NEXT
*Unify simulation and live trading logic.*

### Pre-conditions (must complete Phase 1 outstanding items first)
- ✅ `CachedGrokService` implemented and verified
- ✅ `AgenticTradingAgent` sub-interface defined (Option C)
- ✅ `KlineClosedEvent` record defined (Option C candle detection)
- ✅ `AgentDecision` record defined
- ✅ `AgentOrchestrator` `@KafkaListener` + Bulkhead dispatch wired
- ✅ Concrete `AgenticTradingAgent` implementation: `LLMTradingAgent` (MACD+RSI+LLMProvider)
- ✅ `BacktestService` wired to `TradingAgentFactory` + `BacktestAgentExecutionService` — `FuturesTradingBot` no longer instantiated

### Exact Steps

1. **Leverage ta4j** ✅ Already a dependency:
   - Refactor `BacktestService` to use **single ta4j `BarSeries` + `Rule`/`Strategy` composition** instead of separate `BarSeries` per indicator.

2. **Event-Driven Replay**:
   - Refactor `BacktestService` to load CSV/Parquet data and replay it as high-speed `StreamMarketDataEvent`s on an internal in-memory bus (no Kafka needed for backtesting).
   - The `TradingAgent` (and its LLM logic) should not know it is running in the past.

3. **PaperLLM / CachedLLM** ✅ COMPLETE:
   - `CachedGrokService` implemented. SHA-256 hash of `symbol|price|volume|trend|sentiment|goal`.
   - **L1**: Redis (TTL 30 days). **L2**: File cache at `~/.trading-bot/backtest-llm-cache/`.
   - Synthetic offline fallback: `UPTREND → BUY (75%)`, `DOWNTREND → SELL (70%)`, neutral → `HOLD (60%)`.
   - Activated via `agent.llm.cache.enabled=true` in `application-backtest.properties`.
   - Real Grok API **never called** in backtest profile. Verified: cache MISS → file write → cache HIT confirmed ✅.
   - ⚠️ **Remaining gap**: `BacktestService` still uses `FuturesTradingBot` directly — not wired to `AgentOrchestrator`. Requires Phase 1 outstanding items to be resolved first.

4. **Metrics**:
   - Implement `BacktestResult` calculating: Sharpe Ratio, Max Drawdown, Win Rate, Profit Factor, equity curve (JSON/CSV output).
   - Remove `LONG` hardcode from `TradeDirection` → make configurable per agent.

### Agentic Architecture Changes Required (Pre-Phase 2)

> ✅ **Implemented (Feb 20, 2026)** — Option C chosen: sub-interface + separate event type.
> See files created below. `FuturesTradingBot` was intentionally left untouched (ISP / OCP).

#### `KlineClosedEvent` Record (Option C candle detection)
```java
// tradingbot.domain.market.KlineClosedEvent
public record KlineClosedEvent(
        String exchange,
        String symbol,
        String interval,
        BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
        BigDecimal volume,
        Instant openTime,
        Instant closeTime
) {}
```
> Exchange adapters publish to Kafka topic `kline-closed.{symbol}` only when a bar
> is definitively closed. `AgentOrchestrator` never needs to check `isCandleClosed`.

#### `AgentDecision` Record
```java
// tradingbot.agent.domain.model.AgentDecision
public record AgentDecision(
        String agentId, String symbol, Action action,
        int confidence, String reasoning, Instant decidedAt
) {
    public enum Action { BUY, SELL, HOLD }
}
```

#### `AgenticTradingAgent` Sub-interface (Option C)
```java
// tradingbot.agent.AgenticTradingAgent
public interface AgenticTradingAgent extends TradingAgent {
    String getSymbol();
    String getExchange();
    AgentStatus getStatus();
    Mono<AgentDecision> onKlineClosed(KlineClosedEvent event);
    void pause();
    void resume();
}
```
> `FuturesTradingBot` implements `TradingAgent` only — no changes needed.
> New Phase 2 agent implementations will implement `AgenticTradingAgent`.

#### `AgentOrchestrator` — `@KafkaListener` path (added)
```java
@KafkaListener(topicPattern = "kline-closed\\..*", groupId = "agent-orchestrator-klines")
public void onKlineClosedEvent(KlineClosedEvent event) {
    agenticAgents.stream()
        .filter(a -> event.symbol().equals(a.getSymbol()))
        .filter(a -> a.getStatus() == AgentStatus.ACTIVE)
        .forEach(agent -> dispatchWithBulkhead(agent, event));
}

private void dispatchWithBulkhead(AgenticTradingAgent agent, KlineClosedEvent event) {
    Bulkhead bulkhead = bulkheadRegistry.bulkhead("agent-" + agent.getId(), ...);
    agentScheduler.schedule(() -> {
        if (!bulkhead.tryAcquirePermission()) { /* drop */ return; }
        try {
            agent.onKlineClosed(event).subscribe(...);
        } finally {
            bulkhead.releasePermission();
        }
    });
}
```
> The existing `@Scheduled` polling loop is retained as the primary path until
> concrete `AgenticTradingAgent` beans are registered in Phase 2.

#### `AgentStatus` Standalone Enum
```java
// tradingbot.agent.domain.model.AgentStatus
public enum AgentStatus {
    CREATED, INITIALIZING, ACTIVE, PAUSED, STOPPED, ERROR,
    @Deprecated IDLE  // kept for AgentEntity.AgentStatus compat
}
```

#### Agent Lifecycle State Machine
```
CREATED ──► INITIALIZING ──► ACTIVE
                                │
                           ┌────┘
                           ▼
                         PAUSED
                           │
                      ┌────┘
                      ▼
                   STOPPED
                      │
               ERROR (auto-retry
                via Resilience4j)
```

### Data Flow (Phase 2 — Backtesting)
```
┌─────────────────────────────────────────────────────────┐
│                  Data Source                             │
│  CSV / Parquet historical OHLCV files                   │
└──────────────┬──────────────────────────────────────────┘
               │ replay at max speed
               ▼
┌─────────────────────────────────────────────────────────┐
│              BacktestReplayService                       │
│  Converts rows → StreamMarketDataEvent                  │
│  (isCandleClosed=true for every historical bar)         │
└──────────────┬──────────────────────────────────────────┘
               │ In-memory bus (no Kafka)
               ▼
┌─────────────────────────────────────────────────────────┐
│         AgentOrchestrator (refactored — event-driven)   │
│       │                                                 │
│       ├──► ta4j BarSeries.addBar(OHLCV)                │
│       │         │                                       │
│       │         └──► Strategy Signal (MACD/RSI/BB)     │
│       │                                                 │
│       └──► CachedGrokService (LLMProvider)             │
│                 │                                       │
│         ┌───── SHA-256(prompt+OHLCV) ──────┐           │
│         │                                  │           │
│      Cache HIT                         Cache MISS      │
│    (Redis / File)                    Synthetic fallback │
│         │                                  │           │
│         └──────────── Reasoning ───────────┘           │
└──────────────┬──────────────────────────────────────────┘
               │ Signal + Reasoning
               ▼
┌─────────────────────────────────────────────────────────┐
│              PaperTradingService                         │
│  Simulated fills, no real orders                        │
│  Tracks: balance, trades, equity curve                  │
└──────────────┬──────────────────────────────────────────┘
               ▼
┌─────────────────────────────────────────────────────────┐
│              BacktestResult                              │
│  Sharpe Ratio, Max Drawdown, Win Rate,                  │
│  Profit Factor, Equity Curve (JSON/CSV)                 │
└─────────────────────────────────────────────────────────┘
```

---

## Phase 3: Multi-Exchange Expansion (2–3 weeks, parallel with Phase 2)

### Steps
1. **Unified Interface**: `FuturesExchangeService` already partially defined ✅. Complete it.
2. **Priority Order**:
   - ✅ Binance — WebSocket complete (Phase 1).
   - ✅ Bybit — `BybitStreamingService` complete (Phase 1). REST execution via `BybitFuturesService`.
   - 🟡 dYdX v4 — finish partial implementation (gas signing for real execution).
   - 🔲 OKX, Hyperliquid, Gate.io, Deribit — add via **XChange** only where no native SDK exists.
3. **Config-driven agents** in `application.yaml`:
   ```yaml
   trading:
     agents:
       - exchange: BINANCE
         symbol: BTCUSDT
         interval: 1m
         strategy: MACD_RSI
       - exchange: BYBIT
         symbol: ETHUSDT
         interval: 5m
         strategy: BOLLINGER
   ```
4. Unified domain models + MapStruct (already present in `build.gradle` ✅).

### Data Flow (Phase 3 — Multi-Exchange)
```
┌──────────────────────────────────────────────────────────────┐
│                     Exchange Layer                            │
│  Binance WS ──► BinanceStreamingService      ✅             │
│  Bybit WS   ──► BybitStreamingService        ✅             │
│  OKX        ──► XChangeStreamingService      🔲             │
│  Hyperliquid──► XChangeStreamingService      🔲             │
│  dYdX v4    ──► DydxStreamingService         🟡             │
└──────────────────────────┬───────────────────────────────────┘
                           │ All normalized to StreamMarketDataEvent
                           ▼
              Kafka: market-data.{exchange}.{symbol}
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
         Agent(BTC)   Agent(ETH)   Agent(SOL)
         BINANCE      BYBIT        OKX
         MACD_RSI     BOLLINGER    RSI_ONLY
         [Bulkhead]   [Bulkhead]   [Bulkhead]
              │            │            │
              └────────────┴────────────┘
                           │
                  RiskManagementService
                  (global drawdown guard)
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        Binance REST  Bybit REST   XChange REST
        (execution)  (execution)  (execution)
```

---

## Phase 4: Modern React Frontend Dashboard (2–3 weeks)

### Steps
1. **Tech Stack**:
   - Initialize **React + Vite + TypeScript** in `frontend/`.
   - Add Tailwind + shadcn/ui + `lightweight-charts` + TanStack Query + Zustand.
2. **Backend Prerequisites** *(confirmed in `build.gradle`)* ✅:
   - `spring-boot-starter-websocket` already present.
   - `@EnableWebSocketMessageBroker` + STOMP endpoint for live frontend updates.
   - CORS config for `http://localhost:5173` (dev) and production domain.
3. **Features**:
   - Live PnL updates via WebSocket push.
   - "Agent Mind" view: Visualizing LLM `Reasoning` text in real time.
   - Backtest runner + interactive equity curve charts.
   - Start / Stop / Pause agent controls (requires Agent lifecycle state machine — Phase 2).

### Data Flow (Phase 4 — Frontend Integration)
```
┌─────────────────────────────────────────────────────────┐
│               Spring Boot Backend                        │
│                                                         │
│  AgentOrchestrator                                      │
│       │                                                 │
│       ├──► TradeExecutionEvent ──► Kafka                │
│       ├──► PnLUpdateEvent      ──► Kafka                │
│       └──► ReasoningEvent      ──► Kafka                │
│                                        │                │
│                            KafkaConsumer                │
│                                        │                │
│                         STOMP WebSocket Broker          │
│                         /topic/pnl                      │
│                         /topic/trades                   │
│                         /topic/reasoning                │
└──────────────────────────────┬──────────────────────────┘
                               │ WebSocket (STOMP)
                               ▼
┌─────────────────────────────────────────────────────────┐
│               React Frontend (Vite + TS)                 │
│                                                         │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  Live Chart  │  │ Agent Mind   │  │  Trade Log    │  │
│  │ lightweight- │  │  LLM Reasoning│  │  (fills,PnL)  │  │
│  │  charts      │  │  live text   │  │               │  │
│  └─────────────┘  └──────────────┘  └───────────────┘  │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Backtest Runner                                  │   │
│  │  CSV Upload → Run → Equity Curve Chart            │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## Phase 5: CI/CD & DevOps (1 week)

### Steps
1. **GitHub Actions**:
   - Create `.github/workflows/ci.yml` for Build, Test, and Docker Publish.
   - Use `actions/checkout@v4` and `actions/setup-java@v4`.
   - Add Testcontainers modules: `testcontainers:postgresql`, `testcontainers:redis`.
   - Create separate `e2eTest` Gradle source set/task (isolate from unit tests).
   - SonarQube + K8s manifest validation.
2. **Infrastructure**:
   - Finalize `docker-compose.yml` with Frontend service.
   - Confirm no orphaned containers remain (`docker-compose down --remove-orphans` ✅ done Feb 20).

### Data Flow (Phase 5 — CI/CD Pipeline)
```
git push origin platform-upgrade
          │
          ▼
┌─────────────────────────────────────────────────────────┐
│              GitHub Actions: ci.yml                      │
│                                                         │
│  ┌──────────┐   ┌───────────────────┐   ┌───────────┐  │
│  │  Build   │──►│  Test             │──►│  Publish  │  │
│  │ ./gradlew│   │  Unit + Integration│   │  Docker   │  │
│  │   build  │   │  (Testcontainers: │   │  ghcr.io  │  │
│  └──────────┘   │  Postgres, Redis, │   └───────────┘  │
│                 │  Kafka)           │                   │
│                 └───────────────────┘                   │
│                          │                              │
│                   SonarQube Analysis                    │
│                   K8s manifest validation               │
└─────────────────────────────────────────────────────────┘
          │ (main branch only)
          ▼
┌─────────────────────────────────────────────────────────┐
│              Deploy to Production                        │
│  kubectl apply -f k8s/                                  │
│  (Redis, Postgres, Kafka, Bot, Frontend)                │
└─────────────────────────────────────────────────────────┘
```

---

## Phase 6: Production Hardening (ongoing 4–6 weeks)

- Advanced risk management: ATR position sizing, daily loss limit, Redis-backed correlation matrix, global drawdown kill-switch.
- Strategy JAR hot-loading (deploy new strategies without restart).
- OpenTelemetry + Prometheus/Grafana (Actuator already present ✅).
- Telegram/Slack notifications for trade events.
- HashiCorp Vault for secret management (replace environment variable approach).

### Data Flow (Phase 6 — Observability + Alerts)
```
┌─────────────────────────────────────────────────────────┐
│               Spring Boot Bot                            │
│  OpenTelemetry Agent (auto-instrumentation)             │
│       │                                                 │
│       ├──► Traces  ──► Jaeger                          │
│       ├──► Metrics ──► Prometheus ──► Grafana Dashboard │
│       └──► Logs    ──► Loki       ──► Grafana Logs      │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│               Alert Pipeline                             │
│  TradeExecutionEvent                                    │
│       │                                                 │
│       ├──► Kafka ──► NotificationService               │
│                           │                             │
│                   ┌───────┴────────┐                   │
│                   ▼                ▼                   │
│             Telegram Bot      Slack Webhook             │
│          "BUY 0.01 BTC     "STOP: Max Drawdown         │
│           @ $45,000"         limit hit (-5%)"           │
└─────────────────────────────────────────────────────────┘
```

---

## Overall Progress Tracker

| Phase | Description | Status | Remaining |
|-------|-------------|--------|-----------|
| **Phase 0** | Foundation & Hygiene | ✅ **Complete** | — |
| **Phase 1** | Real-Time Data Fabric | ✅ **Complete** | Pre-Phase 2 agentic refactor ✅ done (sub-interface, KlineClosedEvent, Bulkhead) |
| **Phase 2** | Professional Backtesting | 🟡 **75% done** | DIP port interfaces ✅. LLMTradingAgent ✅. CsvBacktestAgentExecution ✅. BacktestService wired ✅. BacktestMetrics (8 fields) ✅. Remaining: equity curve JSON/CSV export, Phase 3 multi-exchange agent config |
| **Phase 3** | Multi-Exchange Expansion | 🔲 **Not started** | Depends on Phase 2 |
| **Phase 4** | React Frontend | 🔲 **Not started** | Depends on Phase 2 agent lifecycle |
| **Phase 5** | CI/CD & DevOps | 🔲 **Not started** | — |
| **Phase 6** | Production Hardening | 🔲 **Not started** | Ongoing |

---

## Decision Log

| Date | Decision | Reason |
|------|----------|--------|
| Feb 18, 2026 | Adopt XChange for non-SDK exchanges | Java-native, JVM-resident, type-safe, 4k+ stars. No sidecar needed. |
| Feb 19, 2026 | `BybitFuturesService` is REST-only | Confirmed via code review. WebSocket streaming built as separate `BybitStreamingService`. |
| Feb 19, 2026 | `StreamMarketDataEvent` confirmed | Normalized domain event with all required fields including `isCandleClosed`. |
| Feb 19, 2026 | XChange deferred to Phase 3 | Native SDKs (Binance + Bybit) sufficient for Phase 1. XChange only when new exchanges onboarded. |
| Feb 20, 2026 | `CachedGrokService` implemented | SHA-256 prompt hashing → Redis L1 → File L2. Synthetic fallback for offline backtest. Verified end-to-end. |
| Feb 20, 2026 | Kafka disabled in backtest profile | `trading.kafka.publish.enabled=false` + `spring.kafka.listener.auto-startup=false`. Eliminates 60s timeout and NetworkClient flood. |
| Feb 20, 2026 | Orphaned Docker containers removed | `docker-compose down --remove-orphans` run. `docker-compose.yml` confirmed clean. |
| Feb 20, 2026 | Agentic architecture refactor deferred | `TradingAgent` interface + `AgentOrchestrator` `@KafkaListener` refactor identified as pre-Phase 2 requirements. Not blocking Phase 1 completion. |
| Feb 20, 2026 | Option C chosen for `TradingAgent` evolution | Created `AgenticTradingAgent` sub-interface. `FuturesTradingBot` left untouched (ISP/OCP). New Phase 2 agents implement sub-interface only. |
| Feb 20, 2026 | Option C chosen for candle detection | Created `KlineClosedEvent` record in `tradingbot.domain.market`. `StreamMarketDataEvent` untouched. `AgentOrchestrator` subscribes to type-safe `KlineClosedEvent` via `@KafkaListener(topicPattern="kline-closed\\..*")`. |
| Feb 20, 2026 | `Bulkhead` per agent added | `resilience4j-bulkhead` dependency added. `dispatchWithBulkhead()` in `AgentOrchestrator` creates per-agent bulkhead (10 concurrent calls, 500 ms max wait) lazily via `BulkheadRegistry`. |
| Feb 20, 2026 | Phase 2 DIP port interfaces | `BarSeriesIndicator`, `TradingAgentFactory`, `BacktestAgentExecutionService`, `BacktestMetricsCalculator` created. Commits 9e04430. |
| Feb 20, 2026 | Phase 2 concrete implementations | `LLMTradingAgent` (MACD+RSI+LLM), `LLMTradingAgentFactory`, `CsvBacktestAgentExecutionService`, `StandardBacktestMetricsCalculator`. Commit 96dca24. |
| Feb 20, 2026 | BacktestService DIP wiring + cleanup | `FuturesTradingBot` instantiation removed. `MockSentimentAnalyzer` deleted. `TradeDirection.LONG` hardcode removed. `BacktestResult` (3 fields) replaced by `BacktestMetrics` (8 fields: Sharpe, MaxDD, WinRate, ProfitFactor, EquityCurve). Commit 00e6fe8. |
