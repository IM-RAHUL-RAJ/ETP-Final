package com.capstone.etp.exception;

import java.util.UUID;

public final class DuplicateOrderException extends ETPException {

    private final UUID idempotencyKey;

    public DuplicateOrderException(UUID idempotencyKey) {
        super("ORD-409", "Duplicate order");
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }
}