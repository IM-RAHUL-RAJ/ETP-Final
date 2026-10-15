package com.capstone.etp.automation;

import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.dto.PlaceOrderRequest;
import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.kafka.consumer.QuoteObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;

/**
 * Runs the configured strategies against the real-time price stream (the market-data topic, via QuoteObserver) and
 * places an order through the Trade REST API when one triggers.
 *
 * <pre>
 * quote -> price history -> strategy -> trigger -> order request -> Trade REST API -> (existing order flow)
 * </pre>
 *
 * It never writes to the orders table and never publishes to the execution topic itself. Strategies are checked
 * when the service starts: a disabled or invalid one is left out and can never place an order.
 */
@Slf4j
public class AutomationService implements QuoteObserver {

    private static final int RECENT_RESULTS = 200;
    private static final int CHART_POINTS = 120;

    private record Active(StrategyDefinition definition, String symbol, TradingStrategy strategy) {
    }

    private final AutomationProperties properties;
    private final AutomationMapper store;
    private final HistorySeeder seeder;
    private final TradeApiClient tradeApi;
    private final Executor executor;
    private final Clock clock;
    private final PriceHistory history;
    private volatile Map<String, List<Active>> bySymbol = Map.of();
    private final Deque<AutomationResult> recent = new ConcurrentLinkedDeque<>();

    public AutomationService(AutomationProperties properties, AutomationMapper store, TradeApiClient tradeApi,
                             Executor executor, Clock clock) {
        this(properties, store, (symbol, count) -> List.of(), tradeApi, executor, clock);
    }

    public AutomationService(AutomationProperties properties, AutomationMapper store, HistorySeeder seeder, TradeApiClient tradeApi,
                             Executor executor, Clock clock) {
        this.properties = properties;
        this.store = store;
        this.seeder = seeder;
        this.tradeApi = tradeApi;
        this.executor = executor;
        this.clock = clock;
        this.history = new PriceHistory(Math.max(properties.getHistorySize(), 10));
        reload();
    }

    /**
     * Loads the strategies that are switched on. Called at start, after every change a customer makes, and on a timer
     * so a change made on another instance is picked up. A strategy that is invalid is left out; if the store cannot
     * be read the strategies already running keep running.
     */
    @Scheduled(fixedDelayString = "${app.automation.reload-ms:30000}", initialDelay = 30_000)
    public void reload() {
        if (!properties.isEnabled()) {
            bySymbol = Map.of();
            log.warn("[AUTOMATION] switched off on the platform (app.automation.enabled=false): no strategy will trade");
            return;
        }
        List<StrategyDefinition> definitions;
        try {
            definitions = store.findEnabled();
        } catch (RuntimeException e) {
            log.error("[AUTOMATION] could not read the strategies, keeping the ones running: {}", e.getClass().getSimpleName());
            return;
        }
        Map<String, List<Active>> fresh = new HashMap<>();
        for (StrategyDefinition definition : definitions) {
            register(fresh, definition, properties.getHistorySize());
        }
        bySymbol = fresh;
        log.info("[AUTOMATION] strategies running={}", fresh.values().stream().mapToInt(List::size).sum());
        warmUp(fresh);
    }

    /**
     * A strategy that has just been switched on starts from recent prices rather than from nothing, so it can act on the
     * very next live tick instead of waiting minutes for a full window. Done off the caller's thread; a failure just
     * means the strategy warms up from the live stream instead.
     */
    private void warmUp(Map<String, List<Active>> running) {
        running.forEach((symbol, actives) -> {
            int wanted = actives.stream().mapToInt(active -> active.strategy().requiredObservations()).max().orElse(0);
            if (wanted > 0 && history.size(symbol) < wanted) {
                executor.execute(() -> {
                    List<BigDecimal> starting = seeder.recentCloses(symbol, wanted);
                    if (!starting.isEmpty()) {
                        history.prependIfShort(symbol, starting, wanted);
                        log.info("[AUTOMATION] warmed up symbol={} with {} recent prices", symbol, Math.min(starting.size(), wanted));
                    }
                });
            }
        });
    }

    /** How many prices have been collected for a running strategy, and how many it needs before it can act. */
    public Optional<Progress> progressOf(String strategyId) {
        for (List<Active> actives : bySymbol.values()) {
            for (Active active : actives) {
                if (active.definition().getId().equals(strategyId)) {
                    return Optional.of(new Progress(Math.min(history.size(active.symbol()), active.strategy().requiredObservations()),
                            active.strategy().requiredObservations()));
                }
            }
        }
        return Optional.empty();
    }

    public record Progress(int collected, int required) {
    }

