package com.capstone.etp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonConfigTest {

    @Test
    void objectMapper_registersJavaTimeModule_andDisablesTimestampSerialization() {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper = config.objectMapper();

        assertNotNull(mapper);
        assertFalse(mapper.getSerializationConfig().isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));

        String serialized;
        try {
            serialized = mapper.writeValueAsString(Instant.parse("2026-01-01T00:00:00Z"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertTrue(serialized.contains("2026-01-01T00:00:00Z"));
    }
}
