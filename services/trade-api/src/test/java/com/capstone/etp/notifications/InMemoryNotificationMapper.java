package com.capstone.etp.notifications;

import com.capstone.etp.preferences.AlertChannel;
import com.capstone.etp.preferences.ChannelSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An in-memory ledger with the same rules as the table: {@code event_id} is unique, so a second insert
 * of the same event does nothing. It lets the idempotency and state-machine tests run without a database.
 */
class InMemoryNotificationMapper implements NotificationMapper {

    final Map<Long, Notification> rows = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(100);

    @Override
    public int insertIfAbsent(UUID eventId, Long accountId, NotificationKind kind, String subject, String body) {
        if (rows.values().stream().anyMatch(n -> n.getEventId().equals(eventId))) {
            return 0;
        }
        Notification n = new Notification();
        n.setId(sequence.incrementAndGet());
        n.setEventId(eventId);
        n.setAccountId(accountId);
        n.setKind(kind);
        n.setSubject(subject);
        n.setBody(body);
        n.setStatus(NotificationStatus.QUEUED);
        n.setCreatedAt(Instant.now());
        rows.put(n.getId(), n);
        return 1;
    }

    @Override
    public Long findIdByEventId(UUID eventId) {
        return rows.values().stream().filter(n -> n.getEventId().equals(eventId))
                .map(Notification::getId).findFirst().orElse(null);
    }

    @Override
    public List<Notification> claimDue(int limit, int leaseSeconds) {
        return rows.values().stream()
                .filter(n -> n.getStatus() == NotificationStatus.QUEUED || n.getStatus() == NotificationStatus.HELD)
                .limit(limit).toList();
    }

    @Override
    public int markSent(Long id, AlertChannel channel, ChannelSource source, String reason) {
        Notification n = rows.get(id);
        n.setStatus(NotificationStatus.SENT);
        n.setStatusReason(reason);
        n.setResolvedChannel(channel);
        n.setChannelSource(source);
        n.setAttempts(n.getAttempts() + 1);
        n.setSentAt(Instant.now());
        return 1;
    }

    @Override
    public int reschedule(Long id, NotificationStatus status, String reason, AlertChannel channel,
                          ChannelSource source, int delaySeconds) {
        Notification n = rows.get(id);
        n.setStatus(status);
        n.setStatusReason(reason);
        n.setResolvedChannel(channel);
        n.setChannelSource(source);
        n.setAttempts(n.getAttempts() + 1);
        return 1;
    }

    @Override
    public int markFailed(Long id, String reason, AlertChannel channel, ChannelSource source) {
        Notification n = rows.get(id);
        n.setStatus(NotificationStatus.FAILED);
        n.setStatusReason(reason);
        n.setResolvedChannel(channel);
        n.setChannelSource(source);
        n.setAttempts(n.getAttempts() + 1);
        return 1;
    }

    @Override
    public List<Notification> findByAccount(Long accountId, boolean unreadOnly, int limit) {
        List<Notification> found = new ArrayList<>(rows.values().stream()
                .filter(n -> n.getAccountId().equals(accountId))
                .filter(n -> !unreadOnly || n.getReadAt() == null)
                .toList());
        found.sort(Comparator.comparing(Notification::getId).reversed());
        return found.stream().limit(limit).toList();
    }

    @Override
    public Notification findByIdAndAccount(Long id, Long accountId) {
        Notification n = rows.get(id);
        return n != null && n.getAccountId().equals(accountId) ? n : null;
    }

    @Override
    public int countUnread(Long accountId) {
        return (int) rows.values().stream()
                .filter(n -> n.getAccountId().equals(accountId) && n.getReadAt() == null).count();
    }

    @Override
    public int markRead(Long id, Long accountId) {
        Notification n = findByIdAndAccount(id, accountId);
        if (n == null) {
            return 0;
        }
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
        }
        return 1;
    }

    @Override
    public List<Notification> findByAccountAndType(Long accountId, boolean unreadOnly, String type, int limit) {
        return findByAccount(accountId, unreadOnly, Integer.MAX_VALUE).stream()
                .filter(n -> switch (type) {
                    case "price_alert" -> n.getKind() == NotificationKind.PRICE_ALERT;
                    case "order" -> n.getKind().isOrder();
                    case "funds" -> n.getKind() == NotificationKind.FUNDS;
                    default -> true;
                })
                .limit(limit).toList();
    }

    @Override
    public Map<String, Long> countByGroup(Long accountId) {
        List<Notification> mine = findByAccount(accountId, false, Integer.MAX_VALUE);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("total", (long) mine.size());
        counts.put("price_alert", mine.stream().filter(n -> n.getKind() == NotificationKind.PRICE_ALERT).count());
        counts.put("orders", mine.stream().filter(n -> n.getKind().isOrder()).count());
        counts.put("funds", mine.stream().filter(n -> n.getKind() == NotificationKind.FUNDS).count());
        return counts;
    }

    @Override
    public List<Notification> findStatusesByIds(java.util.Collection<Long> ids) {
        return ids.stream().map(rows::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public int markSkipped(Long id, String reason) {
        Notification n = rows.get(id);
        n.setStatus(NotificationStatus.SKIPPED);
        n.setStatusReason(reason);
        return 1;
    }

    @Override
    public int markAllRead(Long accountId) {
        int changed = 0;
        for (Notification n : rows.values()) {
            if (n.getAccountId().equals(accountId) && n.getReadAt() == null) {
                n.setReadAt(Instant.now());
                changed++;
            }
        }
        return changed;
    }
}
