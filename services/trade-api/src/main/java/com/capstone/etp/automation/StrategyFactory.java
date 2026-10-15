package com.capstone.etp.automation;

/** Builds the rule for a validated definition. The one place that knows which class belongs to which type. */
public final class StrategyFactory {

    private StrategyFactory() {
    }

    public static TradingStrategy create(StrategyDefinition definition) {
        return switch (definition.getType()) {
            case MA_CROSSOVER -> new MovingAverageCrossoverStrategy(definition.getShortPeriod(), definition.getLongPeriod());
            case BOLLINGER_BANDS -> new BollingerBandStrategy(definition.getPeriod(), definition.getMultiplier());
        };
    }
}
