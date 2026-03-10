package tradingbot.bot.service;

import java.util.List;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SentimentService — Retrieves real-time social sentiment for a trading symbol
 * from the X (Twitter) API v2 recent-search endpoint.
 *
 * <p>Returns a sentiment score in the range [-1.0, 1.0]:
 * <ul>
 *   <li>+1.0 — entirely positive</li>
 *   <li> 0.0 — neutral</li>
 *   <li>-1.0 — entirely negative</li>
 * </ul>
 *
 * <p>Activation: only instantiated when {@code sentiment.enabled=true} is set.
 * Guard this flag in production config to avoid unintended API calls.
 */
@Service
@ConditionalOnProperty(name = "sentiment.enabled", havingValue = "true", matchIfMissing = false)
public class SentimentService {

    private static final Logger logger = Logger.getLogger(SentimentService.class.getName());

    private static final String X_RECENT_SEARCH_URL = "https://api.twitter.com/2/tweets/search/recent";
    private static final int MAX_RESULTS = 10;

    // Positive signal words (lower-case for comparison)
    private static final List<String> POSITIVE_WORDS = List.of(
            "bullish", "bull", "buy", "long", "moon", "pump", "surge", "rally",
            "breakout", "gain", "profit", "up", "rise", "growth", "strong",
            "positive", "good", "great", "excellent", "boom", "🚀", "📈"
    );

    // Negative signal words (lower-case for comparison)
    private static final List<String> NEGATIVE_WORDS = List.of(
            "bearish", "bear", "sell", "short", "dump", "crash", "drop", "fall",
            "breakdown", "loss", "down", "decline", "weak", "negative", "bad",
            "terrible", "bust", "rekt", "panic", "📉", "💀"
    );

    private final RestTemplate restTemplate;
    private final String bearerToken;
    private final boolean enabled;

    public SentimentService(
            RestTemplate restTemplate,
            @Value("${sentiment.x.bearer-token:}") String bearerToken,
            @Value("${sentiment.enabled:false}") boolean enabled) {
        this.restTemplate = restTemplate;
        this.bearerToken = bearerToken;
        this.enabled = enabled;
    }

    /**
     * Fetches recent tweets for the given symbol cashtag and returns a
     * sentiment score in [-1.0, 1.0].
     *
     * @param symbol trading symbol such as {@code BTC} or {@code BTCUSDT}
     * @return sentiment score; 0.0 returned on error or when disabled
     */
    public double getSentimentScore(String symbol) {
        if (!enabled) {
            logger.fine("Sentiment analysis is disabled; returning neutral score.");
            return 0.0;
        }
        if (bearerToken == null || bearerToken.isBlank()) {
            logger.warning("X API bearer token not configured (sentiment.x.bearer-token); returning neutral score.");
            return 0.0;
        }

        String cashtag = buildCashtag(symbol);
        try {
            String url = UriComponentsBuilder.fromHttpUrl(X_RECENT_SEARCH_URL)
                    .queryParam("query", cashtag + " -is:retweet lang:en")
                    .queryParam("tweet.fields", "text")
                    .queryParam("max_results", MAX_RESULTS)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + bearerToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<TweetsResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, TweetsResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<TweetData> tweets = response.getBody().getData();
                if (tweets == null || tweets.isEmpty()) {
                    logger.fine(() -> "No tweets found for cashtag: " + cashtag);
                    return 0.0;
                }
                double score = computeScore(tweets);
                logger.info(() -> "Sentiment score for %s: %.3f (from %d tweets)".formatted(cashtag, score, tweets.size()));
                return score;
            }
            logger.warning(() -> "X API returned non-success status: " + response.getStatusCode());
        } catch (Exception e) {
            logger.severe("Error fetching sentiment from X API: " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Convenience wrapper — returns {@code true} when the score is above +0.1.
     */
    public boolean isPositiveSentiment(String symbol) {
        return getSentimentScore(symbol) > 0.1;
    }

    /**
     * Convenience wrapper — returns {@code true} when the score is below -0.1.
     */
    public boolean isNegativeSentiment(String symbol) {
        return getSentimentScore(symbol) < -0.1;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a raw symbol (e.g. {@code BTCUSDT} or {@code BTC}) to a cashtag
     * understood by the X API (e.g. {@code $BTC}).
     */
    private String buildCashtag(String symbol) {
        // Strip quote currency suffixes like USDT / USDC / BUSD / USD / BTC
        String base = symbol.toUpperCase()
                .replaceAll("(USDT|USDC|BUSD|TUSD|USD|BTC|ETH)$", "");
        if (base.isBlank()) {
            base = symbol.toUpperCase();
        }
        return "$" + base;
    }

    /**
     * Scores each tweet text and returns the mean score across all tweets.
     * Each tweet score is: (positiveHits - negativeHits) / max(totalHits, 1),
     * clamped to [-1.0, 1.0].
     */
    private double computeScore(List<TweetData> tweets) {
        double total = 0.0;
        for (TweetData tweet : tweets) {
            total += scoreTweet(tweet.getText());
        }
        return Math.clamp(total / tweets.size(), -1.0, 1.0);
    }

    private double scoreTweet(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        String lower = text.toLowerCase();
        long positive = POSITIVE_WORDS.stream().filter(lower::contains).count();
        long negative = NEGATIVE_WORDS.stream().filter(lower::contains).count();
        long total = positive + negative;
        if (total == 0) {
            return 0.0;
        }
        return (double) (positive - negative) / total;
    }

    // -------------------------------------------------------------------------
    // Response DTOs
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TweetsResponse {
        @JsonProperty("data")
        private List<TweetData> data;

        public List<TweetData> getData() {
            return data;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TweetData {
        @JsonProperty("id")
        private String id;

        @JsonProperty("text")
        private String text;

        public String getId() {
            return id;
        }

        public String getText() {
            return text;
        }
    }
}
