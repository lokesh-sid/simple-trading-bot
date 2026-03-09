package tradingbot.infrastructure.marketdata.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.binance.connector.futures.client.impl.UMWebsocketClientImpl;
import com.binance.connector.futures.client.utils.WebSocketCallback;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tradingbot.domain.market.BookTickerPayload;
import tradingbot.domain.market.StreamMarketDataEvent;
import tradingbot.domain.market.StreamMarketDataEvent.EventType;

@ExtendWith(MockitoExtension.class)
class BinanceWebSocketAdapterTest {

    @Mock
    private UMWebsocketClientImpl wsClient;

    private BinanceWebSocketAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new BinanceWebSocketAdapter();
        // Inject mock so no real WebSocket connection is opened
        ReflectionTestUtils.setField(adapter, "wsClient", wsClient);
    }

    // -------------------------------------------------------------------------
    // streamTrades
    // -------------------------------------------------------------------------

    @Test
    void streamTrades_subscribesToAggTradeStreamWithLowercaseSymbol() {
        when(wsClient.aggTradeStream(eq("btcusdt"), any(WebSocketCallback.class))).thenReturn(1);

        adapter.streamTrades("BTCUSDT").subscribe();

        verify(wsClient, times(1)).aggTradeStream(eq("btcusdt"), any(WebSocketCallback.class));
    }

    @Test
    void streamTrades_parsesValidAggTradeEvent() {
        ArgumentCaptor<WebSocketCallback> callbackCaptor =
                ArgumentCaptor.forClass(WebSocketCallback.class);
        when(wsClient.aggTradeStream(eq("btcusdt"), callbackCaptor.capture())).thenReturn(1);

        Flux<StreamMarketDataEvent> flux = adapter.streamTrades("BTCUSDT");

        StepVerifier.create(flux.take(1))
                .then(() -> callbackCaptor.getValue().onReceive(
                        "{\"e\":\"aggTrade\",\"p\":\"50000.00\",\"q\":\"0.01\",\"T\":1699000000000}"))
                .assertNext(event -> {
                    assertEquals("BINANCE_FUTURES", event.exchange());
                    assertEquals("BTCUSDT", event.symbol());
                    assertEquals(EventType.TRADE, event.type());
                    assertEquals(new BigDecimal("50000.00"), event.price());
                    assertEquals(new BigDecimal("0.01"), event.quantity());
                    assertNotNull(event.timestamp());
                })
                .verifyComplete();
    }

    @Test
    void streamTrades_skipsEventsMissingPriceOrQuantity_andKeepsSinkAlive() {
        ArgumentCaptor<WebSocketCallback> callbackCaptor =
                ArgumentCaptor.forClass(WebSocketCallback.class);
        when(wsClient.aggTradeStream(eq("btcusdt"), callbackCaptor.capture())).thenReturn(1);

        Flux<StreamMarketDataEvent> flux = adapter.streamTrades("BTCUSDT");

        StepVerifier.create(flux.take(1))
                .then(() -> {
                    // Incomplete event first — no price/qty fields, should be silently skipped
                    callbackCaptor.getValue().onReceive("{\"e\":\"aggTrade\",\"T\":1699000000000}");
                    // Valid event follows — sink must still be alive and process it
                    callbackCaptor.getValue().onReceive(
                            "{\"e\":\"aggTrade\",\"p\":\"50000.00\",\"q\":\"0.01\",\"T\":1699000000000}");
                })
                .assertNext(event -> assertEquals(new BigDecimal("50000.00"), event.price()))
                .verifyComplete();
    }

    @Test
    void streamTrades_handlesMalformedJson_andKeepsSinkAlive() {
        ArgumentCaptor<WebSocketCallback> callbackCaptor =
                ArgumentCaptor.forClass(WebSocketCallback.class);
        when(wsClient.aggTradeStream(eq("btcusdt"), callbackCaptor.capture())).thenReturn(1);

        Flux<StreamMarketDataEvent> flux = adapter.streamTrades("BTCUSDT");

        StepVerifier.create(flux.take(1))
                .then(() -> {
                    // Malformed JSON — must not terminate the sink
                    callbackCaptor.getValue().onReceive("not-valid-json{{{");
                    // Valid event follows — sink must still be alive
                    callbackCaptor.getValue().onReceive(
                            "{\"e\":\"aggTrade\",\"p\":\"50000.00\",\"q\":\"0.01\",\"T\":1699000000000}");
                })
                .assertNext(event -> assertEquals(new BigDecimal("50000.00"), event.price()))
                .verifyComplete();
    }

    @Test
    void streamTrades_reusesExistingSinkForSameSymbol() {
        when(wsClient.aggTradeStream(eq("btcusdt"), any(WebSocketCallback.class))).thenReturn(1);

        adapter.streamTrades("BTCUSDT").subscribe();
        adapter.streamTrades("BTCUSDT").subscribe();

        // Only one underlying WebSocket connection regardless of subscriber count
        verify(wsClient, times(1)).aggTradeStream(eq("btcusdt"), any(WebSocketCallback.class));
    }

    // -------------------------------------------------------------------------
    // streamBookTicker
    // -------------------------------------------------------------------------

    @Test
    void streamBookTicker_subscribesToBookTickerStreamWithLowercaseSymbol() {
        when(wsClient.bookTicker(eq("btcusdt"), any(WebSocketCallback.class))).thenReturn(10);

        adapter.streamBookTicker("BTCUSDT").subscribe();

        verify(wsClient, times(1)).bookTicker(eq("btcusdt"), any(WebSocketCallback.class));
    }

    @Test
    void streamBookTicker_parsesValidBookTickerEvent() {
        ArgumentCaptor<WebSocketCallback> callbackCaptor =
                ArgumentCaptor.forClass(WebSocketCallback.class);
        when(wsClient.bookTicker(eq("btcusdt"), callbackCaptor.capture())).thenReturn(10);

        Flux<StreamMarketDataEvent> flux = adapter.streamBookTicker("BTCUSDT");

        StepVerifier.create(flux.take(1))
                .then(() -> callbackCaptor.getValue().onReceive(
                        "{\"b\":\"49990.00\",\"a\":\"50000.00\",\"T\":1699000000000}"))
                .assertNext(event -> {
                    assertEquals("BINANCE_FUTURES", event.exchange());
                    assertEquals("BTCUSDT", event.symbol());
                    assertEquals(EventType.BOOK_TICKER, event.type());
                    // price field holds the ask (conservative LONG entry cost)
                    assertEquals(new BigDecimal("50000.00"), event.price());
                    assertInstanceOf(BookTickerPayload.class, event.payload());
                    BookTickerPayload payload = (BookTickerPayload) event.payload();
                    assertEquals(new BigDecimal("49990.00"), payload.bid());
                    assertEquals(new BigDecimal("50000.00"), payload.ask());
                    assertNotNull(event.timestamp());
                })
                .verifyComplete();
    }

    @Test
    void streamBookTicker_usesSystemTimeWhenTimestampFieldAbsent() {
        ArgumentCaptor<WebSocketCallback> callbackCaptor =
                ArgumentCaptor.forClass(WebSocketCallback.class);
        when(wsClient.bookTicker(eq("btcusdt"), callbackCaptor.capture())).thenReturn(11);

        Flux<StreamMarketDataEvent> flux = adapter.streamBookTicker("BTCUSDT");

        StepVerifier.create(flux.take(1))
                .then(() -> callbackCaptor.getValue().onReceive(
                        "{\"b\":\"49990.00\",\"a\":\"50000.00\"}"))
                .assertNext(event -> assertNotNull(event.timestamp()))
                .verifyComplete();
    }

    @Test
    void streamBookTicker_skipsEventsMissingBidOrAsk_andKeepsSinkAlive() {
        ArgumentCaptor<WebSocketCallback> callbackCaptor =
                ArgumentCaptor.forClass(WebSocketCallback.class);
        when(wsClient.bookTicker(eq("btcusdt"), callbackCaptor.capture())).thenReturn(12);

        Flux<StreamMarketDataEvent> flux = adapter.streamBookTicker("BTCUSDT");

        StepVerifier.create(flux.take(1))
                .then(() -> {
                    // Missing bid/ask — should be silently skipped
                    callbackCaptor.getValue().onReceive("{\"T\":1699000000000}");
                    // Valid event follows — sink must still be alive
                    callbackCaptor.getValue().onReceive(
                            "{\"b\":\"49990.00\",\"a\":\"50000.00\",\"T\":1699000000000}");
                })
                .assertNext(event -> assertEquals(EventType.BOOK_TICKER, event.type()))
                .verifyComplete();
    }

    @Test
    void streamBookTicker_reusesExistingSinkForSameSymbol() {
        when(wsClient.bookTicker(eq("btcusdt"), any(WebSocketCallback.class))).thenReturn(10);

        adapter.streamBookTicker("BTCUSDT").subscribe();
        adapter.streamBookTicker("BTCUSDT").subscribe();

        verify(wsClient, times(1)).bookTicker(eq("btcusdt"), any(WebSocketCallback.class));
    }

    // -------------------------------------------------------------------------
    // cleanup
    // -------------------------------------------------------------------------

    @Test
    void cleanup_closesAllOpenConnections() {
        when(wsClient.aggTradeStream(eq("btcusdt"), any(WebSocketCallback.class))).thenReturn(1);
        when(wsClient.bookTicker(eq("btcusdt"), any(WebSocketCallback.class))).thenReturn(2);

        adapter.streamTrades("BTCUSDT").subscribe();
        adapter.streamBookTicker("BTCUSDT").subscribe();

        adapter.cleanup();

        verify(wsClient).closeConnection(1);
        verify(wsClient).closeConnection(2);
    }

    @Test
    void cleanup_withNoOpenStreams_doesNotThrow() {
        // Calling cleanup with no active streams should be a no-op
        adapter.cleanup();
        // No interactions with wsClient expected
        verify(wsClient, times(0)).closeConnection(any(Integer.class));
    }
}
