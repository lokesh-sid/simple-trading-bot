package tradingbot.agent.service;

import java.util.List;

import tradingbot.agent.domain.model.TradingMemory;

/**
 * MemoryStoreService - Vector database interface for storing and retrieving trading memories
 * 
 * This service manages the storage and semantic search of historical trading experiences
 * in a vector database (Pinecone, Weaviate, or Qdrant).
 */
public interface MemoryStoreService {
    
    /**
     * Store a trading memory in the vector database
     * 
     * @param memory The trading memory to store (must include embedding vector)
     */
    void store(TradingMemory memory);
    
    /**
     * Find similar trading memories for a given scenario
     * 
     * @param queryEmbedding The embedding vector of the current scenario
     * @param symbol The trading symbol to filter by (e.g., "BTCUSDT")
     * @param topK Number of most similar memories to retrieve
     * @return List of similar memories, sorted by similarity score (highest first)
     */
    List<TradingMemory> findSimilar(double[] queryEmbedding, String symbol, int topK);
    
    /**
     * Find similar trading memories with additional filters
     * 
     * @param queryEmbedding The embedding vector of the current scenario
     * @param symbol The trading symbol to filter by
     * @param topK Number of most similar memories to retrieve
     * @param minSimilarity Minimum similarity threshold (0-1)
     * @param maxAgeDays Only include memories from last N days (0 = no limit)
     * @return List of similar memories, sorted by similarity score (highest first)
     */
    List<TradingMemory> findSimilar(
        double[] queryEmbedding, 
        String symbol, 
        int topK,
        double minSimilarity,
        int maxAgeDays
    );
    
    /**
     * Delete a trading memory from the vector database
     * 
     * @param memoryId The ID of the memory to delete
     */
    void delete(String memoryId);
    
    /**
     * Check if the vector database is healthy and accessible
     * 
     * @return true if the database is accessible, false otherwise
     */
    boolean isHealthy();
}
