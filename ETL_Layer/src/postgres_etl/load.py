from __future__ import annotations

import hashlib
import json
import logging
from datetime import date, datetime, timezone, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any
from uuid import uuid4

import duckdb

from .config import TRADES_DUCKDB_PATH
from .dead_letter import record_dead_letter
from .errors import LoadError

logger = logging.getLogger(__name__)

BASE_DIR = Path(__file__).resolve().parent
SCHEMA_FILE = BASE_DIR / "contracts" / "trades-schema.sql"


def _create_schema(conn: duckdb.DuckDBPyConnection) -> None:
    if not SCHEMA_FILE.exists():
        raise LoadError(f"Schema file not found: {SCHEMA_FILE}")
    sql = SCHEMA_FILE.read_text(encoding="utf-8")
    conn.execute(sql)
    logger.info("Schema created / verified")


def _load_dim_date(conn: duckdb.DuckDBPyConnection, year: int) -> int:
    start = date(year, 1, 1)
    end = date(year, 12, 31)
    current = start
    rows = 0

    while current <= end:
        date_key = int(current.strftime("%Y%m%d"))
        exists = conn.execute(
            "SELECT 1 FROM dim_date WHERE date_key = ?", [date_key]
        ).fetchone()

        if not exists:
            conn.execute(
                """
                INSERT INTO dim_date
                (date_key, full_date, day, month, year, quarter,
                 day_of_week, day_name, month_name, is_weekday)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                [
                    date_key,
                    current,
                    current.day,
                    current.month,
                    current.year,
                    ((current.month - 1) // 3) + 1,
                    current.weekday() + 1,
                    current.strftime("%A"),
                    current.strftime("%B"),
                    current.weekday() < 5,
                ],
            )
            rows += 1
        current += timedelta(days=1)

    if rows:
        logger.info("DIM_DATE: inserted %d new rows for year %d", rows, year)
    return rows


def _upsert_instrument(conn: duckdb.DuckDBPyConnection, row: dict) -> int:
    existing = conn.execute(
        "SELECT instrument_key FROM dim_instrument WHERE symbol = ?",
        [row["symbol"]],
    ).fetchone()

    if existing:
        # Type 1: overwrite attributes in place
        conn.execute(
            """
            UPDATE dim_instrument
            SET name        = ?,
                asset_class = ?,
                currency    = ?,
                exchange    = ?,
                tradable    = ?,
                loaded_at   = CURRENT_TIMESTAMP
            WHERE symbol = ?
            """,
            [
                row.get("instrument_name"),
                row.get("asset_class"),
                row.get("currency"),
                row.get("exchange"),
                row.get("tradable"),
                row["symbol"],
            ],
        )
        return existing[0]

    # Insert new instrument
    key = (
        conn.execute(
            "SELECT COALESCE(MAX(instrument_key), 0) + 1 FROM dim_instrument"
        ).fetchone()[0]
    )
    conn.execute(
        """
        INSERT INTO dim_instrument
        (instrument_key, symbol, name, asset_class, currency, exchange, tradable)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        [
            key,
            row["symbol"],
            row.get("instrument_name"),
            row.get("asset_class"),
            row.get("currency"),
            row.get("exchange"),
            row.get("tradable"),
        ],
    )
    logger.info(
        "DIM_INSTRUMENT: inserted symbol=%s instrument_key=%d",
        row["symbol"],
        key,
    )
    return key


def _upsert_account(conn: duckdb.DuckDBPyConnection, row: dict) -> int:
    current_version = conn.execute(
        """
        SELECT account_key, status
        FROM   dim_account
        WHERE  account_id  = ?
        AND    is_current   = TRUE
        """,
        [row["account_ref"]],
    ).fetchone()

    today = date.today()

    if current_version:
        existing_key, existing_status = current_version
        if existing_status == row["account_status"]:
            # Status unchanged — return existing key
            return existing_key

        # Status changed — close out the old version
        conn.execute(
            """
            UPDATE dim_account
            SET    end_date   = ?,
                   is_current = FALSE
            WHERE  account_key = ?
            """,
            [today - timedelta(days=1), existing_key],
        )

    # Insert new version
    new_key = (
        conn.execute(
            "SELECT COALESCE(MAX(account_key), 0) + 1 FROM dim_account"
        ).fetchone()[0]
    )
    conn.execute(
        """
        INSERT INTO dim_account
        (account_key, account_id, holder_name, status,
         effective_date, end_date, is_current, source_id, loaded_at)
        VALUES (?, ?, ?, ?, ?, NULL, TRUE, ?, CURRENT_TIMESTAMP)
        """,
        [
            new_key,
            row["account_ref"],
            row["holder_name"],
            row["account_status"],
            today,
            row["account_id"],
        ],
    )
    logger.info(
        "DIM_ACCOUNT: upserted account_ref=%s account_key=%d status=%s",
        row["account_ref"],
        new_key,
        row["account_status"],
    )
    return new_key


