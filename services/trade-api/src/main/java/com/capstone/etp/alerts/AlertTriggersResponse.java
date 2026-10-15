package com.capstone.etp.alerts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The firing history of one alert. Creation is not an event here, only triggers. */
public record AlertTriggersResponse(Long alertId, List<Trigger> triggers) {

    /** notificationStatus is SENT, QUEUED, HELD, FAILED, or UNKNOWN when no message was recorded. */
    public record Trigger(Long id, Instant triggeredAt, BigDecimal triggerPrice,
                          String notificationStatus, Long notificationId) {
    }
}
