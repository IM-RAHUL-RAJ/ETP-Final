package com.capstone.etp.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** portfolio_realised_pnl in memory: event_id is the primary key, so booking the same event twice does nothing. */
class InMemoryRealisedLedger implements RealisedPnlMapper {

    record Row(UUID eventId, Long accountId, String symbol, int quantity, BigDecimal executedPrice,
               BigDecimal averageCost, String baseCurrency, BigDecimal realisedPnl, Instant bookedAt) {
    }

    final Map<UUID, Row> rows = new LinkedHashMap<>();

    @Override
    public int insertIfAbsent(UUID eventId, Long accountId, String symbol, int quantity, BigDecimal executedPrice,
                              BigDecimal averageCost, String baseCurrency, BigDecimal realisedPnl, Instant bookedAt) {
        if (rows.containsKey(eventId)) {
            return 0;
        }
        rows.put(eventId, new Row(eventId, accountId, symbol, quantity, executedPrice, averageCost,
                baseCurrency, realisedPnl, bookedAt));
        return 1;
    }

    @Override
    public BigDecimal sumRealised(Long accountId, Instant from, Instant to) {
        return rows.values().stream()
                .filter(r -> r.accountId().equals(accountId))
                .filter(r -> from == null || !r.bookedAt().isBefore(from))
                .filter(r -> to == null || r.bookedAt().isBefore(to))
                .map(Row::realisedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public List<SymbolRealised> sumRealisedBySymbol(Long accountId, Instant from, Instant to) {
        return new ArrayList<>();
    }

    @Override
    public int ping() {
        return 1;
    }
}
