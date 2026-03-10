package tradingbot.bot.strategy.analyzer;

import java.util.logging.Logger;

import tradingbot.bot.service.SentimentService;

/**
 * SentimentAnalyzer — facade over {@link SentimentService} used by the trading bot.
 *
 * <p>When a real {@link SentimentService} is injected (i.e. {@code sentiment.enabled=true}),
 * calls are delegated to it and its score is used.  When the service is {@code null}
 * (sentiment disabled), both methods return {@code false} (neutral/unknown).
 */
public class SentimentAnalyzer {

    private static final Logger LOGGER = Logger.getLogger(SentimentAnalyzer.class.getName());

    private final SentimentService sentimentService;

    /**
     * Constructs an analyzer backed by a real {@link SentimentService}.
     *
     * @param sentimentService the service to delegate to; may be {@code null} when
     *                         sentiment analysis is disabled
     */
    public SentimentAnalyzer(SentimentService sentimentService) {
        this.sentimentService = sentimentService;
    }

    /**
     * Returns {@code true} when the X/Twitter sentiment score for the given symbol
     * is positive (score &gt; 0.1).
     */
    public boolean isPositiveSentiment(String symbol) {
        if (sentimentService == null) {
            LOGGER.fine(() -> "Sentiment service unavailable; returning false for symbol: " + symbol);
            return false;
        }
        try {
            LOGGER.info(() -> "Fetching positive sentiment for %s from X posts".formatted(symbol));
            return sentimentService.isPositiveSentiment(symbol);
        } catch (Exception e) {
            LOGGER.severe("Failed to fetch sentiment: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns {@code true} when the X/Twitter sentiment score for the given symbol
     * is negative (score &lt; -0.1).
     */
    public boolean isNegativeSentiment(String symbol) {
        if (sentimentService == null) {
            LOGGER.fine(() -> "Sentiment service unavailable; returning false for symbol: " + symbol);
            return false;
        }
        try {
            LOGGER.info(() -> "Fetching negative sentiment for %s from X posts".formatted(symbol));
            return sentimentService.isNegativeSentiment(symbol);
        } catch (Exception e) {
            LOGGER.severe("Failed to fetch sentiment: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns the raw sentiment score in [-1.0, 1.0] for the given symbol,
     * or {@code 0.0} when sentiment analysis is disabled.
     */
    public double getSentimentScore(String symbol) {
        if (sentimentService == null) {
            return 0.0;
        }
        try {
            return sentimentService.getSentimentScore(symbol);
        } catch (Exception e) {
            LOGGER.severe("Failed to fetch sentiment score: " + e.getMessage());
            return 0.0;
        }
    }

    // ...existing code...
}