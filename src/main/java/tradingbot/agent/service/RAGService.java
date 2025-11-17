package tradingbot.agent.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.Perception;
import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.ReasoningContext;
import tradingbot.agent.domain.model.TradeDirection;
import tradingbot.agent.domain.model.TradeMemory;
import tradingbot.agent.domain.model.TradeOutcome;
import tradingbot.agent.infrastructure.llm.LLMProvider;

/**
 * RAGService - Orchestrates the Retrieval-Augmented Generation pipeline
 * 
 * This service implements the full RAG workflow:
 * 1. Embed the current scenario
 * 2. Retrieve similar past experiences from vector DB
 * 3. Build augmented context with memories
 * 4. Generate reasoning with LLM
 * 5. Store the new experience as a memory
 */
@Service
public class RAGService {
    
    private static final Logger logger = LoggerFactory.getLogger(RAGService.class);
    
    private final EmbeddingService embeddingService;
    private final MemoryStoreService memoryStore;
    private final ContextBuilder contextBuilder;
    private final LLMProvider llmProvider;
    
    @Value("${rag.retrieval.top-k:5}")
    private int retrievalTopK;
    
    @Value("${rag.retrieval.similarity-threshold:0.7}")
    private double similarityThreshold;
    
    @Value("${rag.retrieval.max-age-days:90}")
    private int maxAgeDays;
    
    public RAGService(
            EmbeddingService embeddingService,
            MemoryStoreService memoryStore,
            ContextBuilder contextBuilder,
            LLMProvider llmProvider) {
        this.embeddingService = embeddingService;
        this.memoryStore = memoryStore;
        this.contextBuilder = contextBuilder;
        this.llmProvider = llmProvider;
    }
    
    /**
     * Generate reasoning with RAG enhancement
     * 
     * @param agent The agent making the decision
     * @param context The current market context
     * @return LLM-generated reasoning augmented with historical memories
     */
    public Reasoning generateReasoningWithRAG(Agent agent, ReasoningContext context) {
        try {
            logger.info("Starting RAG-enhanced reasoning for agent {} on symbol {}",
                agent.getId(), context.getTradingSymbol());
            
            // Step 1: Build scenario description and embed it
            String scenarioDescription = contextBuilder.buildScenarioDescription(
                context.getPerception()
            );
            logger.debug("Scenario: {}", scenarioDescription);
            
            double[] queryEmbedding = embeddingService.embed(scenarioDescription);
            logger.debug("Generated embedding with {} dimensions", queryEmbedding.length);
            
            // Step 2: Retrieve similar memories
            List<TradeMemory> similarMemories = memoryStore.findSimilar(
                queryEmbedding,
                context.getTradingSymbol(),
                retrievalTopK,
                similarityThreshold,
                maxAgeDays
            );
            
            logger.info("Retrieved {} similar memories (threshold: {}, maxAge: {} days)",
                similarMemories.size(), similarityThreshold, maxAgeDays);
            
            // Step 3: Build augmented context with memories
            // Create a new ReasoningContext with augmented prompt in perception
            ReasoningContext augmentedReasoningContext = createAugmentedContext(
                context,
                similarMemories
            );
            
            // Step 4: Generate reasoning with LLM
            logger.debug("Calling LLM with augmented context");
            Reasoning reasoning = llmProvider.generateReasoning(augmentedReasoningContext);
            
            logger.info("Generated reasoning with confidence: {}%", reasoning.getConfidence());
            
            // Step 5: Store this experience as a memory (async)
            // Note: We store it immediately with PENDING outcome
            // It will be updated later when the trade completes
            storeMemoryAsync(
                agent.getId().toString(),
                context,
                scenarioDescription,
                queryEmbedding,
                reasoning
            );
            
            return reasoning;
            
        } catch (Exception e) {
            logger.error("RAG pipeline failed, falling back to basic reasoning", e);
            // Fallback: generate reasoning without RAG
            return llmProvider.generateReasoning(context);
        }
    }
    
