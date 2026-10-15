package com.capstone.etp.assistant;

import com.capstone.etp.alerts.AlertResponse;
import com.capstone.etp.alerts.AlertService;
import com.capstone.etp.dto.OrderHistoryEntry;
import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.enums.OrderStatus;
import com.capstone.etp.portfolio.PnlResponse;
import com.capstone.etp.portfolio.PortfolioService;
import com.capstone.etp.portfolio.PortfolioSummaryResponse;
import com.capstone.etp.portfolio.PricedPositionResponse;
import com.capstone.etp.service.AccountService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Builds what the assistant may say, from the account's own data and nothing else: a plain sentence (used as the answer
 * when the model is not, and as the facts the model is given) and the cards that show the figures. Every figure on
 * screen comes from here. Each call reads through the existing services, so their authorisation still applies.
 */
@Service
public class AssistantDataService {

    /** What is known about one question: a summary sentence, the cards, and which datasets were used. */
    public record Grounding(String summary, List<DataCard> cards, List<String> citations) {
    }

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneOffset.UTC);

    private final AccountService accounts;
    private final PortfolioService portfolio;
    private final AlertService alerts;
    private final Clock clock;

    AssistantDataService(AccountService accounts, PortfolioService portfolio, AlertService alerts, Clock clock) {
        this.accounts = accounts;
        this.portfolio = portfolio;
        this.alerts = alerts;
        this.clock = clock;
    }

    public Grounding ground(Long accountId, AssistantIntent intent, String message) {
        return switch (intent) {
            case PORTFOLIO_PERFORMANCE -> performance(accountId);
            case TOP_MOVERS -> movers(accountId);
            case RECENT_TRANSACTIONS -> transactions(accountId, AssistantPolicy.requestedCount(message, 10, 50));
            case BUY_SELL_COUNT -> buySell(accountId);
            case OPEN_ORDERS -> openOrders(accountId);
            case STALE_PRICES -> stale(accountId);
            case PNL_SUMMARY -> pnl(accountId);
            case ACTIVITY_TREND -> trend(accountId);
            case POSITIONS -> positions(accountId);
            case ALERTS -> alertsOf(accountId);
            default -> overview(accountId);
        };
    }

    // ------------------------------------------------------------------ portfolio

    private Grounding performance(Long accountId) {
        PortfolioSummaryResponse s = portfolio.summary(accountId);
        String ccy = s.baseCurrency();
        BigDecimal allTime = nz(s.unrealisedPnl()).add(nz(s.realisedPnl()));
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Total value", money(s.totalValue(), ccy)));
        rows.add(List.of("Cash", money(s.cashBalance(), ccy)));
        rows.add(List.of("Holdings value", money(s.marketValue(), ccy)));
        rows.add(List.of("Today", s.dayChange() == null ? "Not available yet" : signed(s.dayChange(), ccy) + percent(s.dayChangePercent())));
        rows.add(List.of("All-time gain/loss", signed(allTime, ccy)));
        rows.add(List.of("  on holdings (unrealised)", signed(s.unrealisedPnl(), ccy) + percent(s.unrealisedPnlPercent())));
        rows.add(List.of("  from sales (realised)", signed(s.realisedPnl(), ccy)));
        String today = s.dayChange() == null ? "Today's change is not available yet." : "Today your portfolio is " + signed(s.dayChange(), ccy) + percent(s.dayChangePercent()) + ".";
        String summary = "Your portfolio is worth " + money(s.totalValue(), ccy) + " (cash " + money(s.cashBalance(), ccy) + ", holdings " + money(s.marketValue(), ccy) + "). "
                + today + " All-time you are " + signed(allTime, ccy) + " (" + signed(s.unrealisedPnl(), ccy) + " on holdings, " + signed(s.realisedPnl(), ccy) + " from sales)."
                + (s.partial() ? " Some holdings have no price yet, so the totals leave them out." : "");
        return new Grounding(summary, List.of(DataCard.metrics("Portfolio performance (" + ccy + ")", rows)), List.of("portfolio summary"));
    }

    private Grounding movers(Long accountId) {
        List<PricedPositionResponse> priced = portfolio.positions(accountId, null).stream()
                .filter(p -> p.unrealisedPnlPercent() != null && p.unrealisedPnl() != null).toList();
        List<PricedPositionResponse> gainers = priced.stream().filter(p -> p.unrealisedPnl().signum() > 0)
                .sorted(Comparator.comparing(PricedPositionResponse::unrealisedPnlPercent).reversed()).limit(5).toList();
        List<PricedPositionResponse> losers = priced.stream().filter(p -> p.unrealisedPnl().signum() < 0)
                .sorted(Comparator.comparing(PricedPositionResponse::unrealisedPnlPercent)).limit(5).toList();
        List<DataCard> cards = new ArrayList<>();
        cards.add(DataCard.table("Top gainers", List.of("Symbol", "Gain", "%"), gainers.stream()
                .map(p -> List.of(p.symbol(), signed(p.unrealisedPnl(), p.currency()), percent(p.unrealisedPnlPercent()).trim())).toList()));
        cards.add(DataCard.table("Top losers", List.of("Symbol", "Loss", "%"), losers.stream()
                .map(p -> List.of(p.symbol(), signed(p.unrealisedPnl(), p.currency()), percent(p.unrealisedPnlPercent()).trim())).toList()));
        String summary = priced.isEmpty() ? "You have no priced holdings to rank right now."
                : "Among your priced holdings, " + (gainers.isEmpty() ? "none is up" : gainers.get(0).symbol() + " is the biggest gainer at " + percent(gainers.get(0).unrealisedPnlPercent()).trim())
                + "; " + (losers.isEmpty() ? "none is down." : losers.get(0).symbol() + " is the biggest loser at " + percent(losers.get(0).unrealisedPnlPercent()).trim() + ".");
        return new Grounding(summary, cards, List.of("portfolio positions"));
    }

    private Grounding stale(Long accountId) {
        List<PricedPositionResponse> flagged = portfolio.positions(accountId, null).stream()
                .filter(p -> p.lastPrice() == null || p.stale()).toList();
        DataCard card = DataCard.table("Stale or unpriced holdings", List.of("Symbol", "Quantity", "Last price", "Price as of"), flagged.stream()
                .map(p -> List.of(p.symbol(), String.valueOf(p.quantity()), p.lastPrice() == null ? "No price" : plain(p.lastPrice()),
                        p.priceAsOf() == null ? "Never" : WHEN.format(p.priceAsOf()))).toList());
        long unpriced = flagged.stream().filter(p -> p.lastPrice() == null).count();
        String summary = flagged.isEmpty() ? "All your holdings have a fresh price."
                : flagged.size() + " of your holdings " + (flagged.size() == 1 ? "has" : "have") + " an old or missing price (" + unpriced + " with no price at all).";
        return new Grounding(summary, List.of(card), List.of("portfolio positions"));
    }

    private Grounding pnl(Long accountId) {
        PnlResponse p = portfolio.pnl(accountId, null, null, true);
        String ccy = p.baseCurrency();
        List<DataCard> cards = new ArrayList<>();
        cards.add(DataCard.metrics("Profit and loss (" + ccy + ")", List.of(
                List.of("Realised (from sales)", signed(p.realisedPnl(), ccy)),
                List.of("Unrealised (on holdings)", signed(p.unrealisedPnl(), ccy)),
                List.of("Total", signed(p.totalPnl(), ccy)))));
        if (p.bySymbol() != null && !p.bySymbol().isEmpty()) {
            cards.add(DataCard.table("By instrument", List.of("Symbol", "Realised", "Unrealised", "Total"), p.bySymbol().stream()
                    .map(s -> List.of(s.symbol(), signed(s.realisedPnl(), ccy), signed(s.unrealisedPnl(), ccy), signed(s.totalPnl(), ccy))).toList()));
        }
        String summary = "Realised profit and loss (from sales) is " + signed(p.realisedPnl(), ccy) + " and unrealised (on what you hold now) is "
                + signed(p.unrealisedPnl(), ccy) + ", " + signed(p.totalPnl(), ccy) + " in total.";
        return new Grounding(summary, cards, List.of("portfolio pnl"));
    }

    private Grounding positions(Long accountId) {
        List<PricedPositionResponse> held = portfolio.positions(accountId, null);
        DataCard card = DataCard.table("Your positions", List.of("Symbol", "Qty", "Avg cost", "Last", "Value", "Gain/loss"), held.stream()
                .map(p -> List.of(p.symbol(), String.valueOf(p.quantity()), plain(p.averageCost()), p.lastPrice() == null ? "No price" : plain(p.lastPrice()),
                        p.marketValue() == null ? "-" : plain(p.marketValue()), p.unrealisedPnl() == null ? "-" : signed(p.unrealisedPnl(), "").trim())).toList());
        String summary = held.isEmpty() ? "You do not hold any positions yet." : "You hold " + held.size() + " position" + (held.size() == 1 ? "" : "s") + ": "
                + String.join(", ", held.stream().map(PricedPositionResponse::symbol).limit(8).toList()) + (held.size() > 8 ? " and more." : ".");
        return new Grounding(summary, List.of(card), List.of("portfolio positions"));
    }

    // ------------------------------------------------------------------ orders

    private List<OrderHistoryEntry> orders(Long accountId) {
        return accounts.getOrders(accountId, null, null, null).stream()
                .filter(o -> o.getCreatedOn() != null)
                .sorted(Comparator.comparing(OrderHistoryEntry::getCreatedOn).reversed()).toList();
    }

    private Grounding transactions(Long accountId, int count) {
        List<OrderHistoryEntry> latest = orders(accountId).stream().limit(count).toList();
        DataCard card = DataCard.table("Last " + latest.size() + " transactions", List.of("When", "Symbol", "Side", "Qty", "Price", "Status"), latest.stream()
                .map(o -> List.of(WHEN.format(o.getCreatedOn()), o.getSymbol(), String.valueOf(o.getSide()), String.valueOf(o.getQuantity()),
                        o.getExecutedPrice() != null ? plain(o.getExecutedPrice()) : o.getPrice() != null ? plain(o.getPrice()) : "Market", String.valueOf(o.getStatus()))).toList());
        String summary = latest.isEmpty() ? "You have not placed any orders yet."
                : "Here are your " + latest.size() + " most recent orders. The latest is " + describe(latest.get(0)) + ".";
        return new Grounding(summary, List.of(card), List.of("orders"));
    }

    private Grounding buySell(Long accountId) {
        Instant since = clock.instant().minus(Duration.ofDays(7));
        List<OrderHistoryEntry> week = orders(accountId).stream().filter(o -> !o.getCreatedOn().isBefore(since)).toList();
        long buys = week.stream().filter(o -> o.getSide() == OrderSide.BUY).count();
        long sells = week.stream().filter(o -> o.getSide() == OrderSide.SELL).count();
        long buysFilled = week.stream().filter(o -> o.getSide() == OrderSide.BUY && o.getStatus() == OrderStatus.FILLED).count();
        long sellsFilled = week.stream().filter(o -> o.getSide() == OrderSide.SELL && o.getStatus() == OrderStatus.FILLED).count();
        DataCard card = DataCard.table("Orders in the last 7 days", List.of("Side", "Placed", "Filled"), List.of(
                List.of("BUY", String.valueOf(buys), String.valueOf(buysFilled)),
                List.of("SELL", String.valueOf(sells), String.valueOf(sellsFilled))));
        String summary = week.isEmpty() ? "You have not placed any orders in the last 7 days."
                : "In the last 7 days you placed " + buys + " BUY and " + sells + " SELL order" + (buys + sells == 1 ? "" : "s") + ".";
        return new Grounding(summary, List.of(card), List.of("orders"));
    }

    private Grounding openOrders(Long accountId) {
        Instant since = clock.instant().minus(Duration.ofDays(7));
        List<OrderHistoryEntry> all = orders(accountId);
        List<OrderHistoryEntry> working = all.stream().filter(o -> o.getStatus() == OrderStatus.NEW).toList();
        List<OrderHistoryEntry> rejected = all.stream().filter(o -> o.getStatus() == OrderStatus.REJECTED && !o.getCreatedOn().isBefore(since)).toList();
        List<OrderHistoryEntry> shown = new ArrayList<>(working);
        shown.addAll(rejected);
        DataCard card = DataCard.table("Working and recently rejected orders", List.of("When", "Symbol", "Side", "Qty", "Limit", "Status"), shown.stream()
                .map(o -> List.of(WHEN.format(o.getCreatedOn()), o.getSymbol(), String.valueOf(o.getSide()), String.valueOf(o.getQuantity()),
                        o.getPrice() == null ? "Market" : plain(o.getPrice()), String.valueOf(o.getStatus()))).toList());
        String summary = shown.isEmpty() ? "You have no working orders and nothing was rejected in the last 7 days."
                : "You have " + working.size() + " working (NEW) order" + (working.size() == 1 ? "" : "s") + " and " + rejected.size() + " rejected in the last 7 days.";
        return new Grounding(summary, List.of(card), List.of("orders"));
    }

    private Grounding trend(Long accountId) {
        Instant now = clock.instant();
        List<OrderHistoryEntry> month = orders(accountId).stream().filter(o -> !o.getCreatedOn().isBefore(now.minus(Duration.ofDays(30)))).toList();
        List<List<String>> rows = new ArrayList<>();
        // five buckets of six days, oldest first
        for (int index = 4; index >= 0; index--) {
            final int bucket = index;
            Instant to = now.minus(Duration.ofDays(6L * bucket));
            Instant from = to.minus(Duration.ofDays(6));
            List<OrderHistoryEntry> inBucket = month.stream().filter(o -> !o.getCreatedOn().isBefore(from) && o.getCreatedOn().isBefore(bucket == 0 ? to.plusSeconds(1) : to)).toList();
            rows.add(List.of(WHEN.format(from).substring(0, WHEN.format(from).indexOf(',')) + " to " + WHEN.format(to).substring(0, WHEN.format(to).indexOf(',')),
                    String.valueOf(inBucket.size()),
                    String.valueOf(inBucket.stream().filter(o -> o.getSide() == OrderSide.BUY).count()),
                    String.valueOf(inBucket.stream().filter(o -> o.getSide() == OrderSide.SELL).count())));
        }
        DataCard card = DataCard.table("Orders per 6 days, last 30 days", List.of("Period", "Orders", "Buy", "Sell"), rows);
        String summary = month.isEmpty() ? "You placed no orders in the last 30 days."
                : "You placed " + month.size() + " order" + (month.size() == 1 ? "" : "s") + " in the last 30 days.";
        return new Grounding(summary, List.of(card), List.of("orders"));
    }

    private Grounding alertsOf(Long accountId) {
        List<AlertResponse> list = alerts.list(accountId);
        DataCard card = DataCard.table("Your price alerts", List.of("Symbol", "Condition", "Price", "State", "Fired"), list.stream()
                .map(a -> List.of(a.symbol(), String.valueOf(a.condition()), plain(a.price()), a.enabled() ? "Enabled" : "Disabled", String.valueOf(a.triggerCount()))).toList());
        long enabled = list.stream().filter(AlertResponse::enabled).count();
        String summary = list.isEmpty() ? "You have no price alerts." : "You have " + list.size() + " price alert" + (list.size() == 1 ? "" : "s") + ", " + enabled + " enabled.";
        return new Grounding(summary, List.of(card), List.of("alerts"));
    }

    /** For a question with no dedicated answer: a short overview the model can draw on, and no cards. */
    private Grounding overview(Long accountId) {
        PortfolioSummaryResponse s = portfolio.summary(accountId);
        List<OrderHistoryEntry> latest = orders(accountId).stream().limit(5).toList();
        String summary = "Account overview: total value " + money(s.totalValue(), s.baseCurrency()) + ", cash " + money(s.cashBalance(), s.baseCurrency())
                + ", " + s.positionCount() + " holding" + (s.positionCount() == 1 ? "" : "s") + ". Latest orders: "
                + (latest.isEmpty() ? "none" : String.join("; ", latest.stream().map(AssistantDataService::describe).toList())) + ".";
        return new Grounding(summary, List.of(), List.of("portfolio summary", "orders"));
    }

    // ------------------------------------------------------------------ formatting

    private static String describe(OrderHistoryEntry o) {
        return o.getSide() + " " + o.getQuantity() + " " + o.getSymbol() + " (" + o.getStatus() + ", " + WHEN.format(o.getCreatedOn()) + ")";
    }

    private static BigDecimal nz(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String money(BigDecimal value, String currency) {
        return plain(value) + (currency == null || currency.isBlank() ? "" : " " + currency);
    }

    private static String signed(BigDecimal value, String currency) {
        BigDecimal v = nz(value);
        return (v.signum() > 0 ? "+" : "") + money(v, currency);
    }

    private static String percent(BigDecimal value) {
        return value == null ? "" : " (" + (value.signum() > 0 ? "+" : "") + value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%)";
    }
}
