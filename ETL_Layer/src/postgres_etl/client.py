

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any

import psycopg2
import psycopg2.extras

from .config import get_postgres_dsn
from .errors import PostgresConnectionError, PostgresExtractError

logger = logging.getLogger(__name__)

_EXTRACT_SQL = """
SELECT
    o.id                        AS order_id,
    o.id::text                 AS source_order_id,
    o.order_type                AS side,
    o.quantity                  AS quantity,
    o.limit_price               AS price,
    o.status                    AS status,
    o.created_at                AS created_at,
    o.terminal_at               AS terminal_at,

    -- Transaction (filled price) — NULL for unfilled orders
    t.executed_price            AS executed_price,
    t.executed_at               AS executed_at,

    -- Instrument
    i.id                        AS instrument_id,
    i.symbol                    AS symbol,
    i.name                      AS instrument_name,
    i.asset_class               AS asset_class,
    i.currency                  AS currency,
    i.exchange                  AS exchange,
    i.is_tradable               AS tradable,

    -- Account
    a.id                        AS account_id,
    a.account_ref               AS account_ref,
    a.status                    AS account_status,
    a.created_at                AS account_created_at,

    -- Client (holder name)
    c.name                      AS holder_name

FROM orders o
JOIN instruments i  ON i.id = o.instrument_id
JOIN accounts    a  ON a.id = o.account_id
JOIN clients     c  ON c.id = a.client_id
LEFT JOIN transactions t ON t.order_id = o.id

WHERE o.created_at >= %(watermark)s

ORDER BY o.created_at ASC
"""


class PostgresClient:
    def __init__(self, dsn: str | None = None):
        dsn = dsn or get_postgres_dsn()
        try:
            self._conn = psycopg2.connect(
                dsn,
                cursor_factory=psycopg2.extras.RealDictCursor,
            )
            self._conn.set_session(readonly=True, autocommit=True)
            logger.info("PostgresClient connected")
        except psycopg2.OperationalError as exc:
            raise PostgresConnectionError(str(exc)) from exc

    def health(self) -> bool:
        try:
            with self._conn.cursor() as cur:
                cur.execute("SELECT 1")
            return True
        except Exception:
            return False

    def get_orders_since(self, watermark: datetime) -> list[dict[str, Any]]:
        if watermark.tzinfo is None:
            watermark = watermark.replace(tzinfo=timezone.utc)

        try:
            with self._conn.cursor() as cur:
                cur.execute(_EXTRACT_SQL, {"watermark": watermark})
                rows = cur.fetchall()
                logger.info(
                    "PostgresClient fetched %d orders since %s",
                    len(rows),
                    watermark.isoformat(),
                )
                return [dict(row) for row in rows]
        except psycopg2.Error as exc:
            raise PostgresExtractError(str(exc)) from exc

    def close(self) -> None:
        try:
            self._conn.close()
        except Exception:
            pass


    # Context manager support
    def __enter__(self) -> PostgresClient:
        return self

    def __exit__(self, *args: object) -> None:
        self.close