package com.capstone.etp.automation;

/** The outcome of evaluating a strategy once: a trigger (BUY or SELL) or nothing, and why. */
public record StrategySignal(SignalType type, String reason) {

    public static StrategySignal none(String reason) {
        return new StrategySignal(SignalType.NONE, reason);
    }

    public boolean isTrigger() {
        return type != SignalType.NONE;
    }
}
