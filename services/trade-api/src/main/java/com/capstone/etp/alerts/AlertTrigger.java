package com.capstone.etp.alerts;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of price_alert_triggers: one firing of one alert. */
@Data
public class AlertTrigger {
    private Long id;
    private Long alertId;
    private UUID eventId;
    private BigDecimal triggerPrice;
    private Instant triggeredAt;
    private Long notificationId;
}
