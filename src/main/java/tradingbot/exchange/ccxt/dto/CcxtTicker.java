package tradingbot.exchange.ccxt.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CcxtTicker(
    String symbol,
    Long timestamp,
    String datetime,
    BigDecimal high,
    BigDecimal low,
    BigDecimal bid,
    BigDecimal ask,
    BigDecimal last,
    BigDecimal open,
    BigDecimal close,
    BigDecimal percentage,
    BigDecimal change,
    BigDecimal baseVolume,
    BigDecimal quoteVolume
) {}
