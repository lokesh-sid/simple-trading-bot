package tradingbot.agent.service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import tradingbot.agent.domain.model.TradingMemory;

/**
 * CachedMemoryStore - Redis cache wrapper for MemoryStoreService
 * 
 * Provides fast access to recently retrieved memories while delegating
 * storage and long-term retrieval to the underlying vector database.
 * 
 * Cache Strategy:
 * - Store: Write-through (cache + vector DB)
 * - Retrieve: Check cache first, fallback to vector DB
 * - TTL: 1 hour for query results
 */
@Service
public class CachedMemoryStore implements MemoryStoreService {
    
    private static final Logger logger = LoggerFactory.getLogger(CachedMemoryStore.class);
    private static final String CACHE_KEY_PREFIX = "memory:";
    private static final String QUERY_CACHE_PREFIX = "query:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    
    private final MemoryStoreService delegate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final EmbeddingService embeddingService;
    
    public CachedMemoryStore(
            PineconeMemoryStore delegate,
            RedisTemplate<String, Object> redisTemplate,
            EmbeddingService embeddingService) {
        this.delegate = delegate;
        this.redisTemplate = redisTemplate;
        this.embeddingService = embeddingService;
    }
    
    @Override
    public void store(TradingMemory memory) {
        // Write-through: store in both cache and vector DB
        try {
            String cacheKey = CACHE_KEY_PREFIX + memory.getId();
            redisTemplate.opsForValue().set(cacheKey, memory, CACHE_TTL);
            logger.debug("Cached memory {}", memory.getId());
        } catch (Exception e) {
            logger.warn("Failed to cache memory, continuing with vector DB store", e);
        }
        
        // Always write to vector DB
        delegate.store(memory);
    }
    
    @Override
    public List<TradingMemory> findSimilar(double[] queryEmbedding, String symbol, int topK) {
        return findSimilar(queryEmbedding, symbol, topK, 0.0, 0);
    }
    
    @Override
    public List<TradingMemory> findSimilar(
            double[] queryEmbedding,
            String symbol,
            int topK,
            double minSimilarity,
            int maxAgeDays) {
        
        // Build cache key from query parameters
        String queryCacheKey = buildQueryCacheKey(symbol, topK, minSimilarity, maxAgeDays);
        
        try {
            // Check cache first
            @SuppressWarnings("unchecked")
            List<TradingMemory> cached = (List<TradingMemory>) redisTemplate.opsForValue().get(queryCacheKey);
            
            if (cached != null && !cached.isEmpty()) {
                logger.debug("Cache hit for query: {}", queryCacheKey);
                
                // Recalculate similarity scores with current query
                cached.forEach(memory -> {
                    double similarity = embeddingService.cosineSimilarity(
                        queryEmbedding,
                        memory.getEmbedding()
                    );
                    memory.setSimilarityScore(similarity);
                });
                
                return cached;
            }
            
            logger.debug("Cache miss for query: {}", queryCacheKey);
        } catch (Exception e) {
            logger.warn("Cache lookup failed, falling back to vector DB", e);
        }
        
        // Cache miss or error - query vector DB
        List<TradingMemory> results = delegate.findSimilar(
            queryEmbedding,
            symbol,
            topK,
            minSimilarity,
            maxAgeDays
        );
        
        // Cache the results
        try {
            redisTemplate.opsForValue().set(queryCacheKey, results, CACHE_TTL);
            logger.debug("Cached query results: {}", queryCacheKey);
        } catch (Exception e) {
            logger.warn("Failed to cache query results", e);
        }
        
        return results;
    }
    
    @Override
    public void delete(String memoryId) {
        // Delete from cache
        try {
            String cacheKey = CACHE_KEY_PREFIX + memoryId;
            redisTemplate.delete(cacheKey);
            logger.debug("Deleted memory {} from cache", memoryId);
            
            // Invalidate related query caches
            invalidateQueryCaches();
        } catch (Exception e) {
            logger.warn("Failed to delete from cache, continuing with vector DB delete", e);
        }
        
        // Delete from vector DB
        delegate.delete(memoryId);
    }
    
    @Override
    public boolean isHealthy() {
        // Check both cache and vector DB health
        boolean cacheHealthy = isRedisHealthy();
        boolean vectorDbHealthy = delegate.isHealthy();
        
        logger.debug("Health check - Redis: {}, VectorDB: {}", cacheHealthy, vectorDbHealthy);
        
        // Vector DB is critical, cache is optional
        return vectorDbHealthy;
    }
    
    private boolean isRedisHealthy() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            logger.warn("Redis health check failed", e);
            return false;
        }
    }
    
    private String buildQueryCacheKey(String symbol, int topK, double minSimilarity, int maxAgeDays) {
        return String.format("%s%s:k%d:sim%.2f:age%d",
            QUERY_CACHE_PREFIX,
            symbol,
            topK,
            minSimilarity,
            maxAgeDays
        );
    }
    
    private void invalidateQueryCaches() {
        try {
            Set<String> keys = redisTemplate.keys(QUERY_CACHE_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.debug("Invalidated {} query cache entries", keys.size());
            }
        } catch (Exception e) {
            logger.warn("Failed to invalidate query caches", e);
        }
    }
}
