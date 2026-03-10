package tradingbot.agent.infrastructure.llm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import tradingbot.agent.domain.model.AgentGoal;
import tradingbot.agent.domain.model.Perception;
import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.ReasoningContext;

/**
 * Unit tests for {@link PaperLLMProvider}.
 *
 * Verifies Redis cache hit/miss behaviour and synthetic reasoning fallback
 * using a mock {@link RedisTemplate} — no real Redis instance required.
 */
@ExtendWith(MockitoExtension.class)
class PaperLLMProviderTest {

    @Mock
    private GrokClient mockGrokClient;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private PaperLLMProvider provider;
    private ReasoningContext testContext;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        provider = new PaperLLMProvider(
                Optional.of(mockGrokClient),
                redisTemplate,
                PaperLLMProvider.DEFAULT_TTL_HOURS
        );

        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Maximize profits");
        Perception perception = new Perception("BTCUSDT", 45_000.0, "UPTREND", "BULLISH", 1_000_000.0, Instant.now());
        testContext = new ReasoningContext(goal, perception, "BTCUSDT", 10_000.0, 1);
    }

    // -------------------------------------------------------------------------
    // Cache HIT
    // -------------------------------------------------------------------------

    @Test
    void cacheHit_returnsCachedReasoningWithoutCallingDelegate() throws Exception {
        // Given — Redis returns a serialised Reasoning
        String cachedJson = """
                {
                  "observation": "Cached observation",
                  "analysis": "Cached analysis",
                  "riskAssessment": "Low risk",
                  "recommendation": "BUY",
                  "confidence": 80,
                  "timestamp": "2024-01-01T00:00:00Z"
                }
                """;
        String expectedKey = PaperLLMProvider.REDIS_KEY_PREFIX + provider.buildCacheKey(
                PromptTemplates.buildReasoningPrompt(testContext));
        when(valueOps.get(expectedKey)).thenReturn(cachedJson);

        // When
        Reasoning result = provider.generateReasoning(testContext);

        // Then — cached value is returned
        assertNotNull(result);
        assertEquals("Cached observation", result.getObservation());
        assertEquals("Cached analysis", result.getAnalysis());
        assertEquals("BUY", result.getRecommendation());
        assertEquals(80, result.getConfidence());

        // Delegate must NOT be called on a cache hit
        verifyNoInteractions(mockGrokClient);
    }

    // -------------------------------------------------------------------------
    // Cache MISS — delegate enabled
    // -------------------------------------------------------------------------

    @Test
    void cacheMiss_callsDelegateAndStoresResultInRedis() {
        // Given — Redis returns null (cache miss)
        when(valueOps.get(anyString())).thenReturn(null);

        Reasoning delegateReasoning = new Reasoning(
                "Live observation", "Live analysis", "Medium risk", "HOLD", 65, Instant.now());
        when(mockGrokClient.isEnabled()).thenReturn(true);
        when(mockGrokClient.generateReasoning(testContext)).thenReturn(delegateReasoning);

        // When
        Reasoning result = provider.generateReasoning(testContext);

        // Then — delegate result is returned
        assertNotNull(result);
        assertEquals("Live observation", result.getObservation());
        assertEquals("HOLD", result.getRecommendation());
        assertEquals(65, result.getConfidence());

        // Result must be stored in Redis
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> unitCaptor = ArgumentCaptor.forClass(TimeUnit.class);
        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(),
                ttlCaptor.capture(), unitCaptor.capture());

        assertTrue(keyCaptor.getValue().startsWith(PaperLLMProvider.REDIS_KEY_PREFIX));
        assertNotNull(valueCaptor.getValue());
        assertEquals(PaperLLMProvider.DEFAULT_TTL_HOURS, ttlCaptor.getValue());
        assertEquals(TimeUnit.HOURS, unitCaptor.getValue());
    }

    // -------------------------------------------------------------------------
    // Cache MISS — offline (no delegate)
    // -------------------------------------------------------------------------

    @Test
    void cacheMiss_noDelegate_returnsSyntheticReasoningAndStoresInRedis() {
        // Given — no GrokClient available
        PaperLLMProvider offlineProvider = new PaperLLMProvider(
                Optional.empty(), redisTemplate, PaperLLMProvider.DEFAULT_TTL_HOURS);
        when(valueOps.get(anyString())).thenReturn(null);

        // When
        Reasoning result = offlineProvider.generateReasoning(testContext);

        // Then — synthetic reasoning is returned (not null, valid recommendation)
        assertNotNull(result);
        assertNotNull(result.getRecommendation());
        assertTrue(result.getConfidence() > 0);
        // UPTREND → BUY
        assertEquals("BUY", result.getRecommendation());

        // Still stored in Redis
        verify(valueOps).set(anyString(), anyString(), anyLong(), eq(TimeUnit.HOURS));
    }

    @Test
    void cacheMiss_bearishTrend_syntheticReasoningSuggestsSell() {
        // Given — bearish market context
        PaperLLMProvider offlineProvider = new PaperLLMProvider(
                Optional.empty(), redisTemplate, PaperLLMProvider.DEFAULT_TTL_HOURS);
        when(valueOps.get(anyString())).thenReturn(null);

        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Maximize profits");
        Perception bearishPerception = new Perception("ETHUSDT", 2_000.0, "DOWNTREND", "BEARISH", 500_000.0, Instant.now());
        ReasoningContext bearishContext = new ReasoningContext(goal, bearishPerception, "ETHUSDT", 5_000.0, 1);

        // When
        Reasoning result = offlineProvider.generateReasoning(bearishContext);

        // Then — DOWNTREND → SELL
        assertEquals("SELL", result.getRecommendation());
    }

    @Test
    void cacheMiss_neutralTrend_syntheticReasoningSuggestsHold() {
        // Given — neutral/sideways market context
        PaperLLMProvider offlineProvider = new PaperLLMProvider(
                Optional.empty(), redisTemplate, PaperLLMProvider.DEFAULT_TTL_HOURS);
        when(valueOps.get(anyString())).thenReturn(null);

        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Maximize profits");
        Perception neutralPerception = new Perception("SOLUSDT", 150.0, "SIDEWAYS", "NEUTRAL", 200_000.0, Instant.now());
        ReasoningContext neutralContext = new ReasoningContext(goal, neutralPerception, "SOLUSDT", 3_000.0, 1);

        // When
        Reasoning result = offlineProvider.generateReasoning(neutralContext);

        // Then — SIDEWAYS → HOLD
        assertEquals("HOLD", result.getRecommendation());
    }

    // -------------------------------------------------------------------------
    // Idempotency
    // -------------------------------------------------------------------------

    @Test
    void sameContextProducesSameCacheKey() {
        // Given — two calls with the same context
        String prompt = PromptTemplates.buildReasoningPrompt(testContext);
        String key1 = provider.buildCacheKey(prompt);
        String key2 = provider.buildCacheKey(prompt);

        // Then — same key
        assertEquals(key1, key2);
    }

    @Test
    void differentPromptsProduceDifferentCacheKeys() {
        // Given — two different prompts
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Maximize profits");
        Perception otherPerception = new Perception("SOLUSDT", 150.0, "SIDEWAYS", "NEUTRAL", 200_000.0, Instant.now());
        ReasoningContext otherContext = new ReasoningContext(goal, otherPerception, "SOLUSDT", 5_000.0, 2);

        String key1 = provider.buildCacheKey(PromptTemplates.buildReasoningPrompt(testContext));
        String key2 = provider.buildCacheKey(PromptTemplates.buildReasoningPrompt(otherContext));

        // Then — different keys
        assertNotEquals(key1, key2);
    }

    // -------------------------------------------------------------------------
    // Redis failure resilience
    // -------------------------------------------------------------------------

    @Test
    void redisReadFailure_treatedAsCacheMiss() {
        // Given — Redis throws on read
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis unavailable"));
        when(mockGrokClient.isEnabled()).thenReturn(true);
        Reasoning fallback = new Reasoning("obs", "analysis", "risk", "HOLD", 50, Instant.now());
        when(mockGrokClient.generateReasoning(testContext)).thenReturn(fallback);

        // When — must not throw
        Reasoning result = assertDoesNotThrow(() -> provider.generateReasoning(testContext));

        // Then — delegate is called as fallback
        assertNotNull(result);
        assertEquals("HOLD", result.getRecommendation());
    }

    @Test
    void redisWriteFailure_doesNotPropagateException() {
        // Given — Redis read returns null (miss) and write throws
        when(valueOps.get(anyString())).thenReturn(null);
        doThrow(new RuntimeException("Redis write error"))
                .when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        when(mockGrokClient.isEnabled()).thenReturn(true);
        Reasoning delegateReasoning = new Reasoning("obs", "analysis", "risk", "BUY", 70, Instant.now());
        when(mockGrokClient.generateReasoning(testContext)).thenReturn(delegateReasoning);

        // When — must not throw despite Redis write failure
        Reasoning result = assertDoesNotThrow(() -> provider.generateReasoning(testContext));

        // Then — delegate result is still returned
        assertNotNull(result);
        assertEquals("BUY", result.getRecommendation());
    }

    // -------------------------------------------------------------------------
    // Provider metadata
    // -------------------------------------------------------------------------

    @Test
    void getProviderName_includesDelegateNameWhenPresent() {
        when(mockGrokClient.getProviderName()).thenReturn("Grok (X.AI)");
        assertTrue(provider.getProviderName().contains("PaperLLM"));
        assertTrue(provider.getProviderName().contains("Grok (X.AI)"));
    }

    @Test
    void getProviderName_indicatesOfflineWhenNoDelegatePresent() {
        PaperLLMProvider offlineProvider = new PaperLLMProvider(
                Optional.empty(), redisTemplate, PaperLLMProvider.DEFAULT_TTL_HOURS);
        assertTrue(offlineProvider.getProviderName().contains("synthetic-offline"));
    }

    @Test
    void isEnabled_returnsTrueWhenNoDelegatePresent() {
        PaperLLMProvider offlineProvider = new PaperLLMProvider(
                Optional.empty(), redisTemplate, PaperLLMProvider.DEFAULT_TTL_HOURS);
        assertTrue(offlineProvider.isEnabled());
    }
}
