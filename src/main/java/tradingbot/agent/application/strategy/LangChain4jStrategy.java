package tradingbot.agent.application.strategy;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.TradeMemory;
import tradingbot.agent.service.RAGService;
import tradingbot.agent.service.TradingAgentService;

/**
 * LangChain4j-based agentic strategy
 * 
 * The agent autonomously:
 * - Calls tools to gather market data
 * - Analyzes conditions using reasoning
 * - Places orders through tool invocation
 * - Maintains context across calls
 */
@Component
public class LangChain4jStrategy implements AgentStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(LangChain4jStrategy.class);
    
    private final TradingAgentService tradingAgentService;
    private final RAGService ragService;
    
    @Value("${rag.enabled:true}")
    private boolean ragEnabled;
    
    public LangChain4jStrategy(
            TradingAgentService tradingAgentService,
            RAGService ragService) {
        this.tradingAgentService = tradingAgentService;
        this.ragService = ragService;
    }
    
    @Override
    public void executeIteration(Agent agent) {
        logger.info("[AGENTIC] Agent {} analyzing market with tool access", agent.getId());
        
        // 1. Prepare RAG context (historical learnings)
        String ragContext = prepareRAGContext(agent);
        
        // 2. Invoke the agent - it will autonomously call tools and make decisions
        String agentResponse = tradingAgentService.analyzeAndDecide(
            agent.getTradingSymbol(),
            agent.getGoal().toString(),
            agent.getCapital(),
            agent.getState().getIterationCount(),
            ragContext
        );
        
        logger.info("Agent {} decision: {}", agent.getId(), agentResponse);
        
        // 3. Update agent state
        agent.getState().incrementIteration();

        // 4. Parse and store the reasoning
        Reasoning reasoning = parseAgentResponse(agentResponse);
        agent.reason(reasoning);
        
        // 5. Store this experience in RAG for future learning
        if (ragEnabled) {
            storeExperience(agent, reasoning);
        }
        
        logAgentDecision(agent, agentResponse);
    }
    
    @Override
    public String getStrategyName() {
        return "LangChain4j Agentic";
    }
    
    /**
     * Format RAG context from similar historical trades
     */
    private String prepareRAGContext(Agent agent) {
        if (!ragEnabled) {
            return "";
        }
        
        // TODO: Implement retrieveSimilarTrades method in RAGService
        List<TradeMemory> similarTrades = List.of();
        
        if (similarTrades.isEmpty()) {
            return "No historical trading data available yet.";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("Historical Trading Experiences:\n\n");
        
        for (int i = 0; i < similarTrades.size(); i++) {
            TradeMemory memory = similarTrades.get(i);
            context.append(String.format("%d. %s - %s\n", 
                i + 1, memory.getSymbol(), memory.getOutcome()));
            context.append(String.format("   Direction: %s\n", memory.getDirection()));
            context.append(String.format("   Entry: $%.2f\n", memory.getEntryPrice()));
            context.append(String.format("   Result: %s\n\n", memory.getOutcome()));
        }
        
        return context.toString();
    }
    
    /**
     * Parse agent response into Reasoning domain object
     */
    private Reasoning parseAgentResponse(String agentResponse) {
        int confidence = 70;
        // Case-insensitive check and split
        if (agentResponse.toLowerCase().contains("confidence")) {
            String[] parts = agentResponse.split("(?i)confidence[:\\s]+");
            if (parts.length > 1) {
                String confStr = parts[1].replaceAll("\\D", "");
                if (!confStr.isEmpty()) {
                    confidence = Integer.parseInt(confStr.substring(0, Math.min(2, confStr.length())));
                }
            }
        }
        
        String recommendation = "HOLD";
        if (agentResponse.contains("BUY")) {
            recommendation = "BUY";
        } else if (agentResponse.contains("SELL")) {
            recommendation = "SELL";
        }

        return new Reasoning(
            "Market analysis completed",
            agentResponse,
            "Agent used tools to analyze market and make decision",
            recommendation,
            confidence,
            Instant.now()
        );
    }
    
    /**
     * Store this trading experience in RAG for future learning
     */
    private void storeExperience(Agent agent, Reasoning reasoning) {
        try {
            // TODO: Store trade memory properly once executed
            logger.debug("Stored trade memory for future RAG retrieval");
        } catch (Exception e) {
            logger.warn("Failed to store trade memory: {}", e.getMessage());
        }
    }
    
    /**
     * Log the agent's decision
     */
    private void logAgentDecision(Agent agent, String decision) {
        logger.info("""
            
            ╔════════════════════════════════════════════════════════════════╗
            ║ AGENTIC DECISION (LangChain4j)                                 ║
            ╠════════════════════════════════════════════════════════════════╣
            ║ Agent: {} ({})
            ║ Symbol: {}
            ║ Time: {}
            ╠════════════════════════════════════════════════════════════════╣
            ║ DECISION:
            ║ {}
            ╚════════════════════════════════════════════════════════════════╝
            """,
            agent.getName(), agent.getId(),
            agent.getTradingSymbol(),
            Instant.now(),
            decision
        );
    }
}
