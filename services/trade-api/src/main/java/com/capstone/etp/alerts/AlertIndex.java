package com.capstone.etp.alerts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


@Slf4j
@Component
class AlertIndex {

    /** An armed alert, held in memory so a quote does not need a database read. */
    record Entry(long id, long accountId, String symbol, AlertOperator operator, BigDecimal threshold) {
    }

    private final AlertMapper mapper;
    private volatile Map<String, List<Entry>> bySymbol = new ConcurrentHashMap<>();

    AlertIndex(AlertMapper mapper) {
        this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    void loadAtStartup() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${app.alerts.index-refresh-ms:30000}", initialDelay = 30_000)
    void refresh() {
        try {
            Map<String, List<Entry>> fresh = new ConcurrentHashMap<>();
            for (PriceAlert alert : mapper.findAllActive()) {
                fresh.computeIfAbsent(alert.getSymbol(), key -> new CopyOnWriteArrayList<>())
                        .add(toEntry(alert));
            }
            bySymbol = fresh;
            log.debug("[ALERTS] index rebuilt: {} symbols", fresh.size());
        } catch (RuntimeException e) {
            // Keep serving the index we have; the next refresh will try again.
            log.warn("[ALERTS] index refresh failed, keeping the current one: {}", e.getMessage());
        }
    }

    /** Adds an alert when it is armed; an alert that is switched off is never in the index. */
    void add(PriceAlert alert) {
        if (alert.isEnabled() && alert.getStatus() == AlertStatus.ACTIVE) {
            bySymbol.computeIfAbsent(alert.getSymbol(), key -> new CopyOnWriteArrayList<>()).add(toEntry(alert));
        }
    }

    void remove(String symbol, long id) {
        List<Entry> entries = bySymbol.get(symbol);
        if (entries != null) {
            entries.removeIf(entry -> entry.id() == id);
        }
    }

    /** Drops the alert wherever it is indexed, then indexes it again from its current row if it is armed. */
    void replace(PriceAlert before, PriceAlert after) {
        if (before != null) {
            remove(before.getSymbol(), before.getId());
        }
        if (after != null) {
            add(after);
        }
    }

    List<Entry> forSymbol(String symbol) {
        List<Entry> entries = bySymbol.get(symbol);
        return entries == null || entries.isEmpty() ? List.of() : new ArrayList<>(entries);
    }

    int size() {
        return bySymbol.values().stream().mapToInt(List::size).sum();
    }

    private static Entry toEntry(PriceAlert alert) {
        return new Entry(alert.getId(), alert.getAccountId(), alert.getSymbol(),
                alert.getOperator(), alert.getThreshold());
    }
}
