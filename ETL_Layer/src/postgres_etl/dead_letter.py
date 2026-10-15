from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from typing import Any
from uuid import uuid4

import duckdb

logger = logging.getLogger(__name__)


def _generate_dead_letter_key() -> int:
    import hashlib
    ts = datetime.now(tz=timezone.utc).isoformat()
    return int(
        hashlib.sha256(f"{ts}-{uuid4()}".encode("utf-8")).hexdigest()[:15], 16
    )


def record_dead_letter(
    conn: duckdb.DuckDBPyConnection,
    source_order_id: str,
    reason: str,
    batch_id: str,
    raw_row: dict[str, Any],
) -> bool:
    dead_letter_key = _generate_dead_letter_key()
    raw_json = json.dumps(raw_row, default=str)

    try:
        result = conn.execute(
            """
            INSERT INTO dead_letter_trades
            (dead_letter_key, source_order_id, failure_reason, batch_id, raw_row)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            RETURNING dead_letter_key
            """,
            [dead_letter_key, source_order_id, reason, batch_id, raw_json],
        ).fetchone()

        if result:
            logger.warning(
                "DEAD_LETTER: source_order_id=%r reason=%s batch=%s",
                source_order_id,
                reason,
                batch_id,
            )
            return True
        return False

    except Exception as exc:
        logger.error(
            "Failed to record dead letter for %r: %s",
            source_order_id,
            exc,
        )
        raise


def get_dead_letter_count(
    conn: duckdb.DuckDBPyConnection,
    batch_id: str | None = None,
) -> int:
    if batch_id:
        result = conn.execute(
            "SELECT COUNT(*) FROM dead_letter_trades WHERE batch_id = ?",
            [batch_id],
        ).fetchone()
    else:
        result = conn.execute(
            "SELECT COUNT(*) FROM dead_letter_trades"
        ).fetchone()

    return result[0] if result else 0
