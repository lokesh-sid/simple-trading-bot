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
import tradingbot.agent.service.OrderPlacementService;
import tradingbot.agent.service.RAGService;
import tradingbot.bot.model.MarketData;
import tradingbot.domain.market.MarketEvent;

/**
 * RAG-enhanced LLM strategy.
 *
 * <p>Retrieves similar historical trades via the RAG service and augments LLM
 * reasoning with that context. Superseded by {@link LangChain4jStrategy}, which
 * provides the same RAG retrieval with additional capabilities: configurable
 * context limits ({@code rag.strategy.context-limit}), cross-symbol memory
 * retrieval, write-back of new experiences, and autonomous tool-based market
 * data access. The polling fallback ({@code getMarketData}) in this class
 * still returns hardcoded stub data and will never be fixed.
 *
 * @deprecated Use {@link LangChain4jStrategy}. This class will be removed
 *             in a future release. Configure via {@code agent.strategy=langchain4j}.
 */
@Deprecated(since = "LangChain4j migration", forRemoval = true)
@Component
public class RAGEnhancedStrategy implements AgentStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(RAGEnhancedStrategy.class);
    
    private final RAGService ragService;
    private final OrderPlacementService orderPlacementService;
    
    public RAGEnhancedStrategy(
            RAGService ragService,
            OrderPlacementService orderPlacementService) {
        this.ragService = ragService;
        this.orderPlacementService = orderPlacementService;
    }
    
    @Override
    public void executeIteration(Agent agent, MarketEvent triggeringEvent) {
        logger.info("[RAG] Agent {} using RAG-enhanced reasoning (triggerPrice={})",
                agent.getId(), triggeringEvent != null ? triggeringEvent.price() : "n/a");

        // 1. PERCEIVE — use live event price when available, fallback stub for polling
        Perception perception = perceiveMarket(agent, triggeringEvent);
        agent.perceive(perception);
        
        logger.debug("Agent {} perceived market - Price: {} Trend: {}", 
                    agent.getId(), perception.getCurrentPrice(), perception.getTrend());
        
        // 2. REASON with RAG
        ReasoningContext context = buildReasoningContext(agent, perception);
        Reasoning reasoning = ragService.generateReasoningWithRAG(agent, context);
        agent.reason(reasoning);
        
        logger.info("Agent {} reasoning: {} ({}%)", 
            agent.getId(), reasoning.getRecommendation(), reasoning.getConfidence());
        
        // 3. ACT
        Order order = orderPlacementService.processReasoning(agent, perception, reasoning);
        
        if (order != null) {
            logger.info("Agent {} placed order: {} {} @ ${} (qty: {})",
                agent.getId(), order.getDirection(), order.getSymbol(),
                String.format("%.2f", order.getPrice()), order.getQuantity());
        } else {
            logger.info("Agent {} decided to HOLD", agent.getId());
        }
        
        logRecommendation(agent, reasoning);
    }
    
    @Override
    public String getStrategyName() {
        return "RAG-Enhanced LLM";
    }
    
    private Perception perceiveMarket(Agent agent, MarketEvent event) {
        if (event != null) {
            // Use the real-time price from the triggering market event
            return new Perception(
                agent.getTradingSymbol(),
                event.price().doubleValue(),
                "UNKNOWN",   // trend derived from ta4j indicators, not raw tick
                "UNKNOWN",   // sentiment enrichment is a separate concern
                event.volume().doubleValue(),
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
            ║ RAG-ENHANCED RECOMMENDATION                                    ║
            ╠════════════════════════════════════════════════════════════════╣
            ║ Agent: {} ({})
            ║ Symbol: {}
            ║ Recommendation: {}
            ║ Confidence: {}%
            ║ Risk: {}
            ╚════════════════════════════════════════════════════════════════╝
            """,
            agent.getName(), agent.getId(),
            agent.getTradingSymbol(),
            reasoning.getRecommendation(),
            reasoning.getConfidence(),
            reasoning.getRiskAssessment()
        );
    }
}
