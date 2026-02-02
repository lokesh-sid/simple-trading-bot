package tradingbot.agent.application.strategy;

import tradingbot.agent.domain.model.Agent;

/**
 * Strategy interface for different agent reasoning approaches
 * 
 * Implementations:
 * - LangChain4jStrategy: Agentic framework with tool use
 * - RAGEnhancedStrategy: RAG + manual LLM
 * - LegacyLLMStrategy: Original implementation
 */
public interface AgentStrategy {
    
    /**
     * Execute one iteration of the agent loop
     * 
     * @param agent The agent to run
     */
    void executeIteration(Agent agent);
    
    /**
     * Get the name of this strategy
     */
    String getStrategyName();
}
