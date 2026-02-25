package tradingbot.agent.impl.execution;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tradingbot.agent.domain.execution.ExecutionResult;
import tradingbot.agent.domain.execution.ExecutionResult.ExecutionAction;
import tradingbot.agent.domain.execution.OrderExecutionGateway;
import tradingbot.agent.domain.model.AgentDecision;
import tradingbot.agent.domain.model.AgentDecision.Action;
import tradingbot.agent.domain.risk.RiskContext;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.service.OrderResult;

/**
 * LiveOrderGateway — routes agent decisions to the real exchange
 * (e.g. {@code BinanceFuturesService}) with additional safety checks.
 *
 * <h3>Safety features</h3>
 * <ul>
 *   <li>Balance check before opening positions</li>
 *   <li>Stop-loss / take-profit bracket orders placed automatically after entry</li>
 *   <li>Full audit logging of every execution attempt</li>
 *   <li>Automatic {@link RiskContext} updates after fills</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * Position state is in {@code ConcurrentHashMap}s.  The gateway is designed to
 * be called from the {@code AgentOrchestrator}'s bounded-elastic scheduler
 * which serializes calls per agent, but the maps support concurrent access
 * from multiple agents.
 */
@Component
public class LiveOrderGateway implements OrderExecutionGateway {

    private static final Logger log = LoggerFactory.getLogger(LiveOrderGateway.class);

    private static final double DEFAULT_QUANTITY = 0.001;  // conservative default for live
    private static final double MIN_MARGIN_USDT  = 50.0;
    private static final double SL_PERCENT       = 2.0;    // 2% stop-loss
    private static final double TP_PERCENT        = 5.0;    // 5% take-profit

    private final FuturesExchangeService exchange;

    // callback to push RiskContext updates; nullable
    private Consumer<RiskContext> riskContextUpdater;

    // Position state per symbol
    private final Map<String, String> positionSides = new ConcurrentHashMap<>();
    private final Map<String, Double> entryPrices   = new ConcurrentHashMap<>();
    private final Map<String, Double> quantities     = new ConcurrentHashMap<>();

    public LiveOrderGateway(FuturesExchangeService exchange) {
        this.exchange = exchange;
    }

    /**
     * Sets the callback used to update the agent's {@link RiskContext} after fills.
     * Must be called during wiring before any executions occur.
     */
    public void setRiskContextUpdater(Consumer<RiskContext> updater) {
        this.riskContextUpdater = updater;
    }

    @Override
    public ExecutionResult execute(AgentDecision decision, String symbol, double currentPrice) {
        if (decision.action() == Action.HOLD) {
            return ExecutionResult.noop(symbol, "HOLD — no action");
        }

        log.info("[LiveGateway] Executing {} for {} @ {} (agent={})",
                decision.action(), symbol, currentPrice, decision.agentId());

        String side = positionSides.get(symbol);

        if (decision.action() == Action.BUY) {
            return handleBuy(decision, symbol, currentPrice, side);
        } else {
            return handleSell(decision, symbol, currentPrice, side);
        }
    }

    // ── BUY logic ──────────────────────────────────────────────────────────────

    private ExecutionResult handleBuy(AgentDecision decision, String symbol,
                                       double currentPrice, String currentSide) {
        if ("LONG".equals(currentSide)) {
            return ExecutionResult.noop(symbol, "Already LONG — ignoring BUY");
        }
        if ("SHORT".equals(currentSide)) {
            return closeShort(decision, symbol, currentPrice);
        }

        // Flat → check balance → enter long
        double balance = exchange.getMarginBalance();
        if (balance < MIN_MARGIN_USDT) {
            log.warn("[LiveGateway] Insufficient margin: {} < {}", balance, MIN_MARGIN_USDT);
            return ExecutionResult.failed(ExecutionAction.ENTER_LONG, symbol,
                    "Insufficient margin: " + balance);
        }

        return enterLong(decision, symbol, currentPrice);
    }