    private void register(Map<String, List<Active>> target, StrategyDefinition definition, int historySize) {
        String id = definition == null ? null : definition.getId();
        List<String> problems = StrategyDefinitionValidator.problems(definition);
        if (!problems.isEmpty()) {
            log.error("[AUTOMATION] strategy refused id={} problems={}", id, problems);
            return;
        }
        TradingStrategy strategy = StrategyFactory.create(definition);
        if (strategy.requiredObservations() > historySize) {
            log.error("[AUTOMATION] strategy refused id={} problems=[history-size {} is smaller than the {} observations it needs]",
                    id, historySize, strategy.requiredObservations());
            return;
        }
        String symbol = definition.getSymbol().trim().toUpperCase(Locale.ROOT);
        target.computeIfAbsent(symbol, key -> new ArrayList<>()).add(new Active(definition, symbol, strategy));
        log.info("[AUTOMATION] strategy active id={} strategy={} symbol={} accountId={} quantity={}",
                id, strategy.name(), symbol, definition.getAccountId(), definition.getQuantity());
    }

    @Override
    public void onQuote(MarketQuote quote) {
        if (quote == null || quote.getSymbol() == null || quote.getSymbol().isBlank()
                || quote.getPrice() == null || quote.getPrice().signum() <= 0) {
            log.warn("[AUTOMATION] malformed price event ignored");
            return;
        }
        String symbol = quote.getSymbol().trim().toUpperCase(Locale.ROOT);
        List<Active> strategies = bySymbol.get(symbol);
        if (strategies == null || strategies.isEmpty()) {
            return;
        }
        if (!history.add(symbol, quote.getPrice(), quote.getQuoteAsOf())) {
            log.debug("[AUTOMATION] price already seen, not a new observation symbol={}", symbol);
            return;
        }
        List<BigDecimal> prices = history.snapshot(symbol);
        for (Active active : strategies) {
            evaluate(active, prices, quote);
        }
    }

    /** What has happened lately, newest first. For logs, tests and a future status page. */
    public List<AutomationResult> recentResults() {
        return Collections.unmodifiableList(new ArrayList<>(recent));
    }

    private void evaluate(Active active, List<BigDecimal> prices, MarketQuote quote) {
        StrategyDefinition definition = active.definition();
        StrategySignal signal;
        try {
            signal = active.strategy().evaluate(prices);
        } catch (RuntimeException e) {
            log.error("[AUTOMATION] strategy evaluation failed id={} symbol={} error={}",
                    definition.getId(), active.symbol(), e.getClass().getSimpleName());
            return;
        }
        log.debug("[AUTOMATION] evaluated id={} symbol={} signal={} reason={}",
                definition.getId(), active.symbol(), signal.type(), signal.reason());
        if (!signal.isTrigger()) {
            return;
        }

        fire(active, signal, marketableLimit(signal.type(), quote), keyFor(definition, active.symbol(), signal.type(), quote, prices.size()), "");
    }

    /**
     * The limit an automated order is sent with. The executor fills a BUY when the limit is at or above the ask, and a SELL when it
     * is at or below the bid, so a BUY uses the ask and a SELL the bid (the last price sits between them and would be refused),
     * with a small allowance (app.automation.limit-buffer-percent) for the quote moving before the executor looks again.
     */
    BigDecimal marketableLimit(SignalType side, MarketQuote quote) {
        BigDecimal reference = side == SignalType.BUY ? quote.getAsk() : quote.getBid();
        if (reference == null || reference.signum() <= 0) {
            reference = quote.getPrice();
        }
        BigDecimal buffer = properties.getLimitBufferPercent().movePointLeft(2);
        return side == SignalType.BUY
                ? reference.multiply(BigDecimal.ONE.add(buffer))
                : reference.multiply(BigDecimal.ONE.subtract(buffer));
    }

    /** A trigger: record it, build the order and hand it to the one order thread. The same path for a live tick and a demo. */
    private void fire(Active active, StrategySignal signal, BigDecimal price, UUID key, String note) {
        StrategyDefinition definition = active.definition();
        Instant now = clock.instant();
        log.info("[AUTOMATION] event={} strategy={} symbol={} accountId={} trigger={} at={} reason={}{}",
                OrderOutcome.STRATEGY_TRIGGERED, definition.getId(), active.symbol(), definition.getAccountId(),
                signal.type(), now, signal.reason(), note.isEmpty() ? "" : " (" + note + ")");
        remember(definition, active.symbol(), signal.type(), OrderOutcome.STRATEGY_TRIGGERED, null, note + signal.reason(), now);

        PlaceOrderRequest request = PlaceOrderRequest.builder()
                .accountId(definition.getAccountId())
                .symbol(active.symbol())
                .side(signal.type() == SignalType.BUY ? OrderSide.BUY : OrderSide.SELL)
                .quantity(definition.getQuantity())
                .price(price.setScale(2, RoundingMode.HALF_UP))
                .idempotencyKey(key)
                .build();
        Long eventId = recordTrigger(definition, active.symbol(), signal, request, note);
        executor.execute(() -> submit(definition, active.symbol(), signal, request, eventId));
    }

