package tradingbot.domain.market;

import java.math.BigDecimal;

/**
 * Typed payload for {@link StreamMarketDataEvent} events of type
 * {@link StreamMarketDataEvent.EventType#BOOK_TICKER}.
 *
 * <p>Both sides of the spread are preserved so that downstream consumers
 * (e.g. {@code OrderPlacementService}) can choose the correct fill price:
 * <ul>
 *   <li>{@link #ask()} — price a buyer pays (LONG entry / SHORT exit)</li>
 *   <li>{@link #bid()} — price a seller receives (SHORT entry / LONG exit)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * if (event.payload() instanceof BookTickerPayload btp) {
 *     double fillPrice = isBuy ? btp.ask().doubleValue() : btp.bid().doubleValue();
 * }
 * }</pre>
 */
public record BookTickerPayload(BigDecimal bid, BigDecimal ask) {

    /** Convenience: mid-price for signal/indicator use (not for fill simulation). */
    public BigDecimal mid() {
        return bid.add(ask).divide(java.math.BigDecimal.valueOf(2), ask.scale() + 1,
                java.math.RoundingMode.HALF_UP);
    }
}