    private ExecutionResult enterLong(AgentDecision decision, String symbol, double currentPrice) {
        try {
            OrderResult order = exchange.enterLongPosition(symbol, DEFAULT_QUANTITY);
            double fillPrice = order.getAvgFillPrice() > 0 ? order.getAvgFillPrice() : currentPrice;

            positionSides.put(symbol, "LONG");
            entryPrices.put(symbol, fillPrice);
            quantities.put(symbol, DEFAULT_QUANTITY);

            // Place bracket orders (SL + TP)
            placeBracketOrders(symbol, "LONG", fillPrice, DEFAULT_QUANTITY);

            updateRiskContext(decision.agentId(), symbol);

            log.info("[LiveGateway] ENTER_LONG {} @ {} qty={} orderId={}",
                    symbol, fillPrice, DEFAULT_QUANTITY, order.getExchangeOrderId());
            return ExecutionResult.filled(ExecutionAction.ENTER_LONG, symbol,
                    order.getExchangeOrderId(), fillPrice, DEFAULT_QUANTITY, 0,
                    "Entered LONG on live exchange");
        } catch (Exception ex) {
            log.error("[LiveGateway] ENTER_LONG failed for {}: {}", symbol, ex.getMessage(), ex);
            return ExecutionResult.failed(ExecutionAction.ENTER_LONG, symbol, ex.getMessage());
        }
    }

    // ── SELL logic ─────────────────────────────────────────────────────────────

    private ExecutionResult handleSell(AgentDecision decision, String symbol,
                                        double currentPrice, String currentSide) {
        if ("SHORT".equals(currentSide)) {
            return ExecutionResult.noop(symbol, "Already SHORT — ignoring SELL");
        }
        if ("LONG".equals(currentSide)) {
            return closeLong(decision, symbol, currentPrice);
        }

        double balance = exchange.getMarginBalance();
        if (balance < MIN_MARGIN_USDT) {
            log.warn("[LiveGateway] Insufficient margin: {} < {}", balance, MIN_MARGIN_USDT);
            return ExecutionResult.failed(ExecutionAction.ENTER_SHORT, symbol,
                    "Insufficient margin: " + balance);
        }

        return enterShort(decision, symbol, currentPrice);
    }

    private ExecutionResult enterShort(AgentDecision decision, String symbol, double currentPrice) {
        try {
            OrderResult order = exchange.enterShortPosition(symbol, DEFAULT_QUANTITY);
            double fillPrice = order.getAvgFillPrice() > 0 ? order.getAvgFillPrice() : currentPrice;

            positionSides.put(symbol, "SHORT");
            entryPrices.put(symbol, fillPrice);
            quantities.put(symbol, DEFAULT_QUANTITY);

            placeBracketOrders(symbol, "SHORT", fillPrice, DEFAULT_QUANTITY);

            updateRiskContext(decision.agentId(), symbol);

            log.info("[LiveGateway] ENTER_SHORT {} @ {} qty={} orderId={}",
                    symbol, fillPrice, DEFAULT_QUANTITY, order.getExchangeOrderId());
            return ExecutionResult.filled(ExecutionAction.ENTER_SHORT, symbol,
                    order.getExchangeOrderId(), fillPrice, DEFAULT_QUANTITY, 0,
                    "Entered SHORT on live exchange");
        } catch (Exception ex) {
            log.error("[LiveGateway] ENTER_SHORT failed for {}: {}", symbol, ex.getMessage(), ex);
            return ExecutionResult.failed(ExecutionAction.ENTER_SHORT, symbol, ex.getMessage());
        }
    }

    // ── Close helpers ──────────────────────────────────────────────────────────

    private ExecutionResult closeLong(AgentDecision decision, String symbol, double currentPrice) {
        double qty = quantities.getOrDefault(symbol, DEFAULT_QUANTITY);
        double balanceBefore = exchange.getMarginBalance();

        try {
            OrderResult order = exchange.exitLongPosition(symbol, qty);
            double fillPrice = order.getAvgFillPrice() > 0 ? order.getAvgFillPrice() : currentPrice;
            double realizedPnl = exchange.getMarginBalance() - balanceBefore;

            clearPosition(symbol);
            updateRiskContext(decision.agentId(), symbol);

            log.info("[LiveGateway] EXIT_LONG {} @ {} pnl={} orderId={}",
                    symbol, fillPrice, realizedPnl, order.getExchangeOrderId());
            return ExecutionResult.filled(ExecutionAction.EXIT_LONG, symbol,
                    order.getExchangeOrderId(), fillPrice, qty, realizedPnl,
                    "Closed LONG on live exchange");
        } catch (Exception ex) {
            log.error("[LiveGateway] EXIT_LONG failed for {}: {}", symbol, ex.getMessage(), ex);
            return ExecutionResult.failed(ExecutionAction.EXIT_LONG, symbol, ex.getMessage());
        }
    }

