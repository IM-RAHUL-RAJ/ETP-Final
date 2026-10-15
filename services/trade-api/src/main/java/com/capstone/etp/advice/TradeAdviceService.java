package com.capstone.etp.advice;

import com.capstone.etp.dto.PositionResponse;
import com.capstone.etp.dto.WatchlistEntry;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.service.AccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Research advice for what the customer holds and watches. Advisory only: nothing here places an order.
 * It reads the customer's own holdings and watchlist (the account must be one of the token's own accounts),
 * analyses each instrument once, and returns one piece of advice per instrument.
 */
@Slf4j
@Service
public class TradeAdviceService {

    /** How many daily closes to read: enough for the rules and for a useful chart. */
    static final int CHART_HISTORY = 90;

    private final AccountAuthorizer authorizer;
    private final AccountService accounts;
    private final PriceHistoryProvider history;
    private final AdviceStrategy strategy;
    private final Clock clock;

    TradeAdviceService(AccountAuthorizer authorizer, AccountService accounts, PriceHistoryProvider history,
                       AdviceStrategy strategy, Clock clock) {
        this.authorizer = authorizer;
        this.accounts = accounts;
        this.history = history;
        this.strategy = strategy;
        this.clock = clock;
    }

    /** Advice for every distinct instrument in the account's holdings and watchlist. */
    public List<TradeAdvice> adviceFor(Long requestedAccountId) {
        Long accountId = authorizer.resolveOwnAccount(requestedAccountId);
        List<TradeAdvice> result = new ArrayList<>();
        for (String symbol : instruments(accountId)) {
            result.add(analyse(symbol));
        }
        log.info("[ADVICE] accountId={} instruments={}", accountId, result.size());
        return result;
    }

    /** Advice for one instrument, which must be one the customer holds or watches. */
    public TradeAdvice adviceFor(Long requestedAccountId, String rawSymbol) {
        Long accountId = authorizer.resolveOwnAccount(requestedAccountId);
        String symbol = rawSymbol == null ? "" : rawSymbol.trim().toUpperCase(Locale.ROOT);
        if (!instruments(accountId).contains(symbol)) {
            throw new ModuleException(HttpStatus.NOT_FOUND, "ADV-404",
                    "That instrument is not in your holdings or your watchlist");
        }
        return analyse(symbol);
    }

    /** Holdings first, then the watchlist, each instrument once. */
    private Set<String> instruments(Long accountId) {
        Set<String> symbols = new LinkedHashSet<>();
        for (PositionResponse position : accounts.getPositions(accountId)) {
            if (position.getSymbol() != null && position.getQuantity() != null && position.getQuantity() > 0) {
                symbols.add(position.getSymbol().trim().toUpperCase(Locale.ROOT));
            }
        }
        for (WatchlistEntry entry : accounts.getWatchlist(accountId)) {
            if (entry.getSymbol() != null) {
                symbols.add(entry.getSymbol().trim().toUpperCase(Locale.ROOT));
            }
        }
        return symbols;
    }

    private TradeAdvice analyse(String symbol) {
        // The chart wants more history than the rules need, so the signal uses the same window the chart shows.
        List<PricePoint> points = history.points(symbol, Math.max(strategy.requiredObservations(), CHART_HISTORY));
        List<BigDecimal> closes = points.stream().map(PricePoint::close).toList();
        return strategy.evaluate(symbol, closes, clock.instant()).withMarket(strategy.market(points));
    }
}
