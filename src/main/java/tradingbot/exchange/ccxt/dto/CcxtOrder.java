package tradingbot.exchange.ccxt.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CcxtOrder(
    String id,
    String symbol,
    String type,
    String side,
    BigDecimal price,
    BigDecimal amount,
    BigDecimal cost,
    BigDecimal filled,
    BigDecimal remaining,
    String status,
    Long timestamp
) {}