    private ExecutionResult closeShort(AgentDecision decision, String symbol, double currentPrice) {
        double qty = quantities.getOrDefault(symbol, DEFAULT_QUANTITY);
        double balanceBefore = exchange.getMarginBalance();

        try {
            OrderResult order = exchange.exitShortPosition(symbol, qty);
            double fillPrice = order.getAvgFillPrice() > 0 ? order.getAvgFillPrice() : currentPrice;
            double realizedPnl = exchange.getMarginBalance() - balanceBefore;

            clearPosition(symbol);
            updateRiskContext(decision.agentId(), symbol);

            log.info("[LiveGateway] EXIT_SHORT {} @ {} pnl={} orderId={}",
                    symbol, fillPrice, realizedPnl, order.getExchangeOrderId());
            return ExecutionResult.filled(ExecutionAction.EXIT_SHORT, symbol,
                    order.getExchangeOrderId(), fillPrice, qty, realizedPnl,
                    "Closed SHORT on live exchange");
        } catch (Exception ex) {
            log.error("[LiveGateway] EXIT_SHORT failed for {}: {}", symbol, ex.getMessage(), ex);
            return ExecutionResult.failed(ExecutionAction.EXIT_SHORT, symbol, ex.getMessage());
        }
    }

    // ── Bracket orders ─────────────────────────────────────────────────────────

    /**
     * Places stop-loss and take-profit bracket orders after a fill.
     * Failures are logged but do not roll back the entry.
     */
    private void placeBracketOrders(String symbol, String side, double entryPrice, double qty) {
        try {
            if ("LONG".equals(side)) {
                double slPrice = entryPrice * (1 - SL_PERCENT / 100.0);
                double tpPrice = entryPrice * (1 + TP_PERCENT / 100.0);
                exchange.placeStopLossOrder(symbol, "Sell", qty, slPrice);
                exchange.placeTakeProfitOrder(symbol, "Sell", qty, tpPrice);
                log.info("[LiveGateway] Bracket LONG: SL={} TP={}", slPrice, tpPrice);
            } else {
                double slPrice = entryPrice * (1 + SL_PERCENT / 100.0);
                double tpPrice = entryPrice * (1 - TP_PERCENT / 100.0);
                exchange.placeStopLossOrder(symbol, "Buy", qty, slPrice);
                exchange.placeTakeProfitOrder(symbol, "Buy", qty, tpPrice);
                log.info("[LiveGateway] Bracket SHORT: SL={} TP={}", slPrice, tpPrice);
            }
        } catch (Exception ex) {
            log.error("[LiveGateway] Failed to place bracket orders for {} {}: {}",
                    side, symbol, ex.getMessage(), ex);
        }
    }

    // ── Position tracking ──────────────────────────────────────────────────────

    private void clearPosition(String symbol) {
        positionSides.remove(symbol);
        entryPrices.remove(symbol);
        quantities.remove(symbol);
    }

    private void updateRiskContext(String agentId, String symbol) {
        if (riskContextUpdater == null) return;

        String side = positionSides.get(symbol);
        if (side == null) {
            riskContextUpdater.accept(RiskContext.noPosition(agentId, symbol));
        } else if ("LONG".equals(side)) {
            double entry = entryPrices.getOrDefault(symbol, 0.0);
            riskContextUpdater.accept(RiskContext.longPosition(
                    agentId, symbol, entry,
                    quantities.getOrDefault(symbol, 0.0),
                    entry * (1 - SL_PERCENT / 100.0),   // hard SL price
                    entry * (1 + TP_PERCENT / 100.0),    // hard TP price
                    SL_PERCENT, TP_PERCENT));
        } else {
            double entry = entryPrices.getOrDefault(symbol, 0.0);
            riskContextUpdater.accept(RiskContext.shortPosition(
                    agentId, symbol, entry,
                    quantities.getOrDefault(symbol, 0.0),
                    entry * (1 + SL_PERCENT / 100.0),
                    entry * (1 - TP_PERCENT / 100.0),
                    SL_PERCENT, TP_PERCENT));
        }
    }

    // ── Query methods ──────────────────────────────────────────────────────────

    @Override
    public boolean hasOpenPosition(String symbol) {
        return positionSides.containsKey(symbol);
    }

    @Override
    public String getPositionSide(String symbol) {
        return positionSides.get(symbol);
    }

    @Override
    public double getEntryPrice(String symbol) {
        return entryPrices.getOrDefault(symbol, 0.0);
    }

    @Override
    public double getPositionQuantity(String symbol) {
        return quantities.getOrDefault(symbol, 0.0);
    }
}
