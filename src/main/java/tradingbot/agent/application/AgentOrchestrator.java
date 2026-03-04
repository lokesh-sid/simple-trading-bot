package tradingbot.agent.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import tradingbot.agent.ReactiveTradingAgent;
import tradingbot.agent.application.strategy.AgentStrategy;
import tradingbot.agent.application.strategy.LangChain4jStrategy;
import tradingbot.agent.application.strategy.RAGEnhancedStrategy;
import tradingbot.agent.application.strategy.SimpleLLMStrategy;
import tradingbot.agent.domain.execution.ExecutionResult;
import tradingbot.agent.domain.execution.OrderExecutionGateway;
import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentDecision.Action;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.model.AgentStatus;
import tradingbot.agent.domain.repository.AgentRepository;
import tradingbot.agent.infrastructure.persistence.OrderEntity;
import tradingbot.agent.infrastructure.repository.OrderRepository;
import tradingbot.domain.market.KlineClosedEvent;
import tradingbot.domain.market.MarketEvent;
import tradingbot.domain.market.StreamMarketDataEvent;
import tradingbot.infrastructure.marketdata.ExchangeWebSocketClient;

/**
 * AgentOrchestrator - Coordinates the agent's sense-think-act loop
 *
 * REFACTORED: Strategy Pattern + Reactive WebSocket Integration
 *
 * Delegates to:
 * - LangChain4jStrategy: Agentic framework with tool use (default and recommended)
 *
 * Configure via: {@code agent.strategy=langchain4j}
 *
 * <p>The {@code rag} and {@code legacy} strategy names are deprecated. They are still
 * accepted at runtime through the deprecated constructor to avoid hard failures, but
 * will be removed in a future release.
 */
