package tradingbot.domain.market;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Reliable, immutable market data event for the internal reactive stream.
 * Renamed to StreamMarketDataEvent to avoid conflict with legacy bot events.
 */
public record StreamMarketDataEvent(
    String exchange,
    String symbol,
    EventType type,
    BigDecimal price,
    BigDecimal quantity,
    Instant timestamp,
    Object payload // Optional raw payload or specialized data (e.g. OrderBook)
) {
    public enum EventType {
        TRADE,
        BOOK_TICKER,
        KLINE,
        ORDER_BOOK
    }
}
