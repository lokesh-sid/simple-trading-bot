package tradingbot.agent.infrastructure.llm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.ReasoningContext;

/**
 * PaperLLMProvider — a Redis-backed caching decorator around {@link GrokClient}.
 *
 * <p>Designed for backtest replay scenarios where real LLM API calls are undesirable
 * (cost, latency, non-determinism).  When a prompt has been seen before the cached
 * {@link Reasoning} is returned immediately; on a cache miss the delegate provider
 * is called and the result stored with a configurable TTL (default 24 hours).
 *
 * <h3>Difference from {@link CachedGrokService}</h3>
 * {@link CachedGrokService} keys the cache on individual market-context fields (symbol,
 * price, trend, …).  {@code PaperLLMProvider} keys on the <em>full rendered prompt
 * string</em> produced by {@link PromptTemplates#buildReasoningPrompt}, so it is
 * sensitive to any prompt change — making it useful for prompt-engineering iterations
 * during backtest.
 *
 * <h3>Activation</h3>
 * Enabled when {@code paper.llm.enabled=true} (set automatically in
 * {@code application-backtest.properties}).  Annotated {@link Primary} so it takes
 * precedence over other {@link LLMProvider} beans when active.
 *
 * <h3>Offline mode</h3>
 * When no {@link GrokClient} bean is present (e.g. {@code agent.llm.grok.enabled=false})
 * the provider falls back to deterministic synthetic reasoning rather than calling
 * a real API.
 */
@Primary
@Component
@ConditionalOnProperty(name = "paper.llm.enabled", havingValue = "true", matchIfMissing = false)
public class PaperLLMProvider implements LLMProvider {

    private static final Logger logger = LoggerFactory.getLogger(PaperLLMProvider.class);
    static final String REDIS_KEY_PREFIX = "paper:llm:";
    static final long DEFAULT_TTL_HOURS = 24L;

    private final GrokClient delegate;   // nullable — absent when Grok is disabled
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlHours;

    public PaperLLMProvider(
            Optional<GrokClient> delegate,
            RedisTemplate<String, String> redisTemplate,
            @Value("${paper.llm.cache.ttl-hours:" + DEFAULT_TTL_HOURS + "}") long ttlHours) {
        this.delegate = delegate.orElse(null);
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.ttlHours = ttlHours;
    }

    @Override
    public Reasoning generateReasoning(ReasoningContext context) {
        String prompt = PromptTemplates.buildReasoningPrompt(context);
        String cacheKey = buildCacheKey(prompt);

        // --- Cache HIT ---
        Reasoning cached = readFromRedis(cacheKey);
        if (cached != null) {
            logger.debug("[PaperLLM Cache HIT] key={}, symbol={}", cacheKey, context.getTradingSymbol());
            return cached;
        }

        // --- Cache MISS: delegate or synthesize, then store ---
        Reasoning reasoning;
        if (delegate != null && delegate.isEnabled()) {
            logger.info("[PaperLLM Cache MISS] Calling Grok delegate. key={}, symbol={}, price={}",
                    cacheKey, context.getTradingSymbol(), context.getPerception().getCurrentPrice());
            reasoning = delegate.generateReasoning(context);
        } else {
            logger.info("[PaperLLM Cache MISS - Offline] Generating synthetic reasoning. key={}, symbol={}, price={}",
                    cacheKey, context.getTradingSymbol(), context.getPerception().getCurrentPrice());
            reasoning = syntheticReasoning(context);
        }

        writeToRedis(cacheKey, reasoning);
        return reasoning;
    }

    @Override
    public boolean isEnabled() {
        return delegate == null || delegate.isEnabled();
    }

    @Override
    public String getProviderName() {
        return "PaperLLM (wraps: " + (delegate != null ? delegate.getProviderName() : "synthetic-offline") + ")";
    }

    // -------------------------------------------------------------------------
    // Synthetic reasoning (offline fallback)
    // -------------------------------------------------------------------------

    /**
     * Returns a deterministic {@link Reasoning} based solely on the current trend
     * when no real LLM delegate is configured.  Results are cached in Redis so
     * identical market contexts are always served from cache on subsequent calls.
     */
    private Reasoning syntheticReasoning(ReasoningContext context) {
        String trend = context.getPerception().getTrend();
        String symbol = context.getTradingSymbol();
        double price = context.getPerception().getCurrentPrice();

        String recommendation;
        int confidence;
        String analysis;

        if ("UPTREND".equalsIgnoreCase(trend) || "BULLISH".equalsIgnoreCase(trend)) {
            recommendation = "BUY";
            confidence = 75;
            analysis = "Synthetic analysis: %s showing %s at %.2f. Positive momentum detected.".formatted(symbol, trend, price);
        } else if ("DOWNTREND".equalsIgnoreCase(trend) || "BEARISH".equalsIgnoreCase(trend)) {
            recommendation = "SELL";
            confidence = 70;
            analysis = "Synthetic analysis: %s showing %s at %.2f. Negative momentum detected.".formatted(symbol, trend, price);
        } else {
            recommendation = "HOLD";
            confidence = 60;
            analysis = "Synthetic analysis: %s showing neutral/sideways at %.2f. No clear signal.".formatted(symbol, price);
        }

        return new Reasoning(
                "[Synthetic] Price=%.2f, Trend=%s, Sentiment=%s".formatted(
                        price, trend, context.getPerception().getSentiment()),
                analysis,
                "Risk: standard backtest risk — no real capital at stake.",
                recommendation,
                confidence,
                Instant.now()
        );
    }

    // -------------------------------------------------------------------------
    // Cache key
    // -------------------------------------------------------------------------

    /**
     * Builds a deterministic SHA-256 cache key from the fully rendered prompt.
     * Same prompt text always produces the same key.
     */
    String buildCacheKey(String prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(prompt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present in every JVM
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // -------------------------------------------------------------------------
    // Redis helpers
    // -------------------------------------------------------------------------

    private Reasoning readFromRedis(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + cacheKey);
            if (json != null) {
                return fromDto(objectMapper.readValue(json, CachedReasoningDto.class));
            }
        } catch (Exception e) {
            logger.warn("[PaperLLM] Redis read failed (cache miss treated): {}", e.getMessage());
        }
        return null;
    }

    private void writeToRedis(String cacheKey, Reasoning reasoning) {
        try {
            String json = objectMapper.writeValueAsString(toDto(reasoning));
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + cacheKey, json, ttlHours, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.warn("[PaperLLM] Redis write failed (non-fatal): {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // DTO (Reasoning lacks a no-arg constructor required by Jackson)
    // -------------------------------------------------------------------------

    private CachedReasoningDto toDto(Reasoning r) {
        return new CachedReasoningDto(
                r.getObservation(),
                r.getAnalysis(),
                r.getRiskAssessment(),
                r.getRecommendation(),
                r.getConfidence(),
                r.getTimestamp()
        );
    }

    private Reasoning fromDto(CachedReasoningDto dto) {
        return new Reasoning(
                dto.observation(),
                dto.analysis(),
                dto.riskAssessment(),
                dto.recommendation(),
                dto.confidence(),
                dto.timestamp()
        );
    }

    /** Internal DTO for JSON serialisation of cached {@link Reasoning} objects. */
    record CachedReasoningDto(
            @JsonProperty("observation") String observation,
            @JsonProperty("analysis") String analysis,
            @JsonProperty("riskAssessment") String riskAssessment,
            @JsonProperty("recommendation") String recommendation,
            @JsonProperty("confidence") int confidence,
            @JsonProperty("timestamp") Instant timestamp
    ) {
        @JsonCreator
        CachedReasoningDto {}
    }
}

