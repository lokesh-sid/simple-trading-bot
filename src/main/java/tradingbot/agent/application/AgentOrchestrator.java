package tradingbot.agent.application;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import tradingbot.agent.application.strategy.AgentStrategy;
import tradingbot.agent.application.strategy.LangChain4jStrategy;
import tradingbot.agent.application.strategy.LegacyLLMStrategy;
import tradingbot.agent.application.strategy.RAGEnhancedStrategy;
import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.repository.AgentRepository;
import tradingbot.domain.market.StreamMarketDataEvent;
import tradingbot.infrastructure.marketdata.ExchangeWebSocketClient;

/**
 * AgentOrchestrator - Coordinates the agent's sense-think-act loop
 * 
 * REFACTORED: Strategy Pattern + Reactive WebSocket Integration
 * 
 * Delegates to strategy implementations:
 * - LangChain4jStrategy: Agentic framework with tool use (default)
 * - RAGEnhancedStrategy: RAG + manual LLM
 * - LegacyLLMStrategy: Original implementation
 * 
 * Configure via: agent.strategy={langchain4j|rag|legacy}
 */
@Service
public class AgentOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentOrchestrator.class);
    
    // Throttling configuration
    private static final long AGENT_THROTTLE_MS = 5000; // Minimum 5s between runs per agent
    
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
    
    public AgentOrchestrator(
            AgentRepository agentRepository,
            LangChain4jStrategy langChain4jStrategy,
            RAGEnhancedStrategy ragEnhancedStrategy,
            LegacyLLMStrategy legacyLLMStrategy,
            ExchangeWebSocketClient webSocketClient,
            @Value("${agent.strategy:langchain4j}") String strategyName) {
        
        this.agentRepository = agentRepository;
        this.webSocketClient = webSocketClient;
        // Create a bounded elastic scheduler for offloading blocking agent logic
        this.agentScheduler = Schedulers.boundedElastic();
        
        // Select strategy based on configuration
        this.activeStrategy = switch (strategyName.toLowerCase()) {
            case "langchain4j", "agentic" -> langChain4jStrategy;
            case "rag" -> ragEnhancedStrategy;
            case "legacy" -> legacyLLMStrategy;
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
                agent -> "BTCUSDT", // TODO: Replace with agent.getSymbol() when available on Agent
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
    private void handleMarketEvent(StreamMarketDataEvent event) {
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
            
            if (now.toEpochMilli() - lastRun.toEpochMilli() > AGENT_THROTTLE_MS) {
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
    private void executeAgentTransactionWrapper(AgentId agentId, StreamMarketDataEvent event) {
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
    protected void executeAgentTransaction(AgentId agentId, StreamMarketDataEvent triggeringEvent) {
        // Reload agent to ensure we have fresh state in this transaction
        agentRepository.findById(agentId).ifPresentOrElse(agent -> {
            logger.debug("Executing strategy for agent {} triggered by {} @ {}", 
                agent.getName(), triggeringEvent.type(), triggeringEvent.price());
            
            // Execute iteration (Note: this modifies agent state)
            activeStrategy.executeIteration(agent); 
            
            // Save updated state
            agentRepository.save(agent);
        }, () -> {
            logger.warn("Agent {} not found during execution, removing from cache", agentId);
            // Remove from cache if not found
             symbolToAgentMap.values().forEach(set -> set.remove(agentId));
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
    
    /**
     * Run a single iteration using the active strategy (Polling version)
     */
    @Transactional
    public void runSingleAgentIteration(Agent agent) {
        logger.info("Running iteration for agent: {} using {} strategy", 
            agent.getName(), activeStrategy.getStrategyName());
        
        try {
            activeStrategy.executeIteration(agent);
            agentRepository.save(agent);
        } catch (Exception e) {
            logger.error("Failed to complete iteration for agent {}: {}", 
                agent.getId(), e.getMessage(), e);
            throw e;
        }
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
