from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
import duckdb
import pytest

from postgres_etl.load import load, get_watermark
from postgres_etl.transform import transform


def test_load_to_isolated_duckdb(tmp_path):
    db_file = str(tmp_path / "test_trades.duckdb")

    raw_rows = [
        {
            "source_order_id": "order-001",
            "side": "BUY",
            "status": "FILLED",
            "quantity": 10,
            "price": 150.00,
            "executed_price": 150.00,
            "created_at": datetime(2026, 1, 1, 9, 30, tzinfo=timezone.utc),
            "symbol": "AAPL",
            "instrument_id": 1,
            "instrument_name": "Apple Inc",
            "asset_class": "US_EQUITY",
            "currency": "USD",
            "exchange": "NYSE",
            "tradable": True,
            "account_id": 1,
            "account_ref": "ACC0000001",
            "holder_name": "Alice Johnson",
            "account_status": "ACTIVE",
        },
        {
            "source_order_id": "order-002",
            "side": "SELL",
            "status": "NEW",
            "quantity": 5,
            "price": 200.00,
            "executed_price": None,
            "created_at": datetime(2026, 1, 2, 10, 0, tzinfo=timezone.utc),
            "symbol": "AAPL",
            "instrument_id": 1,
            "instrument_name": "Apple Inc",
            "asset_class": "US_EQUITY",
            "currency": "USD",
            "exchange": "NYSE",
            "tradable": True,
            "account_id": 1,
            "account_ref": "ACC0000001",
            "holder_name": "Alice Johnson",
            "account_status": "ACTIVE",
        },
    ]

    transformed = transform(raw_rows)
    inserted = load(transformed, db_path=db_file)
    assert inserted == 2

    # Verify DuckDB content
    conn = duckdb.connect(db_file, read_only=True)
    trades_count = conn.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
    inst_count = conn.execute("SELECT COUNT(*) FROM dim_instrument").fetchone()[0]
    acc_count = conn.execute("SELECT COUNT(*) FROM dim_account").fetchone()[0]
    conn.close()

    assert trades_count == 2
    assert inst_count == 1
    assert acc_count == 1

    # Idempotency test: loading the same data again should not insert duplicates
    inserted_again = load(transformed, db_path=db_file)
    assert inserted_again == 0

    # Test watermark detection
    wm = get_watermark(db_path=db_file)
    assert wm.year == 2026
    assert wm.month == 1
    assert wm.day == 2

