package tradingbot.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.service.OrderResult.OrderStatus;
import tradingbot.exchange.ccxt.CcxtApiClient;
import tradingbot.exchange.ccxt.dto.CcxtOrder;
import tradingbot.exchange.ccxt.dto.CcxtTicker;

@ExtendWith(MockitoExtension.class)
class CcxtFuturesServiceTest {

    @Mock
    private CcxtApiClient apiClient;
    
    @Mock
    private EventPublisher eventPublisher;

    private CcxtFuturesService service;
    private final String exchangeId = "binance";
    private final String quoteCurrency = "USDT";
    private final String symbol = "BTC/USDT";

    @BeforeEach
    void setUp() {
        service = new CcxtFuturesService(apiClient, exchangeId, quoteCurrency, eventPublisher);
    }

    @Test
    void getCurrentPrice_ShouldReturnLastPrice() {
        // Arrange
        CcxtTicker ticker = new CcxtTicker(
            "BTC/USDT", 
            System.currentTimeMillis(),
            "2023-01-01T00:00:00Z",
            new BigDecimal("50100"), // high
            new BigDecimal("49900"), // low
            new BigDecimal("50040"), // bid
            new BigDecimal("50060"), // ask
            new BigDecimal("50050"), // last
            new BigDecimal("50000"), // open
            new BigDecimal("50050"), // close
            new BigDecimal("1.2"),   // percentage
            new BigDecimal("50"),    // change
            new BigDecimal("100"),   // baseVolume
            new BigDecimal("5005000") // quoteVolume
        );
        when(apiClient.fetchTicker(exchangeId, symbol)).thenReturn(ticker);

        // Act
        double price = service.getCurrentPrice(symbol);

        // Assert
        assertEquals(50050.0, price);
        verify(apiClient).fetchTicker(exchangeId, symbol);
    }

    @Test
    void get24HourStats_ShouldReturnStats() {
        // Arrange
        CcxtTicker ticker = new CcxtTicker(
            "BTC/USDT", 
            System.currentTimeMillis(),
            "2023-01-01T00:00:00Z",
            new BigDecimal("50100"), // high
            new BigDecimal("49900"), // low
            new BigDecimal("50040"), // bid
            new BigDecimal("50060"), // ask
            new BigDecimal("50050"), // last
            new BigDecimal("50000"), // open
            new BigDecimal("50050"), // close
            new BigDecimal("1.2"),   // percentage
            new BigDecimal("50"),    // change
            new BigDecimal("100"),   // baseVolume
            new BigDecimal("5005000") // quoteVolume
        );
        when(apiClient.fetchTicker(exchangeId, symbol)).thenReturn(ticker);

        // Act
        Ticker24hrStats stats = service.get24HourStats(symbol);

        // Assert
        assertNotNull(stats);
        assertEquals(symbol, stats.getSymbol());
        assertEquals(50050.0, stats.getLastPrice());
        assertEquals(50100.0, stats.getHighPrice());
        assertEquals(49900.0, stats.getLowPrice());
        assertEquals(100.0, stats.getVolume());
        assertEquals(5005000.0, stats.getQuoteVolume());
        assertEquals(1.2, stats.getPriceChangePercent());
    }

    @Test
    void enterLongPosition_ShouldCreateBuyOrder() {
        // Arrange
        double amount = 0.1;
        BigDecimal amountBd = BigDecimal.valueOf(amount);
        
        CcxtOrder order = new CcxtOrder(
            "123456",                // id
            "BTC/USDT",              // symbol
            "market",                // type
            "buy",                   // side
            new BigDecimal("50000"), // price
            amountBd,                // amount (ordered)
            new BigDecimal("5000"),  // cost
            amountBd,                // filled
            BigDecimal.ZERO,         // remaining
            "closed",                // status
            System.currentTimeMillis() // timestamp
        );
        
        when(apiClient.createOrder(eq(exchangeId), eq(symbol), eq("market"), eq("buy"), eq(amountBd), eq(null), any())).thenReturn(order);

        // Act
        OrderResult result = service.enterLongPosition(symbol, amount);

        // Assert
        assertNotNull(result);
        assertEquals("123456", result.getExchangeOrderId());
        assertEquals(symbol, result.getSymbol());
        assertEquals("BUY", result.getSide());
        assertEquals(OrderStatus.FILLED, result.getStatus());
        assertEquals(0.1, result.getFilledQuantity());
        verify(apiClient).createOrder(eq(exchangeId), eq(symbol), eq("market"), eq("buy"), eq(amountBd), eq(null), any());
        verify(eventPublisher, times(1)).publishTradeExecution(any(TradeExecutionEvent.class));
    }

    @Test
    void getMarginBalance_ShouldReturnCorrectBalance() {
        // Arrange
        Map<String, Object> balance = Map.of(
            "total", Map.of("USDT", 1000.50)
        );
        when(apiClient.fetchBalance(exchangeId)).thenReturn(balance);

        // Act
        double marginBalance = service.getMarginBalance();

        // Assert
        assertEquals(1000.50, marginBalance);
        verify(apiClient).fetchBalance(exchangeId);
    }
}
