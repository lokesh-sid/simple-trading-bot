package tradingbot.strategy.exit;

import tradingbot.strategy.calculator.IndicatorCalculator;
import tradingbot.strategy.calculator.IndicatorValues;

public class MACDExit implements PositionExitCondition {
    private final IndicatorCalculator calculator;

    public MACDExit(IndicatorCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public boolean shouldExit() {
        IndicatorValues indicators = calculator.computeIndicators("1d", "BTCUSDT");
        return indicators != null && indicators.getMacd() < indicators.getSignal();
    }
}