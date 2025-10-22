package tradingbot.agent.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.model.Perception;
import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.ReasoningContext;
import tradingbot.agent.domain.repository.AgentRepository;
import tradingbot.agent.infrastructure.llm.LLMProvider;

/**
 * AgentOrchestrator - Coordinates the agent's sense-think-act loop
 * 
 * This is the "brain" that runs each agent's decision-making cycle:
 * 1. PERCEIVE - Observe market conditions
 * 2. REASON - Use LLM to decide what to do
 * 3. ACT - Execute the recommendation (future: integrate with trading)
 * 
 * MVP Simplification:
 * - Single-threaded execution
 * - Basic market data perception
 * - Grok-powered reasoning
 * - Logging recommendations (no actual trading yet)
 */
@Service
public class AgentOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentOrchestrator.class);
    
    private final AgentRepository agentRepository;
    private final LLMProvider llmProvider;
    
    public AgentOrchestrator(
            AgentRepository agentRepository,
            LLMProvider llmProvider) {
        this.agentRepository = agentRepository;
        this.llmProvider = llmProvider;
    }
    
    /**
     * Main agent loop - runs every 30 seconds for each active agent
     * 
     * In production, this would run more frequently and in parallel.
     * For MVP, we keep it simple with periodic execution.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000) // Every 30 seconds
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
                // Continue with other agents even if one fails
            }
        }
        
        if (agentCount > 0) {
            logger.info("Completed agent orchestration cycle for {} active agent(s)", agentCount);
        }
    }
    
    /**
     * Run a single iteration of the agent loop
     */
    private void runSingleAgentIteration(Agent agent) {
        logger.info("Running iteration for agent: {} ({})", agent.getName(), agent.getId());
        
        try {
            // 1. PERCEIVE - Gather current market state
            Perception perception = perceiveMarket(agent);
            agent.perceive(perception);
            
            logger.debug("Agent {} perceived market - Price: {} Trend: {}", 
                        agent.getId(), perception.getCurrentPrice(), perception.getTrend());
            
            // 2. REASON - Use Grok to decide what to do
            ReasoningContext context = buildReasoningContext(agent, perception);
            Reasoning reasoning = llmProvider.generateReasoning(context);
            agent.reason(reasoning);
            
            logger.info("Agent {} reasoning complete. Recommendation: {} (Confidence: {}%)", 
                       agent.getId(), reasoning.getRecommendation(), reasoning.getConfidence());
            
            // 3. ACT - Log the recommendation (future: execute trades)
            logRecommendation(agent, reasoning);
            
            // 4. SAVE - Persist agent state
            agentRepository.save(agent);
            
        } catch (Exception e) {
            logger.error("Failed to complete iteration for agent {}: {}", 
                        agent.getId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Perceive market conditions for the agent's trading symbol
     */
    private Perception perceiveMarket(Agent agent) {
        // MVP: Simplified market data gathering
        // TODO: Replace with actual market data service
        
        MarketData data = getMarketData(agent.getTradingSymbol());
        
        return new Perception(
            agent.getTradingSymbol(),
            data.getCurrentPrice(),
            data.getTrend(),
            data.getSentiment(),
            data.getVolume(),
            java.time.Instant.now()
        );
    }
    
    /**
     * Get market data (MVP: Dummy data)
     * TODO: Replace with actual Binance API integration
     */
    private MarketData getMarketData(String symbol) {
        // MVP: Return dummy data for demonstration
        return new MarketData(
            45000.0,  // currentPrice
            2.5,      // priceChange24h
            1000000,  // volume
            "UPTREND",
            "BULLISH",
            0.7       // sentimentScore
        );
    }
    
    /**
     * Build reasoning context for LLM
     */
    private ReasoningContext buildReasoningContext(Agent agent, Perception perception) {
        return new ReasoningContext(
            agent.getGoal(),
            perception,
            agent.getTradingSymbol(),
            agent.getCapital(),
            agent.getState().getIterationCount()
        );
    }
    
    /**
     * Log the agent's recommendation
     * 
     * In production, this would:
     * - Execute trades through Binance API
     * - Update portfolio state
     * - Record trade history
     * - Calculate performance metrics
     */
    private void logRecommendation(Agent agent, Reasoning reasoning) {
        logger.info("""
            
            ╔════════════════════════════════════════════════════════════════╗
            ║ AGENT RECOMMENDATION                                           ║
            ╠════════════════════════════════════════════════════════════════╣
            ║ Agent: {} ({})
            ║ Symbol: {}
            ║ Time: {}
            ╠════════════════════════════════════════════════════════════════╣
            ║ RECOMMENDATION:
            ║ {}
            ╠════════════════════════════════════════════════════════════════╣
            ║ CONFIDENCE: {}%
            ╠════════════════════════════════════════════════════════════════╣
            ║ RISK ASSESSMENT:
            ║ {}
            ╚════════════════════════════════════════════════════════════════╝
            """,
            agent.getName(), agent.getId(),
            agent.getTradingSymbol(),
            Instant.now(),
            reasoning.getRecommendation(),
            reasoning.getConfidence(),
            reasoning.getRiskAssessment()
        );
    }
    
    /**
     * Get agent by ID
     */
    public Agent getAgent(AgentId agentId) {
        return agentRepository.findById(agentId)
            .orElseThrow(() -> new AgentNotFoundException("Agent not found: " + agentId));
    }
    
    /**
     * Get all agents
     */
    public Iterable<Agent> getAllAgents() {
        return agentRepository.findAll();
    }
    
    /**
     * Get all active agents
     */
    public Iterable<Agent> getActiveAgents() {
        return agentRepository.findAllActive();
    }
}