    /**
     * Create an augmented reasoning context with memories
     */
    private ReasoningContext createAugmentedContext(
            ReasoningContext baseContext,
            List<TradeMemory> memories) {
        
        // Build augmented perception with memory context
        String basePerceptionText = contextBuilder.buildScenarioDescription(
            baseContext.getPerception()
        );
        
        String augmentedText = contextBuilder.buildAugmentedContext(
            baseContext,
            memories
        );
        
        // Create new perception with augmented sentiment field
        Perception augmentedPerception = new Perception(
            baseContext.getPerception().getSymbol(),
            baseContext.getPerception().getCurrentPrice(),
            baseContext.getPerception().getTrend(),
            augmentedText,  // Store augmented context in sentiment field
            baseContext.getPerception().getVolume(),
            baseContext.getPerception().getTimestamp()
        );
        
        // Return new context with augmented perception
        return new ReasoningContext(
            baseContext.getGoal(),
            augmentedPerception,
            baseContext.getTradingSymbol(),
            baseContext.getCapital(),
            baseContext.getIterationCount()
        );
    }
    
    /**
     * Store a completed trade as a memory for future retrieval
     * 
     * @param agentId The agent who made the trade
     * @param symbol Trading symbol
     * @param scenarioDescription Description of market conditions
     * @param direction Trade direction (LONG/SHORT)
     * @param entryPrice Entry price
     * @param exitPrice Exit price (null if still open)
     * @param outcome Trade outcome
     * @param profitPercent Profit/loss percentage
     * @param lessonLearned What was learned from this trade
     */
    public void storeTradeMemory(
            String agentId,
            String symbol,
            String scenarioDescription,
            TradeDirection direction,
            double entryPrice,
            Double exitPrice,
            TradeOutcome outcome,
            Double profitPercent,
            String lessonLearned) {
        
        try {
            logger.info("Storing trade memory for agent {} on {}", agentId, symbol);
            
            // Generate embedding for the scenario
            double[] embedding = embeddingService.embed(scenarioDescription);
            
            // Build memory
            TradeMemory memory = TradeMemory.builder()
                .id(UUID.randomUUID().toString())
                .agentId(agentId)
                .symbol(symbol)
                .scenarioDescription(scenarioDescription)
                .direction(direction)
                .entryPrice(entryPrice)
                .exitPrice(exitPrice)
                .outcome(outcome)
                .profitPercent(profitPercent)
                .lessonLearned(lessonLearned)
                .timestamp(Instant.now())
                .embedding(embedding)
                .build();
            
            // Store in vector database
            memoryStore.store(memory);
            
            logger.info("Successfully stored trade memory {}", memory.getId());
            
        } catch (Exception e) {
            logger.error("Failed to store trade memory", e);
            // Don't throw - memory storage failure shouldn't break trading
        }
    }
    
    /**
     * Store a memory asynchronously (non-blocking)
     */
    private void storeMemoryAsync(
            String agentId,
            ReasoningContext context,
            String scenarioDescription,
            double[] embedding,
            Reasoning reasoning) {
        
        // Store immediately with PENDING status
        // This will be updated later when the trade executes/completes
        try {
            TradeMemory memory = TradeMemory.builder()
                .id(UUID.randomUUID().toString())
                .agentId(agentId)
                .symbol(context.getTradingSymbol())
                .scenarioDescription(scenarioDescription)
                .direction(extractDirection(reasoning))
                .entryPrice(context.getPerception().getCurrentPrice())
                .exitPrice(null)  // Will be updated later
                .outcome(TradeOutcome.PENDING)
                .profitPercent(null)  // Will be updated later
                .lessonLearned(reasoning.getAnalysis())  // Initial analysis
                .timestamp(Instant.now())
                .embedding(embedding)
                .build();
            
            memoryStore.store(memory);
            logger.debug("Stored pending memory {}", memory.getId());
            
        } catch (Exception e) {
            logger.warn("Failed to store pending memory", e);
        }
    }
    
    /**
     * Extract trade direction from reasoning
     * Defaults to LONG if unclear
     */
    private TradeDirection extractDirection(Reasoning reasoning) {
        String recommendation = reasoning.getRecommendation().toLowerCase();
        if (recommendation.contains("short") || recommendation.contains("sell")) {
            return TradeDirection.SHORT;
        }
        return TradeDirection.LONG;  // Default to LONG for buy/hold
    }
    
    /**
     * Check if RAG system is healthy
     */
    public boolean isHealthy() {
        return memoryStore.isHealthy();
    }
}
