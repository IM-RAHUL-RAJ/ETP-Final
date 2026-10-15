-- Automated trading history: one row per trigger, with what became of its order.
-- The idempotency key is written before the order is sent, so an order can always be recognised as automated.
-- A removed strategy keeps its history. Safe to run twice.

BEGIN;

SET LOCAL search_path TO trade, public;

CREATE TABLE IF NOT EXISTS automation_events (
    id               BIGSERIAL PRIMARY KEY,
    strategy_id      BIGINT REFERENCES automation_strategies(id) ON DELETE SET NULL,
    account_id       BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    strategy_type    TEXT NOT NULL,
    symbol           TEXT NOT NULL,
    trigger_side     TEXT NOT NULL CHECK (trigger_side IN ('BUY', 'SELL')),
    outcome          TEXT NOT NULL CHECK (outcome IN ('STRATEGY_TRIGGERED', 'ORDER_SUBMITTED', 'ORDER_REJECTED', 'ORDER_SUBMISSION_FAILED')),
    idempotency_key  UUID NOT NULL UNIQUE,
    order_id         UUID,
    limit_price      NUMERIC(18,4),
    quantity         INTEGER,
    detail           TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_automation_events_account ON automation_events (account_id, created_at DESC);

COMMIT;
