-- Automated trading: the strategies a customer sets up and switches on.
-- A strategy is created OFF. Safe to run twice.

BEGIN;

SET LOCAL search_path TO trade, public;

CREATE TABLE IF NOT EXISTS automation_strategies (
    id            BIGSERIAL PRIMARY KEY,
    account_id    BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    type          TEXT NOT NULL CHECK (type IN ('MA_CROSSOVER', 'BOLLINGER_BANDS')),
    symbol        TEXT NOT NULL,
    quantity      INTEGER NOT NULL CHECK (quantity >= 1),
    short_period  INTEGER,
    long_period   INTEGER,
    period        INTEGER,
    multiplier    NUMERIC(6,2),
    enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_automation_strategies_account ON automation_strategies (account_id);
CREATE INDEX IF NOT EXISTS idx_automation_strategies_enabled ON automation_strategies (symbol) WHERE enabled;

COMMIT;