    /** The persistent history (automation_events). Optional: without it the in-memory list still works. */
    private volatile AutomationEventMapper events;

    public void useHistory(AutomationEventMapper events) {
        this.events = events;
    }

    private Long recordTrigger(StrategyDefinition definition, String symbol, StrategySignal signal, PlaceOrderRequest request, String note) {
        if (events == null) {
            return null;
        }
        try {
            Long strategyId = definition.getId() == null || !definition.getId().matches("\\d+") ? null : Long.valueOf(definition.getId());
            return events.insertTrigger(strategyId, definition.getAccountId(), String.valueOf(definition.getType()), symbol,
                    signal.type().name(), request.getIdempotencyKey(), request.getPrice(), request.getQuantity(), note + signal.reason());
        } catch (RuntimeException e) {
            log.warn("[AUTOMATION] could not save the trigger to the history: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private void recordOutcome(Long eventId, OrderOutcome outcome, UUID orderId, String detail) {
        if (events == null || eventId == null) {
            return;
        }
        try {
            events.recordOutcome(eventId, outcome.name(), orderId, detail);
        } catch (RuntimeException e) {
            log.warn("[AUTOMATION] could not save the outcome to the history: {}", e.getClass().getSimpleName());
        }
    }

    /** What a running strategy is looking at: the prices collected and the lines the rule compares them with. */
    public record StrategyChart(List<BigDecimal> prices, Map<String, List<BigDecimal>> lines) {
    }

    public Optional<StrategyChart> chartOf(String strategyId) {
        Active active = find(strategyId);
        if (active == null) {
            return Optional.empty();
        }
        List<BigDecimal> all = history.snapshot(active.symbol());
        int from = Math.max(0, all.size() - CHART_POINTS);
        StrategyDefinition d = active.definition();
        Map<String, List<BigDecimal>> lines = new LinkedHashMap<>();
        if (d.getType() == StrategyType.MA_CROSSOVER) {
            lines.put("shortAverage", new ArrayList<>());
            lines.put("longAverage", new ArrayList<>());
        } else {
            lines.put("upper", new ArrayList<>());
            lines.put("middle", new ArrayList<>());
            lines.put("lower", new ArrayList<>());
        }
        for (int i = from; i < all.size(); i++) {
            int end = i + 1;
            if (d.getType() == StrategyType.MA_CROSSOVER) {
                lines.get("shortAverage").add(end >= d.getShortPeriod() ? round(Averages.mean(all, end, d.getShortPeriod())) : null);
                lines.get("longAverage").add(end >= d.getLongPeriod() ? round(Averages.mean(all, end, d.getLongPeriod())) : null);
            } else if (end >= d.getPeriod()) {
                BigDecimal middle = Averages.mean(all, end, d.getPeriod());
                BigDecimal spread = Averages.standardDeviation(all, end, d.getPeriod(), middle).multiply(d.getMultiplier());
                lines.get("upper").add(round(middle.add(spread)));
                lines.get("middle").add(round(middle));
                lines.get("lower").add(round(middle.subtract(spread)));
            } else {
                lines.get("upper").add(null);
                lines.get("middle").add(null);
                lines.get("lower").add(null);
            }
        }
        List<BigDecimal> shown = all.subList(from, all.size()).stream().map(AutomationService::round).toList();
        return Optional.of(new StrategyChart(shown, lines));
    }

    /** What a demo did: the prices it walked through and the trigger it produced. */
    public record Simulation(int ticks, BigDecimal startPrice, BigDecimal finalPrice, SignalType trigger) {
    }

    /**
     * A demo of one running strategy: a scripted price path around the base price that ends in the crossing the customer
     * asked to see. The path is evaluated on its own and never added to the shared price history, so it cannot trigger
     * another customer's strategy. The order it produces is REAL: it goes through the Trade REST API like any other.
     *
     * @throws IllegalStateException if the strategy is not running, or the path did not produce the trigger
     */
    public Simulation simulate(String strategyId, SignalType direction, BigDecimal base) {
        Active active = find(strategyId);
        if (active == null) {
            throw new IllegalStateException("The strategy is not running");
        }
        if (direction == SignalType.NONE || base == null || base.signum() <= 0) {
            throw new IllegalArgumentException("direction must be BUY or SELL and the base price above zero");
        }
        List<BigDecimal> path = demoPath(active, direction, base);
        StrategySignal signal = active.strategy().evaluate(path);
        if (signal.type() != direction) {
            throw new IllegalStateException("The demo prices did not produce a " + direction + " trigger. With a very short Bollinger window (under about 10 prices) no single move can pass the band, so use a longer one.");
        }
        BigDecimal last = path.get(path.size() - 1);
        String basis = active.definition().getId() + "|demo|" + direction + "|" + clock.instant();
        fire(active, signal, last, UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8)), "demo price path: ");
        return new Simulation(path.size(), base, last, signal.type());
    }

