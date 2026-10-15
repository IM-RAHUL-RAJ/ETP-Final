package com.capstone.etp.notifications;

import com.capstone.etp.preferences.AlertChannel;
import com.capstone.etp.preferences.ChannelSource;

import java.time.Instant;

/**
 * One notification as the inbox shows it. {@code channel} and {@code channelSource} are what the
 * preferences module resolved when the message was sent (null while it is still waiting), which is
 * what makes "it went out on the channel preferences holds" something a record shows.
 * The destination is not part of it: it is not stored.
 */
public record NotificationResponse(
        Long id,
        NotificationKind kind,
        String subject,
        String body,
        NotificationStatus status,
        String statusReason,
        AlertChannel channel,
        ChannelSource channelSource,
        Instant createdAt,
        Instant sentAt,
        boolean read) {

    static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getKind(),
                n.getSubject(),
                n.getBody(),
                n.getStatus(),
                n.getStatusReason(),
                n.getResolvedChannel(),
                n.getChannelSource(),
                n.getCreatedAt(),
                n.getSentAt(),
                n.getReadAt() != null);
    }
}
