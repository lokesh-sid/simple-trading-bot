package tradingbot.bot.service.backtest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tradingbot.agent.AgenticTradingAgent;
import tradingbot.agent.domain.model.AgentDecision;
import tradingbot.agent.domain.model.AgentDecision.Action;
import tradingbot.bot.service.BinanceFuturesService.Candle;
import tradingbot.config.TradingConfig;
import tradingbot.domain.market.KlineClosedEvent;

/**
 * CsvBacktestAgentExecutionService — drives an {@link AgenticTradingAgent}
 * through a list of historical {@link Candle}s and records simulated fills.
 *
 * <h3>Replay loop</h3>
 * <ol>
 *   <li>Call {@link BacktestExchangeService#setMarketContext(List, int)} for each bar
 *       (sets current price + processes pending orders internally).</li>
 *   <li>Convert the bar to a {@link KlineClosedEvent} and call
 *       {@code agent.onKlineClosed(event).block()} — LLM reasoning is exercised
 *       for every bar (CachedGrokService returns synthetic / cached response
 *       instantaneously in backtest mode).</li>
 *   <li>Route BUY/SELL decisions through the exchange; HOLD is a no-op.</li>
 *   <li>Record the running equity after each bar and every fill as a
 *       {@link tradingbot.bot.service.backtest.BacktestAgentExecutionService.TradeEvent}.</li>
 * </ol>
 *
 * <h3>Position model</h3>
 * Simple directional tracking:  one long position at a time.
 * <ul>
 *   <li>BUY signal → open long (if not already long)</li>
 *   <li>SELL signal → close long (if currently long)</li>
 *   <li>Quantity = fixed 1.0 contract (configurable per agent in Phase 3)</li>
 * </ul>
 */
@Component
public class CsvBacktestAgentExecutionService implements BacktestAgentExecutionService {

    private static final Logger log =
            LoggerFactory.getLogger(CsvBacktestAgentExecutionService.class);

    private static final double DEFAULT_TRADE_QUANTITY = 1.0;

    @Override
    public ExecutionResult execute(AgenticTradingAgent agent,
                                   List<Candle> history,
                                   TradingConfig config,
                                   BacktestExchangeService exchange) {

        String symbol    = config.getSymbol();
        int    totalBars = history.size();

        List<TradeEvent> trades      = new ArrayList<>();
        List<Double>     equityCurve = new ArrayList<>(totalBars);

        boolean inLong = false; // simple 1-position tracker

        log.info("[CsvBacktest] starting replay: symbol={} bars={} agent={}",
                symbol, totalBars, agent.getId());

        for (int i = 0; i < totalBars; i++) {
            Candle candle = history.get(i);

            // 1. Advance time: sets current price + processes pending fills
            exchange.setMarketContext(history, i);

            // 2. Ask the agent to evaluate this closed bar
            KlineClosedEvent event = toKlineEvent(symbol, candle, config);
            AgentDecision decision;
            try {
                decision = agent.onKlineClosed(event).block();
            } catch (Exception ex) {
                log.warn("[CsvBacktest] bar {} agent error: {}", i, ex.getMessage());
                equityCurve.add(exchange.getMarginBalance());
                continue;
            }

            if (decision == null) {
                equityCurve.add(exchange.getMarginBalance());
                continue;
            }

            // 3. Route decision → exchange
            Action action = decision.action();
            double balanceBefore = exchange.getMarginBalance();

            if (action == Action.BUY && !inLong) {
                try {
                    exchange.enterLongPosition(symbol, DEFAULT_TRADE_QUANTITY);
                    double fillPrice = exchange.getCurrentPrice(symbol);
                    trades.add(new TradeEvent(i, symbol, "BUY", fillPrice,
                            DEFAULT_TRADE_QUANTITY, 0.0, decision.reasoning()));
                    inLong = true;
                    log.debug("[CsvBacktest] bar={} BUY @ {}", i, fillPrice);
                } catch (Exception ex) {
                    log.warn("[CsvBacktest] bar {} BUY failed: {}", i, ex.getMessage());
                }

            } else if (action == Action.SELL && inLong) {
                try {
                    exchange.exitLongPosition(symbol, DEFAULT_TRADE_QUANTITY);
                    double fillPrice   = exchange.getCurrentPrice(symbol);
                    double realizedPnl = exchange.getMarginBalance() - balanceBefore;
                    trades.add(new TradeEvent(i, symbol, "SELL", fillPrice,
                            DEFAULT_TRADE_QUANTITY, realizedPnl, decision.reasoning()));
                    inLong = false;
                    log.debug("[CsvBacktest] bar={} SELL @ {} pnl={}", i, fillPrice, realizedPnl);
                } catch (Exception ex) {
                    log.warn("[CsvBacktest] bar {} SELL failed: {}", i, ex.getMessage());
                }
            }

            // 4. Record equity snapshot after the bar
            equityCurve.add(exchange.getMarginBalance());
        }

        log.info("[CsvBacktest] replay complete: bars={} trades={} finalBalance={}",
                totalBars, trades.size(), exchange.getMarginBalance());

        return new ExecutionResult(trades, equityCurve, totalBars);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * Converts a {@link Candle} (epoch-ms timestamps) into a
     * {@link KlineClosedEvent} (Instant).
     */
    private KlineClosedEvent toKlineEvent(String symbol, Candle candle, TradingConfig config) {
        String exchange = "BACKTEST";
        String interval = String.valueOf(config.getInterval()) + "m";
        Instant openTime  = Instant.ofEpochMilli(candle.getOpenTime());
        Instant closeTime = Instant.ofEpochMilli(candle.getCloseTime());

        // Guard against null OHLCV (malformed CSV rows)
        BigDecimal open   = nvl(candle.getOpen());
        BigDecimal high   = nvl(candle.getHigh());
        BigDecimal low    = nvl(candle.getLow());
        BigDecimal close  = nvl(candle.getClose());
        BigDecimal volume = nvl(candle.getVolume());

        return new KlineClosedEvent(exchange, symbol, interval,
                open, high, low, close, volume, openTime, closeTime);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
