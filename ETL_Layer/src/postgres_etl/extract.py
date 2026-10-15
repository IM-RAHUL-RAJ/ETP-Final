from __future__ import annotations

import logging
from datetime import datetime
from typing import Any

from .client import PostgresClient

logger = logging.getLogger(__name__)


def extract_orders(
    watermark: datetime,
    client: PostgresClient | None = None,
) -> list[dict[str, Any]]:
    owned = client is None
    if owned:
        client = PostgresClient()

    try:
        rows = client.get_orders_since(watermark)
        logger.info("EXTRACT complete: %d raw rows", len(rows))
        return rows
    finally:
        if owned:
            client.close()
