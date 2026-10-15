package com.capstone.etp.portfolio;

import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.security.AccountAuthorizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Value over time. Each read takes a fresh valuation and records it (at most every ten minutes), so the
 * history is made of what the customer actually saw. When prices are down nothing is recorded and the
 * past is still shown.
 */
@Slf4j
@Service
public class PortfolioHistoryService {

    static final Duration DAY = Duration.ofHours(24);

    private final PortfolioService portfolio;
    private final PortfolioSnapshotMapper snapshots;
    private final AccountAuthorizer authorizer;
    private final Clock clock;

    PortfolioHistoryService(PortfolioService portfolio, PortfolioSnapshotMapper snapshots,
                            AccountAuthorizer authorizer, Clock clock) {
        this.portfolio = portfolio;
        this.snapshots = snapshots;
        this.authorizer = authorizer;
        this.clock = clock;
    }

    public PortfolioHistoryResponse history(Long accountId, String rawRange) {
        authorizer.ensureOwnAccount(accountId);
        String range = rawRange == null ? "30d" : rawRange.trim().toLowerCase(Locale.ROOT);
        Duration window = windowOf(range);
        Instant now = clock.instant();

        PortfolioSummaryResponse current = null;
        try {
            current = portfolio.summary(accountId);
            snapshots.recordIfDue(accountId, current.totalValue(), current.baseCurrency());
        } catch (PricingUnavailableException e) {
            log.debug("[PORTFOLIO] no prices, history shown without a new point");
        } catch (RuntimeException e) {
            log.warn("[PORTFOLIO] could not record a history point: {}", e.getClass().getSimpleName());
        }

        List<PortfolioHistoryResponse.Point> points = snapshots.findSince(accountId, now.minus(window)).stream()
                .map(s -> new PortfolioHistoryResponse.Point(s.getTakenAt(), s.getTotalValue()))
                .toList();

        return new PortfolioHistoryResponse(accountId, current == null ? null : current.baseCurrency(), range, points,
                current == null ? null : dayChangeOf(current, accountId, now));
    }

    /** Prefer the real thing: holdings now against yesterday close. Without previous closes, fall back to the recorded history. */
    private PortfolioHistoryResponse.DayChange dayChangeOf(PortfolioSummaryResponse current, Long accountId, Instant now) {
        if (current.dayChange() != null) {
            return new PortfolioHistoryResponse.DayChange(current.dayChange(), current.dayChangePercent(), null);
        }
        return snapshotDayChange(accountId, current.totalValue(), now);
    }

    private PortfolioHistoryResponse.DayChange snapshotDayChange(Long accountId, BigDecimal total, Instant now) {
        PortfolioSnapshotMapper.Snapshot before = snapshots.findAtOrBefore(accountId, now.minus(DAY));
        if (before == null || before.getTotalValue() == null || before.getTotalValue().signum() == 0) {
            return null;
        }
        BigDecimal funds = snapshots.netFundsSince(accountId, before.getTakenAt());
        BigDecimal change = total.subtract(before.getTotalValue()).subtract(funds == null ? BigDecimal.ZERO : funds)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal percent = change.multiply(BigDecimal.valueOf(100))
                .divide(before.getTotalValue(), 2, RoundingMode.HALF_UP);
        return new PortfolioHistoryResponse.DayChange(change, percent, before.getTakenAt());
    }

    static Duration windowOf(String range) {
        return switch (range) {
            case "30d" -> Duration.ofDays(30);
            case "3m" -> Duration.ofDays(92);
            case "1y" -> Duration.ofDays(366);
            default -> throw new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "range must be 30d, 3m or 1y");
        };
    }
}
