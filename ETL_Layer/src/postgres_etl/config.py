from __future__ import annotations

import os

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass


def _require(key: str, *fallbacks: str) -> str:
    """Value of `key` (or of the first fallback name that is set), from the environment or the repository's .env."""
    for name in (key, *fallbacks):
        value = os.environ.get(name)
        if value:
            return value
    raise RuntimeError(
        f"Required environment variable '{key}' is not set. "
        f"Add it to the .env at the repository root (see .env.example)."
    )


def get_postgres_dsn() -> str:
    # The shared platform variables (DB_*) are used; the older POSTGRES_* names still work.
    host     = _require("DB_HOST", "POSTGRES_HOST")
    port     = _require("DB_PORT", "POSTGRES_PORT")
    dbname   = _require("DB_NAME", "POSTGRES_DB")
    user     = _require("DB_USER", "POSTGRES_USER")
    password = _require("DB_PASSWORD", "POSTGRES_PASSWORD")
    # One database, two schemas: the orders data is in the trade schema.
    schema   = _require("DB_SCHEMA_TRADE")
    return (
        f"host={host} port={port} dbname={dbname} "
        f"user={user} password={password} "
        f"options='-c search_path={schema},public'"
    )


TRADES_DUCKDB_PATH: str = os.environ.get(
    "TRADES_DUCKDB_PATH", "data/trades.duckdb"
)

# Kafka configuration for trade-events consumer
# The broker is KAFKA_BOOTSTRAP_SERVERS from the shared .env (a Linux VM, localhost, ...); there is no built-in address.
KAFKA_BROKER = os.environ.get("KAFKA_BROKER") or os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "")
KAFKA_TRADE_EVENTS_TOPIC = os.environ.get("KAFKA_TRADE_EVENTS_TOPIC") or os.environ.get("TRADE_EVENTS_TOPIC", "trade-events")
KAFKA_CONSUMER_GROUP = os.environ.get("KAFKA_CONSUMER_GROUP", "analytics-loader")

# Watermark tracking for incremental loads
WATERMARK_FILE: str = os.environ.get(
    "WATERMARK_FILE", "data/.watermark"
)