@Service
public class AgentOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentOrchestrator.class);

    // -------------------------------------------------------------------------
    // Throttling configuration (externalisable — see application.properties)
    // -------------------------------------------------------------------------

    /**
     * Default minimum interval between consecutive runs for any agent.
     * Override via {@code agent.throttle.default-ms} (default: 5 000 ms).
     */
    @Value("${agent.throttle.default-ms:5000}")
    private long defaultThrottleMs;

    /**
     * Per-symbol throttle overrides.
     * Configured as a SpEL map literal, e.g.:
     * {@code agent.throttle.per-symbol={BTCUSDT:1000, ETHUSDT:3000}}
     * Symbols not listed fall back to {@code defaultThrottleMs}.
     */
    @Value("#{${agent.throttle.per-symbol:{}}}")
    private Map<String, Long> perSymbolThrottleMs;
    
    private final AgentRepository agentRepository;
    private final AgentStrategy activeStrategy;
    private final ExchangeWebSocketClient webSocketClient;
    private final Scheduler agentScheduler;
    
    @Value("${websocket.enabled:false}")
    private boolean websocketEnabled;
    
    // Active WS subscriptions keyed by symbol
    private final Map<String, Disposable> activeSubscriptions = new ConcurrentHashMap<>();
    
    // Cache of active agents interested in each symbol to avoid DB hits on every tick
    // Key: Symbol (e.g. "BTCUSDT"), Value: Set of AgentIds
    private final Map<String, Set<AgentId>> symbolToAgentMap = new ConcurrentHashMap<>();
    
    // Throttling state: AgentId -> Last Execution Time
    private final Map<AgentId, Instant> lastExecutionTime = new ConcurrentHashMap<>();

    // --- Phase 1.5 / Pre-Phase 2: ReactiveTradingAgent event-driven path -------------

    /**
     * Reactive agents implementing {@link ReactiveTradingAgent}.
     * Populated by Spring when concrete implementations are registered as beans.
     * Empty until Phase 2 agent implementations are added.
     */
    private final List<ReactiveTradingAgent> agenticAgents;

    /**
     * Per-agent bulkhead registry — isolates one misbehaving agent from others.
     * A named {@link Bulkhead} is created lazily on first dispatch per agentId.
     */
    private final BulkheadRegistry bulkheadRegistry;

    /**
     * Unified order execution gateway (P1).  When non-null, agent decisions
     * are routed through this gateway after the signal is produced.
     * Nullable — when absent, decisions are only logged (pre-P1 behaviour).
     */
    private final OrderExecutionGateway executionGateway;
    private final OrderRepository orderRepository;
    private final PerformanceTrackingService performanceTrackingService;

    /**
     * Primary constructor — Spring uses this for dependency injection.
     *
     * <p>Only {@link LangChain4jStrategy} is accepted. If {@code agent.strategy}
     * is set to the deprecated values {@code rag} or {@code legacy} a warning is
     * emitted and {@code LangChain4jStrategy} is used as fallback, because the
     * deprecated beans are no longer injected here.
     */
    @Autowired
    public AgentOrchestrator(
            AgentRepository agentRepository,
            LangChain4jStrategy langChain4jStrategy,
            ExchangeWebSocketClient webSocketClient,
            List<ReactiveTradingAgent> agenticAgents,
            BulkheadRegistry bulkheadRegistry,
            @org.springframework.lang.Nullable OrderExecutionGateway executionGateway,
            OrderRepository orderRepository,
            PerformanceTrackingService performanceTrackingService,
            @Value("${agent.strategy:langchain4j}") String strategyName) {

        this.agentRepository = agentRepository;
        this.webSocketClient = webSocketClient;
        this.agenticAgents = agenticAgents;
        this.bulkheadRegistry = bulkheadRegistry;
        this.executionGateway = executionGateway;
        this.orderRepository = orderRepository;
        this.performanceTrackingService = performanceTrackingService;
        this.agentScheduler = Schedulers.boundedElastic();

        String normalized = strategyName.toLowerCase();
        if ("rag".equals(normalized) || "legacy".equals(normalized)) {
            logger.warn("Strategy '{}' is deprecated and its bean is no longer injected into this constructor. "
                    + "Falling back to LangChain4j. Migrate to agent.strategy=langchain4j.", strategyName);
        } else if (!"langchain4j".equals(normalized) && !"agentic".equals(normalized)) {
            logger.warn("Unknown strategy '{}', defaulting to LangChain4j.", strategyName);
        }
        this.activeStrategy = langChain4jStrategy;

        logger.info("╔════════════════════════════════════════════════════════════════╗");
        logger.info("║ AgentOrchestrator initialized with: {}                        ",
                   String.format("%-30s", activeStrategy.getStrategyName()));
        logger.info("╚════════════════════════════════════════════════════════════════╝");
    }

    /**
     * Deprecated constructor retained for callers that explicitly pass
     * {@link RAGEnhancedStrategy} or {@link SimpleLLMStrategy} beans
     * (e.g. legacy integration tests).
     *
     * @deprecated Inject only {@link LangChain4jStrategy} and use the primary
     *             constructor. This overload will be removed when the deprecated
     *             strategy classes are deleted.
     */
    @Deprecated(since = "LangChain4j migration", forRemoval = true)
    @SuppressWarnings("deprecation")
    public AgentOrchestrator(
            AgentRepository agentRepository,
            LangChain4jStrategy langChain4jStrategy,
            RAGEnhancedStrategy ragEnhancedStrategy,
            SimpleLLMStrategy legacyLLMStrategy,
            ExchangeWebSocketClient webSocketClient,
            List<ReactiveTradingAgent> agenticAgents,
            BulkheadRegistry bulkheadRegistry,
            @org.springframework.lang.Nullable OrderExecutionGateway executionGateway,
            OrderRepository orderRepository,
            PerformanceTrackingService performanceTrackingService,
            @Value("${agent.strategy:langchain4j}") String strategyName) {

        this.agentRepository = agentRepository;
        this.webSocketClient = webSocketClient;
        this.agenticAgents = agenticAgents;
        this.bulkheadRegistry = bulkheadRegistry;
        this.executionGateway = executionGateway;
        this.orderRepository = orderRepository;
        this.performanceTrackingService = performanceTrackingService;
        this.agentScheduler = Schedulers.boundedElastic();

        this.activeStrategy = switch (strategyName.toLowerCase()) {
            case "langchain4j", "agentic" -> langChain4jStrategy;
            case "rag" -> {
                logger.warn("Strategy 'rag' (RAGEnhancedStrategy) is deprecated and will be removed. "
                        + "Migrate to agent.strategy=langchain4j which includes a superior RAG pipeline.");
                yield ragEnhancedStrategy;
            }
            case "legacy" -> {
                logger.warn("Strategy 'legacy' (SimpleLLMStrategy) is deprecated and will be removed. "
                        + "Migrate to agent.strategy=langchain4j.");
                yield legacyLLMStrategy;
            }
            default -> {
                logger.warn("Unknown strategy: {}, defaulting to LangChain4j", strategyName);
                yield langChain4jStrategy;
            }
        };

        logger.info("╔════════════════════════════════════════════════════════════════╗");
        logger.info("║ AgentOrchestrator initialized with: {}                        ",
                   String.format("%-30s", activeStrategy.getStrategyName()));
        logger.info("╚════════════════════════════════════════════════════════════════╝");
    }

    @PostConstruct
    public void initWebSocket() {
        if (websocketEnabled) {
            logger.info("WebSocket enabled - starting reactive market data streams");
            refreshSubscriptions();
        } else {
            logger.info("WebSocket disabled - using polling mechanism");
        }
    }
    
    /**
     * Refreshes subscriptions based on currently active agents.
     * This method is idempotent and can be called periodically.
     */
    public synchronized void refreshSubscriptions() {
        if (!websocketEnabled) return;
        
        // 1. Fetch all currently active agents
        Iterable<Agent> activeAgents = agentRepository.findAllActive();
        
        // 2. Rebuild the symbol -> agent mapping
        Map<String, Set<AgentId>> newMapping = StreamSupport.stream(activeAgents.spliterator(), false)
            .collect(Collectors.groupingBy(
                Agent::getTradingSymbol,
                Collectors.mapping(Agent::getId, Collectors.toSet())
            ));
        
        // Update the cache atomically (or close enough for this use case)
        symbolToAgentMap.clear();
        symbolToAgentMap.putAll(newMapping);
        
        // 3. Ensure we have a subscription for each needed symbol
        newMapping.keySet().forEach(symbol -> {
            if (!activeSubscriptions.containsKey(symbol)) {
                logger.info("Subscribing to WebSocket for symbol: {}", symbol);
                Disposable sub = webSocketClient.streamTrades(symbol)
                    .doOnNext(this::handleMarketEvent)
                    .onErrorContinue((ex, obj) -> logger.error("Error in stream for {}: {}", symbol, ex.getMessage()))
                    .subscribe();
                activeSubscriptions.put(symbol, sub);
            }
        });
        
        // 4. (Optional) Cleanup subscriptions for symbols no longer needed
        // For simplicity in Phase 1, we keep subscriptions open even if agents drop off
        // to avoid churn. Can be added later.
    }

    /**
     * Reactively handles market events.
     * Finds interested agents and schedules their execution if not throttled.
     */
    private void handleMarketEvent(MarketEvent event) {
        // Find agents interested in this symbol
        // For Phase 1 we hardcode "BTCUSDT" mapping or use event.symbol()
        // If event.symbol() is null, assume global/default but it shouldn't be.
        String symbol = event.symbol() != null ? event.symbol() : "BTCUSDT"; 
        
        Set<AgentId> interestedAgents = symbolToAgentMap.get(symbol);
        
        if (interestedAgents == null || interestedAgents.isEmpty()) {
            return;
        }

        Instant now = Instant.now();

        // Iterate and schedule execution if throttled condition met
        for (AgentId agentId : interestedAgents) {
            // Check throttle
            Instant lastRun = lastExecutionTime.getOrDefault(agentId, Instant.MIN);
            
            if (now.toEpochMilli() - lastRun.toEpochMilli() > getThrottleMs(symbol)) {
                // Update time immediately to prevent double scheduling
                lastExecutionTime.put(agentId, now);
                
                // Offload the blocking/transactional agent logic to a separate scheduler
                // so we don't block the Netty/WebSocket thread.
                agentScheduler.schedule(() -> executeAgentTransactionWrapper(agentId, event));
            }
        }
    }

    /**
     * Non-transactional wrapper to call the transactional method.
     * This ensures the transaction boundary is clean.
     */
    private void executeAgentTransactionWrapper(AgentId agentId, MarketEvent event) {
        try {
            executeAgentTransaction(agentId, event);
        } catch (Exception e) {
            logger.error("Error in async agent execution for {}: {}", agentId, e.getMessage(), e);
        }
    }

    /**
     * Transactional execution of the agent logic.
     * Loads the fresh agent state and runs the strategy.
     */
    @Transactional
    protected void executeAgentTransaction(AgentId agentId, MarketEvent triggeringEvent) {
        // Reload agent to ensure we have fresh state in this transaction
        agentRepository.findById(agentId).ifPresentOrElse(agent -> {
            logger.debug("Executing strategy for agent {} triggered by {} @ {}", 
                agent.getName(), 
                (triggeringEvent instanceof StreamMarketDataEvent se) ? se.type() : "MARKET_EVENT", 
                triggeringEvent.price());
            
            // Execute iteration (Note: this modifies agent state)
            activeStrategy.executeIteration(agent, triggeringEvent);
            
            // Save updated state
            agentRepository.save(agent);
        }, () -> {
            logger.warn("Agent {} not found during execution, evicting from caches", agentId);
            // Remove stale agent from all in-memory caches so it stops receiving events.
            evictAgent(agentId);
        });
    }

    @PreDestroy
    public void cleanup() {
        activeSubscriptions.values().forEach(Disposable::dispose);
        activeSubscriptions.clear();
        agentScheduler.dispose(); // Shutdown scheduler
    }
    
    /**
     * Main agent loop - runs every 30 seconds
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void executeAgentLoop() { // Transactional removed from here as it delegates
        if (websocketEnabled) {
            // Use this loop to refresh subscriptions/cache in case new agents were added
            logger.debug("Heartbeat check (WebSocket active) - refreshing subscriptions");
            refreshSubscriptions(); 
            return; 
        }

        logger.debug("Starting agent orchestration cycle (Polling Mode)");
        
        // Polling implementation (Legacy)
        Iterable<Agent> activeAgents = agentRepository.findAllActive();
        int agentCount = 0;
        
        for (Agent agent : activeAgents) {
            try {
                // Throttle check for polling too? 
                // Currently only 30s loop, so native throttling is 30s.
                runSingleAgentIteration(agent);
                agentCount++;
            } catch (Exception e) {
                logger.error("Error in agent loop for agent {}: {}", 
                    agent.getId(), e.getMessage(), e);
            }
        }
        
        if (agentCount > 0) {
            logger.info("Completed cycle for {} agent(s) using {}", 
                agentCount, activeStrategy.getStrategyName());
        }
    }

    // -------------------------------------------------------------------------
    // Phase 1.5 / Pre-Phase 2 — Event-driven path via KlineClosedEvent
    // -------------------------------------------------------------------------

    /**
     * Consumes {@link KlineClosedEvent}s published to
     * {@code kline-closed.{symbol}} Kafka topics by exchange adapters.
     *
     * <p>Design notes (Option C — separate event type):
     * <ul>
     *   <li>This listener only sees fully-closed candles; no TRADE / tick
     *       noise ever reaches agent logic.</li>
     *   <li>A per-agent {@link Bulkhead} is acquired before dispatching so
     *       that one slow LLM call cannot starve other agents.</li>
     *   <li>In backtest profile this method is never invoked because
     *       {@code spring.kafka.listener.auto-startup=false}.</li>
     * </ul>
     *
     * <p>Topic pattern: {@code kline-closed.BTCUSDT}, {@code kline-closed.ETHUSDT}, …
     */
    @KafkaListener(
            topicPattern = "kline-closed\\..*",
            groupId = "agent-orchestrator-klines",
            containerFactory = "kafkaListenerContainerFactory")
    public void onKlineClosedEvent(KlineClosedEvent event) {
        if (agenticAgents.isEmpty()) {
            // No ReactiveTradingAgent beans registered yet (normal during Phase 1).
            logger.trace("[KlineListener] No ReactiveTradingAgent beans — skipping dispatch for {}/{}",
                    event.exchange(), event.symbol());
            return;
        }

        agenticAgents.stream()
                .filter(agent -> event.symbol().equals(agent.getSymbol()))
                .filter(agent -> agent.getStatus() == AgentStatus.ACTIVE)
                .forEach(agent -> dispatchWithBulkhead(agent, event));
    }

    /**
     * Acquires the per-agent {@link Bulkhead} and dispatches the agent
     * asynchronously on the bounded-elastic scheduler.
     *
     * <p>A bulkhead is created lazily with conservative defaults
     * (10 concurrent calls, 500 ms max wait) if one does not yet exist.
     */
    private void dispatchWithBulkhead(ReactiveTradingAgent agent, KlineClosedEvent event) {
        String bulkheadName = "agent-" + agent.getId();
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(
                bulkheadName,
                () -> BulkheadConfig.custom()
                        .maxConcurrentCalls(10)
                        .maxWaitDuration(Duration.ofMillis(500))
                        .build());

        agentScheduler.schedule(() -> {
            if (!bulkhead.tryAcquirePermission()) {
                logger.warn("[Bulkhead] Agent {} is saturated — dropping event for {}",
                        agent.getId(), event.symbol());
                return;
            }
            try {
                agent.onKlineClosed(event)
                        .doOnSuccess(decision -> {
                            logger.info(
                                    "[AgenticAgent] {} → {} (confidence {}%) for {}",
                                    agent.getId(), decision.action(), decision.confidence(), event.symbol());
                            // P1: Route decision through the execution gateway
                            if (executionGateway != null && decision.isEntry()) {
                                try {
                                    double price = event.close().doubleValue();
                                    ExecutionResult result = executionGateway.execute(
                                            decision, event.symbol(), price);
                                    logger.info("[AgenticAgent] {} execution: {} success={} fill={}",
                                            agent.getId(), result.action(), result.success(), result.fillPrice());

                                    // P2: Persist the order history to the database
                                    try {
                                        OrderEntity entity = new OrderEntity();
                                        entity.setId(UUID.randomUUID().toString());
                                        entity.setAgentId(agent.getId());
                                        entity.setSymbol(event.symbol());
                                        entity.setDirection(decision.action() == Action.BUY ? OrderEntity.Direction.LONG : OrderEntity.Direction.SHORT);
                                        entity.setPrice(price);
                                        entity.setQuantity(result.fillQuantity() > 0 ? result.fillQuantity() : (decision.quantity() != null ? decision.quantity() : 1.0));
                                        entity.setCreatedAt(Instant.now());
                                        if (result.success() && result.action() != ExecutionResult.ExecutionAction.NOOP) {
                                            entity.setStatus(OrderEntity.Status.EXECUTED);
                                            entity.setExecutedAt(Instant.now());
                                            entity.setExchangeOrderId(result.exchangeOrderId());
                                            entity.setRealizedPnl(result.realizedPnl());
                                        } else {
                                            entity.setStatus(OrderEntity.Status.FAILED);
                                            entity.setFailureReason(result.reason());
                                        }
                                        orderRepository.save(entity);
                                        
                                        // P3: Track performance metrics
                                        performanceTrackingService.recordExecution(agent.getId(), result);
                                    } catch (Exception dbEx) {
                                        logger.error("[AgenticAgent] {} failed to persist order history or performance: {}", agent.getId(), dbEx.getMessage(), dbEx);
                                    }
                                } catch (Exception exGw) {
                                    logger.error("[AgenticAgent] {} gateway error: {}",
                                            agent.getId(), exGw.getMessage(), exGw);
                                }
                            }
                        })
                        .doOnError(ex -> logger.error(
                                "[AgenticAgent] {} error on kline {}: {}",
                                agent.getId(), event.symbol(), ex.getMessage(), ex))
                        .subscribe();
            } finally {
                bulkhead.releasePermission();
            }
        });
    }

    /**
     * Run a single iteration using the active strategy (Polling version)
     */
    @Transactional
    public void runSingleAgentIteration(Agent agent) {
        logger.info("Running iteration for agent: {} using {} strategy", 
            agent.getName(), activeStrategy.getStrategyName());
        
        try {
            // Polling path — no triggering event available
            activeStrategy.executeIteration(agent, null);
            agentRepository.save(agent);
        } catch (Exception e) {
            logger.error("Failed to complete iteration for agent {}: {}", 
                agent.getId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Returns the throttle interval for a given symbol, falling back to the
     * default if no per-symbol override is configured.
     */
    private long getThrottleMs(String symbol) {
        return perSymbolThrottleMs.getOrDefault(symbol, defaultThrottleMs);
    }

    // -------------------------------------------------------------------------
    // Cache management
    // -------------------------------------------------------------------------

    /**
     * Evicts a stopped or deleted agent from all in-memory caches.
     *
     * <p>Must be called by any service layer method that stops, pauses, or deletes
     * an agent so that stale entries in {@link #symbolToAgentMap} and
     * {@link #lastExecutionTime} do not cause the orchestrator to dispatch
     * market events to a non-running agent.</p>
     *
     * @param agentId the agent to evict
     */
    public void evictAgent(AgentId agentId) {
        symbolToAgentMap.forEach((symbol, agentIds) -> agentIds.remove(agentId));
        symbolToAgentMap.entrySet().removeIf(e -> e.getValue().isEmpty());
        lastExecutionTime.remove(agentId);
        logger.info("[Orchestrator] Evicted agent {} from symbolToAgentMap and lastExecutionTime caches",
                agentId);
    }

    // Repository query methods
    public Agent getAgent(AgentId agentId) {
        return agentRepository.findById(agentId)
            .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
    }
    
    public Iterable<Agent> getAllAgents() {
        return agentRepository.findAll();
    }
    
    public Iterable<Agent> getActiveAgents() {
        return agentRepository.findAllActive();
    }
}
