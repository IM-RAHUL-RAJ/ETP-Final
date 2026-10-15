package com.capstone.etp.notifications;

import com.capstone.etp.preferences.AlertChannel;
import com.capstone.etp.preferences.ChannelResolver;
import com.capstone.etp.preferences.PreferencesUnavailableException;
import com.capstone.etp.preferences.ResolvedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Slf4j
@Component
class NotificationDispatcher {

    static final String REASON_NO_CONTACT = "NO_CONTACT";
    static final String REASON_PREFERENCES_UNAVAILABLE = "PREFERENCES_UNAVAILABLE";
    static final String REASON_PROVIDER_ERROR = "PROVIDER_ERROR";
    static final String REASON_STUB = "STUB_DELIVERY";
    private static final int LEASE_SECONDS = 120;
    private static final int MAX_BACKOFF_SECONDS = 3_600;

    private final NotificationMapper mapper;
    private final ChannelResolver resolver;
    private final Map<AlertChannel, ChannelSender> senders = new EnumMap<>(AlertChannel.class);
    private final int batchSize;
    private final int maxAttempts;
    private final int retryBackoffSeconds;
    private final int heldRetrySeconds;

    NotificationDispatcher(NotificationMapper mapper,
                           ChannelResolver resolver,
                           List<ChannelSender> channelSenders,
                           @Value("${app.notifications.batch-size:50}") int batchSize,
                           @Value("${app.notifications.max-attempts:5}") int maxAttempts,
                           @Value("${app.notifications.retry-backoff-seconds:30}") int retryBackoffSeconds,
                           @Value("${app.notifications.held-retry-seconds:300}") int heldRetrySeconds) {
        this.mapper = mapper;
        this.resolver = resolver;
        channelSenders.forEach(sender -> senders.put(sender.channel(), sender));
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.retryBackoffSeconds = retryBackoffSeconds;
        this.heldRetrySeconds = heldRetrySeconds;
    }

    @Scheduled(fixedDelayString = "${app.notifications.dispatch-interval-ms:2000}", initialDelay = 5_000)
    void dispatchDue() {
        List<Notification> due;
        try {
            due = mapper.claimDue(batchSize, LEASE_SECONDS);
        } catch (RuntimeException e) {
            log.warn("[NOTIFY] could not read due notifications: {}", e.getMessage());
            return;
        }
        for (Notification notification : due) {
            try {
                dispatch(notification);
            } catch (RuntimeException e) {
                // One bad row must not stop the rest; its lease expires and it is retried.
                log.error("[NOTIFY] dispatch failed notificationId={}: {}", notification.getId(), e.getMessage());
            }
        }
    }

    /** Sends one notification. Package-visible so the behaviour can be tested without a scheduler. */
    void dispatch(Notification notification) {
        if (!resolver.wants(notification.getAccountId(), notification.getKind().name())) {
            mapper.markSkipped(notification.getId(), "OPTED_OUT");
            log.info("[NOTIFY] notificationId={} not sent: the customer switched this kind off", notification.getId());
            return;
        }
        Optional<ResolvedChannel> resolved;
        try {
            resolved = resolver.resolve(notification.getAccountId());
        } catch (PreferencesUnavailableException e) {
            mapper.reschedule(notification.getId(), NotificationStatus.QUEUED, REASON_PREFERENCES_UNAVAILABLE,
                    null, null, backoff(notification.getAttempts() + 1));
            log.warn("[NOTIFY] preferences unavailable, notificationId={} kept queued", notification.getId());
            return;
        }

        if (resolved.isEmpty()) {
            mapper.reschedule(notification.getId(), NotificationStatus.HELD, REASON_NO_CONTACT,
                    null, null, heldRetrySeconds);
            log.warn("[NOTIFY] no usable contact for notificationId={}, held", notification.getId());
            return;
        }

        ResolvedChannel channel = resolved.get();
        ChannelSender sender = senders.get(channel.channel());
        if (sender == null) {
            mapper.markFailed(notification.getId(), "NO_SENDER", channel.channel(), channel.source());
            return;
        }

        try {
            ChannelSender.Mode mode = sender.send(
                    new OutboundMessage(channel.destination(), notification.getSubject(), notification.getBody(),
                            notification.getKind(), resolver.mascotOf(notification.getAccountId())));
            mapper.markSent(notification.getId(), channel.channel(), channel.source(),
                    mode == ChannelSender.Mode.STUB ? REASON_STUB : null);
            log.info("[NOTIFY] sent notificationId={} channel={} source={} mode={}",
                    notification.getId(), channel.channel(), channel.source(), mode);
        } catch (ChannelSender.DeliveryException | RuntimeException e) {
            int attempts = notification.getAttempts() + 1;
            if (attempts >= maxAttempts) {
                mapper.markFailed(notification.getId(), REASON_PROVIDER_ERROR, channel.channel(), channel.source());
                log.error("[NOTIFY] giving up notificationId={} after {} attempts ({})", notification.getId(), attempts,
                        e instanceof ChannelSender.DeliveryException ? e.getMessage() : e.getClass().getSimpleName());
            } else {
                mapper.reschedule(notification.getId(), NotificationStatus.QUEUED, REASON_PROVIDER_ERROR,
                        channel.channel(), channel.source(), backoff(attempts));
                log.warn("[NOTIFY] send failed notificationId={} attempt={} ({}); will retry",
                        notification.getId(), attempts, e.getClass().getSimpleName());
            }
        }
    }

    private int backoff(int attempt) {
        long delay = (long) retryBackoffSeconds * (1L << Math.min(Math.max(attempt - 1, 0), 10));
        return (int) Math.min(delay, MAX_BACKOFF_SECONDS);
    }
}
