package com.capstone.etp.notifications;

import com.capstone.etp.preferences.AlertChannel;


interface ChannelSender {

    AlertChannel channel();

    /** @throws DeliveryException when the provider refused or could not be reached */
    Mode send(OutboundMessage message) throws DeliveryException;

    /** Whether the message really left the platform, or was only logged because the channel has no provider here. */
    enum Mode {
        LIVE,
        STUB
    }

    /** A provider failure. Carries no destination and no message text. */
    class DeliveryException extends Exception {
        DeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
