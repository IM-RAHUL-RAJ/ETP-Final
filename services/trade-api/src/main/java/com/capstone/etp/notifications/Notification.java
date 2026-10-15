package com.capstone.etp.notifications;

import com.capstone.etp.preferences.AlertChannel;
import com.capstone.etp.preferences.ChannelSource;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;


@Data
public class Notification {
    private Long id;
    private UUID eventId;
    private Long accountId;
    private NotificationKind kind;
    private String subject;
    private String body;
    private NotificationStatus status;
    private String statusReason;
    private AlertChannel resolvedChannel;
    private ChannelSource channelSource;
    private int attempts;
    private Instant createdAt;
    private Instant sentAt;
    private Instant readAt;
}
