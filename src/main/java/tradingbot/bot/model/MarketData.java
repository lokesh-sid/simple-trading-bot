package tradingbot.bot.model;

import tradingbot.strategy.calculator.IndicatorValues;

/**
 * Encapsulates market indicator data for both daily and weekly timeframes.
 * <p>
 * Used by the trading bot to make entry and exit decisions based on technical analysis.
 *
 * @param dailyIndicators  Technical indicator values computed for the daily timeframe
 * @param weeklyIndicators Technical indicator values computed for the weekly timeframe
 * @see IndicatorValues
 */
public record MarketData(
    IndicatorValues dailyIndicators,
    IndicatorValues weeklyIndicators
) {}