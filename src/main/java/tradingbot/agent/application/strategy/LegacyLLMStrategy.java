package tradingbot.agent.application.strategy;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.Order;
import tradingbot.agent.domain.model.Perception;
import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.ReasoningContext;
import tradingbot.agent.infrastructure.llm.LLMProvider;
import tradingbot.agent.service.OrderPlacementService;
import tradingbot.bot.model.MarketData;
import tradingbot.domain.market.StreamMarketDataEvent;

/**
 * Legacy LLM strategy (original implementation)
 * 
 * Uses direct LLM calls without RAG or tool use
 */
@Component
public class LegacyLLMStrategy implements AgentStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(LegacyLLMStrategy.class);
    
    private final LLMProvider llmProvider;
    private final OrderPlacementService orderPlacementService;
    
    public LegacyLLMStrategy(
            LLMProvider llmProvider,
            OrderPlacementService orderPlacementService) {
        this.llmProvider = llmProvider;
        this.orderPlacementService = orderPlacementService;
    }
    
    @Override
    public void executeIteration(Agent agent, StreamMarketDataEvent triggeringEvent) {
        logger.info("[LEGACY] Agent {} using traditional LLM (triggerPrice={})",
                agent.getId(), triggeringEvent != null ? triggeringEvent.price() : "n/a");

        // 1. PERCEIVE — use live event price when available, fallback stub for polling
        Perception perception = perceiveMarket(agent, triggeringEvent);
        agent.perceive(perception);
        
        logger.debug("Agent {} perceived market - Price: {} Trend: {}", 
                    agent.getId(), perception.getCurrentPrice(), perception.getTrend());
        
        // 2. REASON
        ReasoningContext context = buildReasoningContext(agent, perception);
        Reasoning reasoning = llmProvider.generateReasoning(context);
        agent.reason(reasoning);
        
        logger.info("Agent {} reasoning complete. Recommendation: {} (Confidence: {}%)", 
                   agent.getId(), reasoning.getRecommendation(), reasoning.getConfidence());
        
        // 3. ACT
        Order order = orderPlacementService.processReasoning(agent, perception, reasoning);
        
        if (order != null) {
            logger.info("Agent {} placed order: {} {} @ ${} (qty: {})",
                agent.getId(), order.getDirection(), order.getSymbol(),
                String.format("%.2f", order.getPrice()), order.getQuantity());
        } else {
            logger.info("Agent {} decided to HOLD (confidence too low or unclear direction)",
                agent.getId());
        }
        
        logRecommendation(agent, reasoning);
    }
    
    @Override
    public String getStrategyName() {
        return "Legacy LLM";
    }
    
    private Perception perceiveMarket(Agent agent, StreamMarketDataEvent event) {
        if (event != null) {
            // Use the real-time price from the triggering WebSocket event
            return new Perception(
                agent.getTradingSymbol(),
                event.price().doubleValue(),
                "UNKNOWN",   // trend derived from ta4j indicators, not raw tick
                "UNKNOWN",   // sentiment enrichment is a separate concern
                event.quantity().doubleValue(),
                event.timestamp()
            );
        }
        // Polling fallback — stub data (Phase 2: replace with exchange REST call)
        MarketData data = getMarketData(agent.getTradingSymbol());
        return new Perception(
            agent.getTradingSymbol(),
            data.currentPrice(),
            data.trend(),
            data.sentiment(),
            data.volume(),
            Instant.now()
        );
    }

    private MarketData getMarketData(String symbol) {
        // TODO(Phase 2): replace with real REST market-data fetch
        return new MarketData(45000.0, 2.5, 1000000, "UPTREND", "BULLISH", 0.7);
    }
    
    private ReasoningContext buildReasoningContext(Agent agent, Perception perception) {
        return new ReasoningContext(
            agent.getGoal(),
            perception,
            agent.getTradingSymbol(),
            agent.getCapital(),
            agent.getState().getIterationCount()
        );
    }
    
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
}
