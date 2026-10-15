-- =====================================================================
-- schema.sql : complete PostgreSQL schema of the Enterprise Trading Platform
--
-- One database (DB_NAME, trading_system_db) holding two schemas:
--
--   trade : everything the order service and the executor service use
--           (clients, accounts, portfolios, instruments, orders, ...)
--   auth  : everything the auth service uses (auth_users, refresh_tokens)
--
-- This file is the final state of migrations/001 .. 007 in one script. It can be run on an
-- empty database, and it is safe to run again (CREATE ... IF NOT EXISTS everywhere).
-- Run it with:  psql -v ON_ERROR_STOP=1 -d <DB_NAME> -f schema.sql
-- (scripts/apply.sh does this for you, creating the database first if it is missing.)
--
-- The applications select their schema through the connection (DB_SCHEMA_TRADE / DB_SCHEMA_AUTH),
-- so no SQL in the services carries a schema prefix.
-- =====================================================================

BEGIN;

CREATE SCHEMA IF NOT EXISTS trade;
CREATE SCHEMA IF NOT EXISTS auth;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

-- =====================================================================
-- SCHEMA trade
-- =====================================================================
SET LOCAL search_path TO trade, public;

DO $$ BEGIN CREATE TYPE gender_enum AS ENUM ('MALE', 'FEMALE', 'OTHER');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN CREATE TYPE client_status_enum AS ENUM ('ACTIVE', 'DORMANT', 'CLOSED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN CREATE TYPE account_type_enum AS ENUM ('US_EQUITY', 'INDIAN_EQUITY', 'FOREX', 'CRYPTO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN CREATE TYPE account_status_enum AS ENUM ('ACTIVE', 'SUSPENDED', 'CLOSED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN CREATE TYPE risk_profile_enum AS ENUM ('CONSERVATIVE', 'MODERATE', 'AGGRESSIVE');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN CREATE TYPE asset_class_enum AS ENUM ('US_EQUITY', 'INDIAN_EQUITY', 'FX', 'CRYPTO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN CREATE TYPE order_type_enum AS ENUM ('BUY', 'SELL');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN CREATE TYPE order_status_enum AS ENUM ('NEW', 'FILLED', 'CANCELLED', 'REJECTED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN CREATE TYPE transaction_status_enum AS ENUM ('EXECUTED', 'FAILED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;


-- ---------------------------------------------------------------------
-- CLIENTS  (a person; sign-in credentials live in auth.auth_users, never here)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS clients (
    id              BIGSERIAL PRIMARY KEY,
    client_ref      VARCHAR(10) NOT NULL UNIQUE,
    name            TEXT NOT NULL,
    email           TEXT NOT NULL UNIQUE,
    phone           TEXT,
    date_of_birth   DATE,
    gender          gender_enum,
    status          client_status_enum NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT ck_clients_client_ref
        CHECK (client_ref ~ '^C[0-9]{9}$'),

    CONSTRAINT ck_clients_phone
        CHECK (phone IS NULL OR phone ~ '^[0-9]{10}$')
);


-- ---------------------------------------------------------------------
-- ACCOUNTS  (exactly one account per client: client_id is UNIQUE)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS accounts (
    id              BIGSERIAL PRIMARY KEY,
    account_ref     VARCHAR(10) NOT NULL UNIQUE,
    client_id       BIGINT NOT NULL
                        REFERENCES clients(id)
                        ON DELETE RESTRICT,
    account_type    account_type_enum NOT NULL,
    currency        CHAR(3) NOT NULL,
    cash_balance    NUMERIC(10,2) NOT NULL DEFAULT 0,
    status          account_status_enum NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    suspended_at    TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ,

    CONSTRAINT uq_accounts_client_id UNIQUE (client_id),

    CONSTRAINT ck_accounts_account_ref
        CHECK (account_ref ~ '^ACC[0-9]{7}$'),

    CONSTRAINT ck_accounts_cash_balance
        CHECK (cash_balance >= 0),

    CONSTRAINT ck_accounts_version
        CHECK (version >= 0),

    CONSTRAINT ck_accounts_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT ck_accounts_state_dates
        CHECK (
            (status = 'ACTIVE'    AND suspended_at IS NULL     AND closed_at IS NULL)
            OR
            (status = 'SUSPENDED' AND suspended_at IS NOT NULL AND closed_at IS NULL)
            OR
            (status = 'CLOSED'    AND closed_at IS NOT NULL)
        )
);


-- ---------------------------------------------------------------------
-- ACCOUNT PREFERENCES
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS account_preferences (
    id                  BIGSERIAL PRIMARY KEY,
    account_id          BIGINT NOT NULL
                            REFERENCES accounts(id)
                            ON DELETE RESTRICT,
    risk_profile        risk_profile_enum NOT NULL,
    investment_goals    TEXT,
    preferred_sectors   TEXT[] NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_account_preferences_account_id
    ON account_preferences (account_id);


-- ---------------------------------------------------------------------
-- PORTFOLIOS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS portfolios (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT NOT NULL UNIQUE
                        REFERENCES accounts(id)
                        ON DELETE RESTRICT,
    name            TEXT NOT NULL,
    current_value   NUMERIC(10,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_portfolios_current_value
        CHECK (current_value >= 0)
);


-- ---------------------------------------------------------------------
-- INSTRUMENTS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS instruments (
    id                  BIGSERIAL PRIMARY KEY,
    symbol              TEXT NOT NULL UNIQUE,
    exchange            TEXT NOT NULL,
    name                TEXT NOT NULL,
    asset_class         asset_class_enum NOT NULL,
    currency            CHAR(3) NOT NULL,
    is_tradable         BOOLEAN NOT NULL DEFAULT TRUE,
    stopped_at          TIMESTAMPTZ,

    CONSTRAINT ck_instruments_tradable_state
        CHECK (
            (is_tradable = TRUE  AND stopped_at IS NULL)
            OR
            (is_tradable = FALSE AND stopped_at IS NOT NULL)
        )
);


-- ---------------------------------------------------------------------
-- PORTFOLIO HOLDINGS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS portfolio_holdings (
    id              BIGSERIAL PRIMARY KEY,
    portfolio_id    BIGINT NOT NULL
                        REFERENCES portfolios(id)
                        ON DELETE RESTRICT,
    instrument_id   BIGINT NOT NULL
                        REFERENCES instruments(id)
                        ON DELETE RESTRICT,
    quantity        INTEGER NOT NULL,
    avg_price       NUMERIC(10,2) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_portfolio_instrument
        UNIQUE (portfolio_id, instrument_id),

    CONSTRAINT ck_holdings_quantity
        CHECK (quantity >= 0),

    CONSTRAINT ck_holdings_avg_price
        CHECK (avg_price >= 0)
);

CREATE INDEX IF NOT EXISTS idx_holdings_instrument_id
    ON portfolio_holdings (instrument_id);


-- ---------------------------------------------------------------------
-- ORDERS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id          BIGINT NOT NULL
                            REFERENCES accounts(id)
                            ON DELETE RESTRICT,
    instrument_id       BIGINT NOT NULL
                            REFERENCES instruments(id)
                            ON DELETE RESTRICT,
    idempotency_key     UUID NOT NULL DEFAULT gen_random_uuid(),
    order_type          order_type_enum NOT NULL,
    quantity            INTEGER NOT NULL,
    limit_price         NUMERIC(10,2),
    status              order_status_enum NOT NULL DEFAULT 'NEW',
    reason              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    terminal_at         TIMESTAMPTZ,

    CONSTRAINT ck_orders_quantity
        CHECK (quantity > 0),

    CONSTRAINT ck_orders_price
        CHECK (limit_price IS NULL OR limit_price > 0),

    CONSTRAINT ck_orders_terminal_timestamp
        CHECK (
            (status IN ('NEW') AND terminal_at IS NULL)
            OR
            (status IN ('FILLED', 'CANCELLED', 'REJECTED') AND terminal_at IS NOT NULL)
        )
);

-- Query 1: open orders of one account, newest first
CREATE INDEX IF NOT EXISTS idx_orders_account_status_created
    ON orders (account_id, status, created_at DESC);

-- Query 2: last orders of one account in any state, newest first
CREATE INDEX IF NOT EXISTS idx_orders_account_created
    ON orders (account_id, created_at DESC);

-- Query 4: every order created since a timestamp
CREATE INDEX IF NOT EXISTS idx_orders_created_at
    ON orders (created_at);


-- ---------------------------------------------------------------------
-- TRANSACTIONS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL UNIQUE
                        REFERENCES orders(id)
                        ON DELETE RESTRICT,
    quantity        INTEGER NOT NULL,
    executed_price  NUMERIC(10,2) NOT NULL,
    status          transaction_status_enum NOT NULL,
    reason          TEXT,
    executed_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_transactions_quantity
        CHECK (quantity > 0),

    CONSTRAINT ck_transactions_price
        CHECK (executed_price >= 0)
);


-- ---------------------------------------------------------------------
-- WATCHLIST
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS watchlist (
    account_id     BIGINT NOT NULL
                       REFERENCES accounts(id)
                       ON DELETE RESTRICT,
    instrument_id  BIGINT NOT NULL
                       REFERENCES instruments(id)
                       ON DELETE RESTRICT,
    added_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (account_id, instrument_id)
);


-- ---------------------------------------------------------------------
-- CUSTOMER PREFERENCES  (belong to the customer, not the account: ADR 0002)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customer_preferences (
    client_id             BIGINT PRIMARY KEY
                              REFERENCES clients(id) ON DELETE RESTRICT,
    default_account_id    BIGINT NOT NULL
                              REFERENCES accounts(id) ON DELETE RESTRICT,
    alert_channel         TEXT NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mascot                TEXT NOT NULL DEFAULT 'OWL',
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    notification_kinds    TEXT NOT NULL
                              DEFAULT 'ORDER_FILLED,ORDER_REJECTED,ORDER_CANCELLED,PRICE_ALERT,FUNDS',

    CONSTRAINT ck_customer_preferences_channel
        CHECK (alert_channel IN ('EMAIL', 'SMS', 'PUSH')),
    CONSTRAINT ck_customer_preferences_mascot
        CHECK (mascot IN ('OWL', 'RABBIT', 'CAT'))
);


-- ---------------------------------------------------------------------
-- NOTIFICATIONS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id                BIGSERIAL PRIMARY KEY,
    event_id          UUID NOT NULL UNIQUE,
    account_id        BIGINT NOT NULL
                          REFERENCES accounts(id) ON DELETE RESTRICT,
    kind              TEXT NOT NULL,
    subject           TEXT NOT NULL,
    body              TEXT NOT NULL,
    status            TEXT NOT NULL DEFAULT 'QUEUED',
    status_reason     TEXT,
    resolved_channel  TEXT,
    channel_source    TEXT,
    attempts          INTEGER NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at           TIMESTAMPTZ,
    read_at           TIMESTAMPTZ,

    CONSTRAINT ck_notifications_kind
        CHECK (kind IN ('ORDER_FILLED', 'ORDER_REJECTED', 'ORDER_CANCELLED', 'PRICE_ALERT', 'FUNDS')),
    CONSTRAINT ck_notifications_status
        CHECK (status IN ('QUEUED', 'HELD', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_notifications_channel
        CHECK (resolved_channel IS NULL OR resolved_channel IN ('EMAIL', 'SMS', 'PUSH')),
    CONSTRAINT ck_notifications_channel_source
        CHECK (channel_source IS NULL OR channel_source IN ('STORED', 'DEFAULT'))
);

CREATE INDEX IF NOT EXISTS idx_notifications_account_created
    ON notifications (account_id, created_at DESC, id DESC);

-- The dispatcher only ever looks at what is still waiting.
CREATE INDEX IF NOT EXISTS idx_notifications_due
    ON notifications (next_attempt_at)
    WHERE status IN ('QUEUED', 'HELD');


-- ---------------------------------------------------------------------
-- PRICE ALERTS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS price_alerts (
    id                  BIGSERIAL PRIMARY KEY,
    account_id          BIGINT NOT NULL
                            REFERENCES accounts(id) ON DELETE RESTRICT,
    symbol              TEXT NOT NULL,
    direction           TEXT NOT NULL,
    operator            TEXT NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    threshold           NUMERIC(18,4) NOT NULL,
    status              TEXT NOT NULL DEFAULT 'ACTIVE',
    delivery_state      TEXT NOT NULL DEFAULT 'NONE',
    delivery_attempts   INTEGER NOT NULL DEFAULT 0,
    triggered_price     NUMERIC(18,4),
    triggered_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_price_alerts_direction
        CHECK (direction IN ('ABOVE', 'BELOW')),
    CONSTRAINT ck_price_alerts_operator
        CHECK (operator IN ('GT', 'GTE', 'LT', 'LTE', 'EQ')),
    CONSTRAINT ck_price_alerts_threshold
        CHECK (threshold > 0),
    CONSTRAINT ck_price_alerts_status
        CHECK (status IN ('ACTIVE', 'TRIGGERED', 'CANCELLED')),
    CONSTRAINT ck_price_alerts_delivery_state
        CHECK (delivery_state IN ('NONE', 'PENDING_HANDOVER', 'HANDED_OVER', 'HANDOVER_FAILED')),
    CONSTRAINT ck_price_alerts_trigger_state
        CHECK ((status = 'TRIGGERED') = (triggered_at IS NOT NULL))
);

-- The quote consumer asks "which active, enabled alerts exist for this symbol" on every message.
CREATE INDEX IF NOT EXISTS idx_price_alerts_active_symbol
    ON price_alerts (symbol)
    WHERE status = 'ACTIVE' AND enabled;

CREATE INDEX IF NOT EXISTS idx_price_alerts_account
    ON price_alerts (account_id, created_at DESC, id DESC);

-- Fired alerts whose handover to notifications has not been confirmed.
CREATE INDEX IF NOT EXISTS idx_price_alerts_pending_handover
    ON price_alerts (triggered_at)
    WHERE status = 'TRIGGERED' AND delivery_state IN ('PENDING_HANDOVER', 'HANDOVER_FAILED');


-- One row per firing. event_id makes a re-delivery a no-op; deleting an alert deletes its history.
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


-- ---------------------------------------------------------------------
-- REALISED PROFIT AND LOSS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS portfolio_realised_pnl (
    event_id             UUID PRIMARY KEY,
    account_id           BIGINT NOT NULL
                             REFERENCES accounts(id) ON DELETE RESTRICT,
    symbol               TEXT NOT NULL,
    quantity             INTEGER NOT NULL,
    executed_price       NUMERIC(18,4) NOT NULL,
    average_cost         NUMERIC(18,4) NOT NULL,
    base_currency        CHAR(3) NOT NULL,
    realised_pnl         NUMERIC(18,4) NOT NULL,
    booked_at            TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_portfolio_realised_quantity CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_portfolio_realised_account_booked
    ON portfolio_realised_pnl (account_id, booked_at);


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


-- ---------------------------------------------------------------------
-- AUTOMATED TRADING: the strategies a customer sets up (created OFF) and their history
-- ---------------------------------------------------------------------
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


-- =====================================================================
-- SCHEMA auth
-- account_id is the trade.accounts id the login belongs to. It is deliberately not a foreign key:
-- the auth service owns its schema and only carries the id in the tokens it issues.
-- =====================================================================
SET LOCAL search_path TO auth, public;

CREATE TABLE IF NOT EXISTS auth_users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(64) UNIQUE NOT NULL,
    password_hash   TEXT NOT NULL,
    account_id      BIGINT NOT NULL,
    roles           TEXT[] NOT NULL DEFAULT ARRAY['CUSTOMER'],
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auth_users_username ON auth_users (username);
CREATE INDEX IF NOT EXISTS idx_auth_users_account_id ON auth_users (account_id);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    token_hash    TEXT NOT NULL UNIQUE,
    expires_at    TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consumed_at   TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

COMMIT;
