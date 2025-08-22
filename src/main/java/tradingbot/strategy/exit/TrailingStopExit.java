package tradingbot.strategy.exit;

import tradingbot.strategy.tracker.TrailingStopTracker;

public class TrailingStopExit implements PositionExitCondition {
    private TrailingStopTracker tracker;

    public TrailingStopExit(TrailingStopTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public boolean shouldExit() {
        return tracker.checkTrailingStop(tracker.getCurrentPrice());
    }
}