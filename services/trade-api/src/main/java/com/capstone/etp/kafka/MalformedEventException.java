package com.capstone.etp.kafka;

// A message that will never be processable (bad JSON, a missing field, a value out of range).

public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message) {
        super(message);
    }

    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}