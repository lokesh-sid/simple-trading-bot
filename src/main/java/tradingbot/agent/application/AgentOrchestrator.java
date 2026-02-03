package tradingbot.agent.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tradingbot.agent.application.strategy.AgentStrategy;
import tradingbot.agent.application.strategy.LangChain4jStrategy;
import tradingbot.agent.application.strategy.LegacyLLMStrategy;
import tradingbot.agent.application.strategy.RAGEnhancedStrategy;
import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.repository.AgentRepository;

/**
 * AgentOrchestrator - Coordinates the agent's sense-think-act loop
 * 
 * REFACTORED: Strategy Pattern Implementation
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
    
    private final AgentRepository agentRepository;
    private final AgentStrategy activeStrategy;
    
    public AgentOrchestrator(
            AgentRepository agentRepository,
            LangChain4jStrategy langChain4jStrategy,
            RAGEnhancedStrategy ragEnhancedStrategy,
            LegacyLLMStrategy legacyLLMStrategy,
            @Value("${agent.strategy:langchain4j}") String strategyName) {
        
        this.agentRepository = agentRepository;
        
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
    
    /**
     * Main agent loop - runs every 30 seconds
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    @Transactional
    public void executeAgentLoop() {
        logger.debug("Starting agent orchestration cycle");
        
        Iterable<Agent> activeAgents = agentRepository.findAllActive();
        int agentCount = 0;
        
        for (Agent agent : activeAgents) {
            try {
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
     * Run a single iteration using the active strategy
     */
    private void runSingleAgentIteration(Agent agent) {
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
            .orElseThrow(() -> new AgentNotFoundException("Agent not found: " + agentId));
    }
    
    public Iterable<Agent> getAllAgents() {
        return agentRepository.findAll();
    }
    
    public Iterable<Agent> getActiveAgents() {
        return agentRepository.findAllActive();
    }
}
