package com.capstone.etp.notifications;

import com.capstone.etp.preferences.AlertChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * A channel the platform cannot provision here (an SMS gateway, a push service). It logs a masked
 * line and reports {@link Mode#STUB}; the notification is recorded as sent with the reason
 * STUB_DELIVERY so nobody mistakes it for a live send. The routing decision is real.
 */
@Slf4j
class StubChannelSender implements ChannelSender {

    private final AlertChannel channel;

    StubChannelSender(AlertChannel channel) {
        this.channel = channel;
    }

    @Override
    public AlertChannel channel() {
        return channel;
    }

    @Override
    public Mode send(OutboundMessage message) {
        log.info("[NOTIFY-STUB:{}] no provider configured; recorded only. to={}", channel, message.masked());
        return Mode.STUB;
    }
}