    private static List<BigDecimal> demoPath(Active active, SignalType direction, BigDecimal base) {
        StrategyDefinition d = active.definition();
        int steady = active.strategy().requiredObservations();
        List<BigDecimal> path = new ArrayList<>();
        for (int i = 0; i < steady; i++) {
            if (d.getType() == StrategyType.BOLLINGER_BANDS && i < steady - 1) {
                // a little noise so the bands have a width; the last steady tick sits on the middle
                path.add(base.multiply(BigDecimal.ONE.add(new BigDecimal(i % 2 == 0 ? "0.001" : "-0.001"))));
            } else {
                path.add(base);
            }
        }
        boolean up = direction == SignalType.BUY ? d.getType() == StrategyType.MA_CROSSOVER : d.getType() == StrategyType.BOLLINGER_BANDS;
        BigDecimal move = new BigDecimal(d.getType() == StrategyType.MA_CROSSOVER ? "0.03" : "0.05");
        path.add(base.multiply(up ? BigDecimal.ONE.add(move) : BigDecimal.ONE.subtract(move)));
        return path;
    }

    private Active find(String strategyId) {
        for (List<Active> actives : bySymbol.values()) {
            for (Active active : actives) {
                if (active.definition().getId().equals(strategyId)) {
                    return active;
                }
            }
        }
        return null;
    }

    private static BigDecimal round(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private void submit(StrategyDefinition definition, String symbol, StrategySignal signal, PlaceOrderRequest request, Long eventId) {
        log.info("[AUTOMATION] order submission attempt strategy={} symbol={} accountId={} side={} quantity={} idempotencyKey={}",
                definition.getId(), symbol, definition.getAccountId(), request.getSide(), request.getQuantity(),
                request.getIdempotencyKey());
        TradeApiClient.SubmissionResult result;
        try {
            // One attempt. A failure is recorded and left alone: retrying could turn an unknown outcome into two orders.
            result = tradeApi.submit(request);
        } catch (RuntimeException e) {
            result = TradeApiClient.SubmissionResult.failed(e.getClass().getSimpleName());
        }

        Instant now = clock.instant();
        switch (result.status()) {
            case SUBMITTED -> {
                log.info("[AUTOMATION] event={} strategy={} symbol={} accountId={} trigger={} orderId={} at={}",
                        OrderOutcome.ORDER_SUBMITTED, definition.getId(), symbol, definition.getAccountId(),
                        signal.type(), result.orderId(), now);
                remember(definition, symbol, signal.type(), OrderOutcome.ORDER_SUBMITTED, result.orderId(), result.detail(), now);
                recordOutcome(eventId, OrderOutcome.ORDER_SUBMITTED, result.orderId(), null);
            }
            case REJECTED -> {
                log.warn("[AUTOMATION] event={} strategy={} symbol={} accountId={} trigger={} detail={} at={}",
                        OrderOutcome.ORDER_REJECTED, definition.getId(), symbol, definition.getAccountId(),
                        signal.type(), result.detail(), now);
                remember(definition, symbol, signal.type(), OrderOutcome.ORDER_REJECTED, null, result.detail(), now);
                recordOutcome(eventId, OrderOutcome.ORDER_REJECTED, null, result.detail());
            }
            case FAILED -> {
                log.error("[AUTOMATION] event={} strategy={} symbol={} accountId={} trigger={} detail={} at={}",
                        OrderOutcome.ORDER_SUBMISSION_FAILED, definition.getId(), symbol, definition.getAccountId(),
                        signal.type(), result.detail(), now);
                remember(definition, symbol, signal.type(), OrderOutcome.ORDER_SUBMISSION_FAILED, null, result.detail(), now);
                recordOutcome(eventId, OrderOutcome.ORDER_SUBMISSION_FAILED, null, result.detail());
            }
        }
    }

    private void remember(StrategyDefinition definition, String symbol, SignalType trigger, OrderOutcome outcome,
                          UUID orderId, String detail, Instant at) {
        recent.addFirst(new AutomationResult(definition.getId(), symbol, definition.getAccountId(), trigger, outcome,
                orderId, detail, at));
        while (recent.size() > RECENT_RESULTS) {
            recent.pollLast();
        }
    }

    /** The same trigger on the same observation always gets the same key, so the platform can never take it twice. */
    private static UUID keyFor(StrategyDefinition definition, String symbol, SignalType side, MarketQuote quote, int observations) {
        String basis = definition.getId() + "|" + symbol + "|" + side + "|"
                + (quote.getQuoteAsOf() != null ? quote.getQuoteAsOf().toString() : "n" + observations);
        return UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8));
    }
}
