package com.capstone.etp.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Refuses a strategy that could trade on a bad account, symbol, quantity or parameters. */
public final class StrategyDefinitionValidator {

    private static final Pattern SYMBOL = Pattern.compile("^[A-Z0-9.:^=\\-]{1,20}$");

    private StrategyDefinitionValidator() {
    }

    /** @return what is wrong, empty when the definition is usable */
    public static List<String> problems(StrategyDefinition definition) {
        List<String> problems = new ArrayList<>();
        if (definition == null) {
            problems.add("no definition");
            return problems;
        }
        if (definition.getId() == null || definition.getId().isBlank()) {
            problems.add("id is required");
        }
        if (definition.getType() == null) {
            problems.add("type is required (MA_CROSSOVER or BOLLINGER_BANDS)");
        }
        if (definition.getAccountId() == null || definition.getAccountId() < 1) {
            problems.add("accountId must be a valid account (1 or more)");
        }
        String symbol = definition.getSymbol() == null ? "" : definition.getSymbol().trim().toUpperCase(Locale.ROOT);
        if (!SYMBOL.matcher(symbol).matches()) {
            problems.add("symbol is missing or not valid");
        }
        if (definition.getQuantity() == null || definition.getQuantity() < 1) {
            problems.add("quantity must be at least 1");
        }
        if (definition.getType() == StrategyType.MA_CROSSOVER) {
            Integer shortPeriod = definition.getShortPeriod();
            Integer longPeriod = definition.getLongPeriod();
            if (shortPeriod == null || shortPeriod < 1) {
                problems.add("shortPeriod must be at least 1");
            }
            if (longPeriod == null || shortPeriod == null || longPeriod <= shortPeriod) {
                problems.add("longPeriod must be above shortPeriod");
            }
        }
        if (definition.getType() == StrategyType.BOLLINGER_BANDS) {
            if (definition.getPeriod() == null || definition.getPeriod() < 2) {
                problems.add("period must be at least 2");
            }
            if (definition.getMultiplier() == null || definition.getMultiplier().signum() <= 0) {
                problems.add("multiplier must be above zero");
            }
        }
        return problems;
    }
}
