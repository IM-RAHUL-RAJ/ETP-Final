-- -----------------------------------------------------------------------------
-- DIM_ACCOUNT
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dim_account (
    account_key     BIGINT       NOT NULL,
    account_id      VARCHAR(32)  NOT NULL,   -- accounts.account_ref from Postgres
    holder_name     VARCHAR(255) NOT NULL,   -- clients.name
    status          VARCHAR(20)  NOT NULL,   -- ACTIVE | SUSPENDED | CLOSED
    effective_date  DATE         NOT NULL,   -- when this version became current
    end_date        DATE,                    -- NULL on the current version
    is_current      BOOLEAN      NOT NULL,   -- TRUE on exactly one row per account_id
    source_id       VARCHAR(64)  NOT NULL,   -- accounts.id / account_id from Postgres (for lineage)
    loaded_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_dim_account PRIMARY KEY (account_key)
);


-- -----------------------------------------------------------------------------
-- DIM_INSTRUMENT
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dim_instrument (
    instrument_key  BIGINT       NOT NULL,
    symbol          VARCHAR(20)  NOT NULL,   -- instruments.symbol from Postgres
    name            VARCHAR(255),
    asset_class     VARCHAR(20),
    currency        CHAR(3),
    exchange        VARCHAR(20),
    tradable        BOOLEAN,
    loaded_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_dim_instrument PRIMARY KEY (instrument_key),
    CONSTRAINT uq_dim_instrument_symbol UNIQUE (symbol)
);


-- -----------------------------------------------------------------------------
-- DIM_DATE
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dim_date (
    date_key    INTEGER     NOT NULL,
    full_date   DATE        NOT NULL,
    day         INTEGER     NOT NULL,
    month       INTEGER     NOT NULL,
    year        INTEGER     NOT NULL,
    quarter     INTEGER     NOT NULL,
    day_of_week INTEGER     NOT NULL,
    day_name    VARCHAR(9)  NOT NULL,
    month_name  VARCHAR(9)  NOT NULL,
    is_weekday  BOOLEAN     NOT NULL,

    CONSTRAINT pk_dim_date PRIMARY KEY (date_key),
    CONSTRAINT uq_dim_date_full_date UNIQUE (full_date)
);


-- -----------------------------------------------------------------------------
-- FACT_TRADES
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fact_trades (
    trade_key       BIGINT        NOT NULL,
    account_key     BIGINT        NOT NULL,
    instrument_key  BIGINT        NOT NULL,
    date_key        INTEGER       NOT NULL,
    side            VARCHAR(4)    NOT NULL,   -- BUY | SELL
    quantity        INTEGER       NOT NULL,
    price           DECIMAL(18,2) NOT NULL,   -- requested price
    status          VARCHAR(20)   NOT NULL,   -- NEW | FILLED | CANCELLED | REJECTED
    executed_price  DECIMAL(18,2),            -- NULL for unfilled orders
    trade_value     DECIMAL(18,2) NOT NULL,   -- quantity × executed_price (or price)
    source_order_id VARCHAR(36)   NOT NULL,   -- orders.id from Postgres (as text)
    created_at      TIMESTAMP     NOT NULL,
    loaded_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_fact_trades         PRIMARY KEY (trade_key),
    CONSTRAINT uq_fact_trades_source  UNIQUE (source_order_id),

    CONSTRAINT fk_fact_trades_account
        FOREIGN KEY (account_key)    REFERENCES dim_account (account_key),
    CONSTRAINT fk_fact_trades_instrument
        FOREIGN KEY (instrument_key) REFERENCES dim_instrument (instrument_key),
    CONSTRAINT fk_fact_trades_date
        FOREIGN KEY (date_key)       REFERENCES dim_date (date_key)
);


-- -----------------------------------------------------------------------------
-- DIM_BATCH
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dim_batch (
    batch_id       VARCHAR(36) NOT NULL,
    started_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_dim_batch PRIMARY KEY (batch_id)
);

-- =============================================================================
-- DEAD_LETTER_TRADES
-- =============================================================================
CREATE TABLE IF NOT EXISTS dead_letter_trades (
    dead_letter_key    BIGINT       NOT NULL,
    source_order_id    VARCHAR(36)  NOT NULL,
    failure_reason     VARCHAR(1024) NOT NULL,
    batch_id           VARCHAR(36),
    raw_row            VARCHAR,
    failed_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_dead_letter_trades PRIMARY KEY (dead_letter_key),
    CONSTRAINT uq_dead_letter_source UNIQUE (source_order_id),
    CONSTRAINT fk_dead_letter_trades_batch
        FOREIGN KEY (batch_id) REFERENCES dim_batch (batch_id)
);