def _insert_trade(
    conn: duckdb.DuckDBPyConnection,
    row: dict,
    account_key: int,
    instrument_key: int,
    batch_id: str,
) -> bool:
    # Generate deterministic trade_key from source_order_id
    trade_key = int(
        hashlib.sha256(row["source_order_id"].encode("utf-8")).hexdigest()[:15], 16
    )

    # Verify date_key exists in dim_date (FK constraint)
    date_exists = conn.execute(
        "SELECT 1 FROM dim_date WHERE date_key = ?", [row["date_key"]]
    ).fetchone()

    if not date_exists:
        reason = (
            f"date_key {row['date_key']} not found in dim_date "
            f"(created_at={row['created_at']})"
        )
        record_dead_letter(
            conn,
            row["source_order_id"],
            reason,
            batch_id,
            row,
        )
        logger.warning("DEAD_LETTER: %s", reason)
        return False

    # Verify account_key exists in dim_account (FK constraint)
    account_exists = conn.execute(
        "SELECT 1 FROM dim_account WHERE account_key = ?", [account_key]
    ).fetchone()

    if not account_exists:
        reason = f"account_key {account_key} not found in dim_account"
        record_dead_letter(
            conn,
            row["source_order_id"],
            reason,
            batch_id,
            row,
        )
        logger.warning("DEAD_LETTER: %s", reason)
        return False

    # Verify instrument_key exists in dim_instrument (FK constraint)
    instrument_exists = conn.execute(
        "SELECT 1 FROM dim_instrument WHERE instrument_key = ?", [instrument_key]
    ).fetchone()

    if not instrument_exists:
        reason = f"instrument_key {instrument_key} not found in dim_instrument"
        record_dead_letter(
            conn,
            row["source_order_id"],
            reason,
            batch_id,
            row,
        )
        logger.warning("DEAD_LETTER: %s", reason)
        return False

    # All checks passed — insert the fact row
    try:
        result = conn.execute(
            """
            INSERT INTO fact_trades
            (trade_key, account_key, instrument_key, date_key,
             side, quantity, price, status,
             executed_price, trade_value, source_order_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            RETURNING trade_key
            """,
            [
                trade_key,
                account_key,
                instrument_key,
                row["date_key"],
                row["side"],
                row["quantity"],
                float(row["price"]),
                row["status"],
                (
                    float(row["executed_price"])
                    if row["executed_price"] is not None
                    else None
                ),
                float(row["trade_value"]),
                row["source_order_id"],
                row["created_at"],
            ],
        ).fetchone()

        return result is not None

    except Exception as exc:
        reason = f"Insert failed: {exc}"
        record_dead_letter(
            conn,
            row["source_order_id"],
            reason,
            batch_id,
            row,
        )
        logger.warning("DEAD_LETTER: %s", reason)
        return False

def load(transformed_data: dict[str, Any], db_path: str | None = None) -> int:
    clean: list[dict] = transformed_data.get("clean", [])
    batch_id = str(uuid4())

    target_path = Path(db_path or TRADES_DUCKDB_PATH)
    target_path.parent.mkdir(parents=True, exist_ok=True)

    conn = duckdb.connect(str(target_path))
    try:
        logger.info("=" * 60)
        logger.info("POSTGRES ETL LOAD — trades.duckdb")
        logger.info("Batch ID: %s", batch_id)
        logger.info("=" * 60)

        _create_schema(conn)
        
        conn.execute(
            """
            INSERT INTO dim_batch (batch_id)
            VALUES (?)
            ON CONFLICT DO NOTHING
            """,
            [batch_id],
        )

        if not clean:
            logger.info("No clean rows to load")
            return 0

        years = {row["created_at"].year for row in clean}
        for year in sorted(years):
            _load_dim_date(conn, year)

        rows_inserted = 0
        for row in clean:
            try:
                instrument_key = _upsert_instrument(conn, row)
                account_key = _upsert_account(conn, row)
                inserted = _insert_trade(conn, row, account_key, instrument_key, batch_id)
                if inserted:
                    rows_inserted += 1
            except Exception as exc:
                reason = f"Row processing failed: {exc}"
                record_dead_letter(
                    conn,
                    row.get("source_order_id", "unknown"),
                    reason,
                    batch_id,
                    row,
                )
                logger.error("DEAD_LETTER: %s", reason)

        conn.commit()

        logger.info("=" * 60)
        logger.info("LOAD COMPLETE: %d new fact_trades rows inserted", rows_inserted)
        logger.info("=" * 60)
        return rows_inserted

    except Exception:
        try:
            conn.rollback()
        except Exception:
            pass
        raise

    finally:
        conn.close()


def get_watermark(db_path: str | None = None) -> datetime:
    """
    Get the watermark from the analytical schema.

    The watermark is the maximum created_at timestamp from fact_trades.
    It is used by the extract phase to determine which orders to fetch.

    If the database doesn't exist or fact_trades is empty, returns epoch.
    """
    path = db_path or TRADES_DUCKDB_PATH
    path_obj = Path(path)

    if not path_obj.exists():
        _default = datetime(1970, 1, 1, tzinfo=timezone.utc)
        logger.info("No trades.duckdb found — defaulting watermark to %s", _default)
        return _default

    conn = duckdb.connect(str(path_obj), read_only=True)
    try:
        result = conn.execute(
            """
            SELECT MAX(created_at)
            FROM   fact_trades
            """
        ).fetchone()

        if result and result[0] is not None:
            wm = result[0]
            if isinstance(wm, datetime) and wm.tzinfo is None:
                wm = wm.replace(tzinfo=timezone.utc)
            logger.info("Watermark from fact_trades: %s", wm)
            return wm
    except Exception as exc:
        logger.warning("Failed to read watermark from fact_trades: %s", exc)
    finally:
        conn.close()

    default = datetime(1970, 1, 1, tzinfo=timezone.utc)
    logger.info("fact_trades empty — defaulting watermark to %s", default)
    return default

