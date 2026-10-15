package com.capstone.etp.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IllegalOrderStateExceptionTest {

    @Test
    void constructor_setsCatalogueCodeAndMessage() {
        IllegalOrderStateException exception = new IllegalOrderStateException("invalid transition");

        assertEquals("ORD-409", exception.getCatalogueCode());
        assertEquals("invalid transition", exception.getMessage());
    }
}
