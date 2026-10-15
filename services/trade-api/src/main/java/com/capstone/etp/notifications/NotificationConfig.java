package com.capstone.etp.notifications;

import com.capstone.etp.preferences.AlertChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Wires the stub channels and switches on the schedulers the notification dispatcher (and the alert modules) use. */
@Configuration
@EnableScheduling
class NotificationConfig {

    @Bean
    ChannelSender smsChannelSender() {
        return new StubChannelSender(AlertChannel.SMS);
    }

    @Bean
    ChannelSender pushChannelSender() {
        return new StubChannelSender(AlertChannel.PUSH);
    }
}
