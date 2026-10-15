-- Sprint 10 UI enhancements: alert operators + enable/disable, trigger history,
-- mascot and notification preferences, cash movements (add / withdraw funds).
-- Safe to run twice.

BEGIN;

SET LOCAL search_path TO trade, public;

-- ---------------------------------------------------------------------
-- PRICE ALERTS: richer conditions and an on/off switch
-- ---------------------------------------------------------------------
ALTER TABLE price_alerts ADD COLUMN IF NOT EXISTS operator TEXT;
ALTER TABLE price_alerts ADD COLUMN IF NOT EXISTS enabled  BOOLEAN NOT NULL DEFAULT TRUE;

-- Existing alerts keep their meaning: ABOVE fired at >=, BELOW fired at <=.
UPDATE price_alerts SET operator = CASE direction WHEN 'ABOVE' THEN 'GTE' ELSE 'LTE' END
 WHERE operator IS NULL;

ALTER TABLE price_alerts ALTER COLUMN operator SET NOT NULL;
ALTER TABLE price_alerts DROP CONSTRAINT IF EXISTS ck_price_alerts_operator;
ALTER TABLE price_alerts ADD CONSTRAINT ck_price_alerts_operator
    CHECK (operator IN ('GT', 'GTE', 'LT', 'LTE', 'EQ'));

-- The evaluator only loads alerts that are active AND switched on.
DROP INDEX IF EXISTS idx_price_alerts_active_symbol;
CREATE INDEX idx_price_alerts_active_symbol
    ON price_alerts (symbol)
    WHERE status = 'ACTIVE' AND enabled;

-- ---------------------------------------------------------------------
-- TRIGGER HISTORY: one row per firing. event_id makes a re-delivery a no-op.
-- Deleting an alert deletes its history.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS price_alert_triggers (
    id               BIGSERIAL PRIMARY KEY,
    alert_id         BIGINT NOT NULL REFERENCES price_alerts(id) ON DELETE CASCADE,
    event_id         UUID NOT NULL UNIQUE,
    trigger_price    NUMERIC(18,4) NOT NULL,
    triggered_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notification_id  BIGINT REFERENCES notifications(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_price_alert_triggers_alert
    ON price_alert_triggers (alert_id, triggered_at DESC, id DESC);

-- Backfill: alerts that already fired get one history row.
INSERT INTO price_alert_triggers (alert_id, event_id, trigger_price, triggered_at)
SELECT id, md5('price-alert-backfill:' || id)::uuid, triggered_price, triggered_at
  FROM price_alerts
 WHERE status = 'TRIGGERED' AND triggered_price IS NOT NULL
ON CONFLICT (event_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- PREFERENCES: mascot and notification choices
-- ---------------------------------------------------------------------
ALTER TABLE customer_preferences ADD COLUMN IF NOT EXISTS mascot TEXT NOT NULL DEFAULT 'OWL';
ALTER TABLE customer_preferences ADD COLUMN IF NOT EXISTS notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE customer_preferences ADD COLUMN IF NOT EXISTS notification_kinds TEXT NOT NULL
    DEFAULT 'ORDER_FILLED,ORDER_REJECTED,ORDER_CANCELLED,PRICE_ALERT,FUNDS';
ALTER TABLE customer_preferences DROP CONSTRAINT IF EXISTS ck_customer_preferences_mascot;
ALTER TABLE customer_preferences ADD CONSTRAINT ck_customer_preferences_mascot
    CHECK (mascot IN ('OWL', 'RABBIT', 'CAT'));

-- Funds movements are announced too.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS ck_notifications_kind;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_kind
    CHECK (kind IN ('ORDER_FILLED', 'ORDER_REJECTED', 'ORDER_CANCELLED', 'PRICE_ALERT', 'FUNDS'));

-- A message the customer opted out of is kept in the inbox but never sent.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS ck_notifications_status;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_status
    CHECK (status IN ('QUEUED', 'HELD', 'SENT', 'FAILED', 'SKIPPED'));

-- ---------------------------------------------------------------------
-- CASH MOVEMENTS: every add / withdraw is a record; accounts.cash_balance follows.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cash_movements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    kind            TEXT NOT NULL,
    amount          NUMERIC(18,2) NOT NULL,
    currency        CHAR(3) NOT NULL,
    method          TEXT NOT NULL,
    balance_after   NUMERIC(18,2) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_cash_movements_kind CHECK (kind IN ('DEPOSIT', 'WITHDRAWAL')),
    CONSTRAINT ck_cash_movements_amount CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_cash_movements_account
    ON cash_movements (account_id, created_at DESC);

-- ---------------------------------------------------------------------
-- PORTFOLIO VALUE HISTORY: one point per visit to the portfolio, at most one every ten minutes.
-- It starts empty and fills as the customer uses the app; the chart says so rather than inventing a past.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS portfolio_value_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    taken_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_value     NUMERIC(18,2) NOT NULL,
    base_currency   CHAR(3) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_portfolio_snapshots_account_time
    ON portfolio_value_snapshots (account_id, taken_at DESC);

COMMIT;
