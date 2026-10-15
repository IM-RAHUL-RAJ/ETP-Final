-- 001: base trade schema (clients, accounts, portfolios, instruments, orders, transactions, watchlist).
-- Runs on an empty database. Everything is created inside schema "trade" (the auth schema comes in 007).
-- Tearing down is reset.sql's job, so this migration drops nothing.

BEGIN;

CREATE SCHEMA IF NOT EXISTS trade;
CREATE SCHEMA IF NOT EXISTS auth;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

SET LOCAL search_path TO trade, public;




CREATE TYPE gender_enum AS ENUM (
    'MALE',
    'FEMALE',
    'OTHER'
);

CREATE TYPE client_status_enum AS ENUM (
    'ACTIVE',
    'DORMANT',
    'CLOSED'
);

CREATE TYPE account_type_enum AS ENUM (
     'US_EQUITY',
    'INDIAN_EQUITY',
    'FOREX',
    'CRYPTO'
);

CREATE TYPE account_status_enum AS ENUM (
    'ACTIVE',
    'SUSPENDED',
    'CLOSED'
);

CREATE TYPE risk_profile_enum AS ENUM (
    'CONSERVATIVE',
    'MODERATE',
    'AGGRESSIVE'
);

CREATE TYPE asset_class_enum AS ENUM (
    'US_EQUITY',
    'INDIAN_EQUITY',
    'FX',
    'CRYPTO'
);

CREATE TYPE order_type_enum AS ENUM (
    'BUY',
    'SELL'
);

CREATE TYPE order_status_enum AS ENUM (
    'NEW',
    'FILLED',
    'CANCELLED',
    'REJECTED'
);

CREATE TYPE transaction_status_enum AS ENUM (
    'EXECUTED',
    'FAILED'
);




-- CLIENTS


CREATE TABLE clients (
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
        CHECK (
            phone IS NULL
            OR phone ~ '^[0-9]{10}$'
        )
);



-- ACCOUNTS


CREATE TABLE accounts (
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
    updated_at       TIMESTAMPTZ,

    -- one account per client
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
            (
                status = 'ACTIVE'
                AND suspended_at IS NULL
                AND closed_at IS NULL
            )
            OR
            (
                status = 'SUSPENDED'
                AND suspended_at IS NOT NULL
                AND closed_at IS NULL
            )
            OR
            (
                status = 'CLOSED'
                AND closed_at IS NOT NULL
            )
        )
);



-- ACCOUNT PREFERENCES


CREATE TABLE account_preferences (
    id                  BIGSERIAL PRIMARY KEY,
    account_id          BIGINT NOT NULL
                            REFERENCES accounts(id)
                            ON DELETE RESTRICT,
    risk_profile        risk_profile_enum NOT NULL,
    investment_goals    TEXT,
    preferred_sectors   TEXT[] NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE INDEX idx_account_preferences_account_id
ON account_preferences(account_id);


-- PORTFOLIOS


CREATE TABLE portfolios (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT NOT NULL UNIQUE
                        REFERENCES accounts(id)
                        ON DELETE RESTRICT,
    name            TEXT NOT NULL,
    current_value   NUMERIC(10,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_portfolios_current_value
        CHECK(current_value >= 0)
);




-- INSTRUMENTS


CREATE TABLE instruments (
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
            (
                is_tradable = TRUE
                AND stopped_at IS NULL
            )
            OR
            (
                is_tradable = FALSE
                AND stopped_at IS NOT NULL
            )
        )
);




-- HOLDINGS


CREATE TABLE portfolio_holdings (
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
        UNIQUE(portfolio_id,instrument_id),


    CONSTRAINT ck_holdings_quantity
        CHECK(quantity >= 0),


    CONSTRAINT ck_holdings_avg_price
        CHECK(avg_price >= 0)
);


CREATE INDEX idx_holdings_instrument_id
ON portfolio_holdings(instrument_id);




-- ORDERS


CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id          BIGINT NOT NULL
                            REFERENCES accounts(id)
                            ON DELETE RESTRICT,
    instrument_id       BIGINT NOT NULL
                            REFERENCES instruments(id)
                            ON DELETE RESTRICT,
    idempotency_key     UUID NOT NULL DEFAULT gen_random_uuid(),
    order_type          order_type_enum NOT NULL,
    quantity            INTEGER NOT NULL,
    limit_price               NUMERIC(10,2),
    status              order_status_enum NOT NULL DEFAULT 'NEW',
    reason              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    terminal_at         TIMESTAMPTZ,

    CONSTRAINT ck_orders_quantity
        CHECK(quantity > 0),


    CONSTRAINT ck_orders_price
        CHECK(
            limit_price IS NULL
            OR limit_price > 0
        ),


    CONSTRAINT ck_orders_terminal_timestamp
        CHECK(
            (
                status IN ('NEW')
                AND terminal_at IS NULL
            )
            OR
            (
                status IN ('FILLED','CANCELLED','REJECTED')
                AND terminal_at IS NOT NULL
            )
        )
);




-- TRANSACTIONS

CREATE TABLE transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL UNIQUE
                        REFERENCES orders(id)
                        ON DELETE RESTRICT,
    quantity        INTEGER NOT NULL,
    executed_price           NUMERIC(10,2) NOT NULL,
    status          transaction_status_enum NOT NULL,
    reason              TEXT,
    executed_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,


    CONSTRAINT ck_transactions_quantity
        CHECK(quantity > 0),


    CONSTRAINT ck_transactions_price
        CHECK(executed_price >= 0)
);


-- ---------------------------------------------------------------------
-- WATCHLIST
-- ---------------------------------------------------------------------

CREATE TABLE watchlist (
    account_id     BIGINT NOT NULL
                       REFERENCES accounts(id)
                       ON DELETE RESTRICT,

    instrument_id  BIGINT NOT NULL
                       REFERENCES instruments(id)
                       ON DELETE RESTRICT,

    added_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (account_id, instrument_id)
);



COMMIT;