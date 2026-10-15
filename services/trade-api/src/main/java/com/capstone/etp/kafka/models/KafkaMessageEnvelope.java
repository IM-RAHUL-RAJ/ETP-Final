package com.capstone.etp.kafka.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KafkaMessageEnvelope<T> {

    private UUID eventId;

    private String eventType;

    private Instant eventTime;

    private String source;

    private int schemaVersion;

    private T payload;
}