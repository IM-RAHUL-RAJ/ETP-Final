package com.capstone.etp.automation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategiesTest {

    private static List<BigDecimal> prices(double... values) {
        List<BigDecimal> list = new ArrayList<>();
        for (double value : values) {
            list.add(BigDecimal.valueOf(value));
        }
        return list;
    }

    /** All the history up to and including each tick, the way the service sees it. */
    private static List<SignalType> replay(TradingStrategy strategy, double... values) {
        List<SignalType> signals = new ArrayList<>();
        for (int i = 1; i <= values.length; i++) {
            double[] slice = java.util.Arrays.copyOf(values, i);
            signals.add(strategy.evaluate(prices(slice)).type());
        }
        return signals;
    }

    // ----------------------------------------------------------------------- moving-average crossover

    private final MovingAverageCrossoverStrategy crossover = new MovingAverageCrossoverStrategy(2, 4);

    @Test
    void crossoverBuysWhenTheShortAverageMovesAboveTheLongOne() {
        // previous: short 10, long 10 (short <= long). current: short 11, long 10.5 (short > long)
        StrategySignal signal = crossover.evaluate(prices(10, 10, 10, 10, 12));

        assertEquals(SignalType.BUY, signal.type());
        assertTrue(signal.reason().contains("crossed above"), signal.reason());
    }

    @Test
    void crossoverSellsWhenTheShortAverageMovesBelowTheLongOne() {
        // previous: short 10, long 10 (short >= long). current: short 9, long 9.5 (short < long)
        StrategySignal signal = crossover.evaluate(prices(10, 10, 10, 10, 8));

        assertEquals(SignalType.SELL, signal.type());
        assertTrue(signal.reason().contains("crossed below"), signal.reason());
    }

    @Test
    void crossoverDoesNothingWhileTheShortAverageStaysBelowTheLongOne() {
        assertEquals(SignalType.NONE, crossover.evaluate(prices(10, 9, 8, 7, 6)).type());
        assertEquals(SignalType.NONE, crossover.evaluate(prices(10, 9, 8, 7, 6.5)).type());
    }

    @Test
    void crossoverDoesNotTriggerJustBecauseTheShortAverageIsAboveTheLongOne() {
        // Rising all the way: short is above long throughout, with no transition on the last tick.
        assertEquals(SignalType.NONE, crossover.evaluate(prices(1, 2, 3, 4, 5)).type());
    }

    @Test
    void anExistingCrossoverDoesNotTriggerAgainOnLaterTicks() {
        List<SignalType> signals = replay(crossover, 10, 10, 10, 10, 12, 13, 14, 15, 16);

        assertEquals(1, signals.stream().filter(signal -> signal == SignalType.BUY).count(), signals.toString());
        assertEquals(SignalType.BUY, signals.get(4));
        assertEquals(List.of(SignalType.NONE, SignalType.NONE, SignalType.NONE, SignalType.NONE),
                signals.subList(5, 9));
    }

    @Test
    void crossoverNeedsEnoughHistoryAndSaysSo() {
        StrategySignal signal = crossover.evaluate(prices(10, 10, 10, 12));

        assertEquals(SignalType.NONE, signal.type());
        assertTrue(signal.reason().contains("insufficient history: 4 of 5"), signal.reason());
        assertEquals(SignalType.NONE, crossover.evaluate(List.of()).type());
    }

    @Test
    void crossoverRefusesPeriodsThatMakeNoSense() {
        assertThrows(IllegalArgumentException.class, () -> new MovingAverageCrossoverStrategy(4, 4));
        assertThrows(IllegalArgumentException.class, () -> new MovingAverageCrossoverStrategy(0, 4));
    }

    // ----------------------------------------------------------------------- bollinger bands

    private final BollingerBandStrategy bollinger = new BollingerBandStrategy(5, new BigDecimal("1.5"));

    @Test
    void bollingerBuysWhenThePriceCrossesBelowTheLowerBand() {
        StrategySignal signal = bollinger.evaluate(prices(10, 11, 10, 11, 10, 11, 10, 2));

        assertEquals(SignalType.BUY, signal.type());
        assertTrue(signal.reason().contains("below the lower band"), signal.reason());
    }

    @Test
    void bollingerSellsWhenThePriceCrossesAboveTheUpperBand() {
        StrategySignal signal = bollinger.evaluate(prices(10, 9, 10, 9, 10, 9, 10, 18));

        assertEquals(SignalType.SELL, signal.type());
        assertTrue(signal.reason().contains("above the upper band"), signal.reason());
    }

    @Test
    void bollingerDoesNothingInsideTheBands() {
        assertEquals(SignalType.NONE, bollinger.evaluate(prices(10, 11, 10, 11, 10, 11, 10, 11)).type());
    }

    @Test
    void bollingerTriggersOnTheCrossingNotOnEveryTickOutsideTheBand() {
        List<SignalType> signals = replay(bollinger, 10, 11, 10, 11, 10, 11, 10, 2, 2, 2, 2);

        assertEquals(1, signals.stream().filter(signal -> signal == SignalType.BUY).count(), signals.toString());
        assertEquals(SignalType.BUY, signals.get(7));
        assertFalse(signals.subList(8, signals.size()).contains(SignalType.BUY));
    }

    @Test
    void bollingerNeedsEnoughHistory() {
        StrategySignal signal = bollinger.evaluate(prices(10, 11, 10));

        assertEquals(SignalType.NONE, signal.type());
        assertTrue(signal.reason().contains("insufficient history"), signal.reason());
    }

    @Test
    void bollingerRefusesBadParameters() {
        assertThrows(IllegalArgumentException.class, () -> new BollingerBandStrategy(1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new BollingerBandStrategy(20, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new BollingerBandStrategy(20, null));
    }

    @Test
    void theBandsAreMiddlePlusAndMinusMultiplierTimesTheStandardDeviation() {
        // 5 prices 2,4,4,4,5,5,7,9 style check on a window of 8: mean 5, population sd 2.
        BollingerBandStrategy strategy = new BollingerBandStrategy(8, new BigDecimal("2"));
        List<BigDecimal> window = prices(2, 4, 4, 4, 5, 5, 7, 9);
        BigDecimal mean = Averages.mean(window, 8, 8);

        assertEquals(0, mean.compareTo(new BigDecimal("5")));
        assertEquals(0, Averages.standardDeviation(window, 8, 8, mean).compareTo(new BigDecimal("2")));
        assertEquals(9, strategy.requiredObservations());
    }

    // ----------------------------------------------------------------------- price history

    @Test
    void historyIsBoundedAndDropsTheOldestPrice() {
        PriceHistory history = new PriceHistory(3);

        for (int price = 1; price <= 5; price++) {
            assertTrue(history.add("AAPL", BigDecimal.valueOf(price), null));
        }

        assertEquals(List.of(BigDecimal.valueOf(3), BigDecimal.valueOf(4), BigDecimal.valueOf(5)), history.snapshot("AAPL"));
    }

    @Test
    void aQuoteThatIsNotNewerIsNotANewObservation() {
        PriceHistory history = new PriceHistory(10);
        java.time.Instant t = java.time.Instant.parse("2026-10-07T10:00:00Z");

        assertTrue(history.add("AAPL", BigDecimal.ONE, t));
        assertFalse(history.add("AAPL", BigDecimal.TEN, t));
        assertFalse(history.add("AAPL", BigDecimal.TEN, t.minusSeconds(5)));
        assertTrue(history.add("AAPL", BigDecimal.TEN, t.plusSeconds(5)));
        assertEquals(2, history.size("AAPL"));
    }

    @Test
    void symbolsAreKeptApart() {
        PriceHistory history = new PriceHistory(10);
        history.add("AAPL", BigDecimal.ONE, null);
        history.add("MSFT", BigDecimal.TEN, null);

        assertEquals(List.of(BigDecimal.ONE), history.snapshot("AAPL"));
        assertEquals(List.of(BigDecimal.TEN), history.snapshot("MSFT"));
        assertTrue(history.snapshot("NVDA").isEmpty());
    }

    // ----------------------------------------------------------------------- definitions

    private static StrategyDefinition definition() {
        StrategyDefinition definition = new StrategyDefinition();
        definition.setId("s1");
        definition.setType(StrategyType.MA_CROSSOVER);
        definition.setAccountId(1L);
        definition.setSymbol("AAPL");
        definition.setQuantity(100);
        definition.setShortPeriod(20);
        definition.setLongPeriod(50);
        definition.setEnabled(true);
        return definition;
    }

    @Test
    void aGoodDefinitionHasNoProblems() {
        assertTrue(StrategyDefinitionValidator.problems(definition()).isEmpty());
    }

    @Test
    void anInvalidAccountIsRefused() {
        StrategyDefinition definition = definition();
        definition.setAccountId(null);
        assertTrue(StrategyDefinitionValidator.problems(definition).stream().anyMatch(p -> p.contains("accountId")));
        definition.setAccountId(0L);
        assertTrue(StrategyDefinitionValidator.problems(definition).stream().anyMatch(p -> p.contains("accountId")));
    }

    @Test
    void anInvalidSymbolIsRefused() {
        for (String bad : new String[] {null, "", "   ", "A B", "THIS-SYMBOL-IS-FAR-TOO-LONG", "AAPL;DROP"}) {
            StrategyDefinition definition = definition();
            definition.setSymbol(bad);
            assertTrue(StrategyDefinitionValidator.problems(definition).stream().anyMatch(p -> p.contains("symbol")),
                    String.valueOf(bad));
        }
    }

    @Test
    void aBadQuantityOrBadParametersAreRefused() {
        StrategyDefinition quantity = definition();
        quantity.setQuantity(0);
        assertFalse(StrategyDefinitionValidator.problems(quantity).isEmpty());

        StrategyDefinition periods = definition();
        periods.setShortPeriod(50);
        assertTrue(StrategyDefinitionValidator.problems(periods).stream().anyMatch(p -> p.contains("longPeriod")));

        StrategyDefinition bands = definition();
        bands.setType(StrategyType.BOLLINGER_BANDS);
        bands.setPeriod(1);
        bands.setMultiplier(BigDecimal.ZERO);
        assertEquals(2, StrategyDefinitionValidator.problems(bands).size());
    }

    @Test
    void theFactoryBuildsTheRuleForTheType() {
        assertTrue(StrategyFactory.create(definition()) instanceof MovingAverageCrossoverStrategy);
        StrategyDefinition bands = definition();
        bands.setType(StrategyType.BOLLINGER_BANDS);
        bands.setPeriod(20);
        bands.setMultiplier(BigDecimal.valueOf(2));
        assertTrue(StrategyFactory.create(bands) instanceof BollingerBandStrategy);
    }

    // ----------------------------------------------------------------------- starting prices

    @Test
    void startingPricesFillTheFrontOfAShortSeriesAndLivePricesStayNewest() {
        PriceHistory history = new PriceHistory(10);
        history.add("AAPL", new BigDecimal("50"), null);
        history.add("AAPL", new BigDecimal("51"), null);

        history.prependIfShort("AAPL", prices(1, 2, 3, 4, 5, 6, 7, 8), 5);

        assertEquals(List.of(6, 7, 8, 50, 51), history.snapshot("AAPL").stream().map(BigDecimal::intValue).toList());
    }

    @Test
    void startingPricesAreIgnoredOnceThereIsEnoughHistory() {
        PriceHistory history = new PriceHistory(10);
        for (int i = 0; i < 5; i++) {
            history.add("AAPL", BigDecimal.TEN, null);
        }

        history.prependIfShort("AAPL", prices(1, 2, 3), 5);

        assertEquals(5, history.size("AAPL"));
        assertFalse(history.snapshot("AAPL").contains(BigDecimal.valueOf(1.0)));
    }

    @Test
    void startingPricesNeverExceedTheCapacityAndEmptyOnesDoNothing() {
        PriceHistory history = new PriceHistory(4);

        history.prependIfShort("AAPL", prices(1, 2, 3, 4, 5, 6), 50);
        history.prependIfShort("MSFT", List.of(), 5);

        assertEquals(4, history.size("AAPL"));
        assertEquals(0, history.size("MSFT"));
    }
}
