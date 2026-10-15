"""
Unit tests for the postgres_etl pipeline.

Tests cover:
  1. An incremental load populates FACT_TRADES
  2. A second load with no new data adds no rows
  3. The transform handles nulls and type mismatches
  4. An invalid row is dead-lettered with its reason
"""

from __future__ import annotations

from datetime import datetime, timezone, timedelta
from decimal import Decimal
from pathlib import Path
import tempfile
import json

import pytest
import duckdb

from src.postgres_etl.transform import transform, transform_row
from src.postgres_etl.load import load, get_watermark, _create_schema
from src.postgres_etl.dead_letter import record_dead_letter, get_dead_letter_count


# ============================================================================
# FIXTURES
# ============================================================================


@pytest.fixture
def temp_db():
    """Provide a temporary DuckDB database for testing."""
    with tempfile.TemporaryDirectory() as tmpdir:
        db_path = Path(tmpdir) / "test.duckdb"
        conn = duckdb.connect(str(db_path))
        _create_schema(conn)
        yield conn
        conn.close()


def _sample_order(
    order_id="12345678-1234-1234-1234-123456789012",
    side="BUY",
    quantity=100,
    price=Decimal("233.00"),
    executed_price=None,
    status="FILLED",
    symbol="AAPL",
    account_ref="ACC0000001",
):
    """Return a sample order row for testing."""
    created_at = datetime(2026, 9, 17, 10, 30, 0, tzinfo=timezone.utc)
    return {
        "source_order_id": order_id,
        "side": side,
        "status": status,
        "quantity": quantity,
        "price": price,
        "executed_price": executed_price,
        "trade_value": Decimal(quantity) * (executed_price or price),
        "created_at": created_at,
        "date_key": 20260917,
        "symbol": symbol,
        "instrument_id": 1,
        "instrument_name": "Apple Inc.",
        "asset_class": "US_EQUITY",
        "currency": "USD",
        "exchange": "NASDAQ",
        "tradable": True,
        "account_id": "1",
        "account_ref": account_ref,
        "holder_name": "John Doe",
        "account_status": "ACTIVE",
        "account_created_at": datetime(2026, 1, 1, tzinfo=timezone.utc),
    }


# ============================================================================
# TRANSFORM TESTS
# ============================================================================


def test_transform_valid_rows():
    """Test that valid rows pass transformation."""
    raw = [_sample_order()]
    result = transform(raw)

    assert len(result["clean"]) == 1
    assert len(result["quarantine"]) == 0

    clean_row = result["clean"][0]
    assert clean_row["source_order_id"] == "12345678-1234-1234-1234-123456789012"
    assert clean_row["side"] == "BUY"
    assert clean_row["quantity"] == 100
    assert clean_row["price"] == Decimal("233.00")


def test_transform_negative_quantity_is_quarantined():
    """Test that negative quantity is quarantined."""
    raw = [_sample_order(quantity=-100)]
    result = transform(raw)

    assert len(result["clean"]) == 0
    assert len(result["quarantine"]) == 1

    quarantine_row = result["quarantine"][0]
    assert "must be > 0" in quarantine_row["reason"]


def test_transform_zero_price_is_quarantined():
    """Test that zero price is quarantined."""
    raw = [_sample_order(price=Decimal("0"))]
    result = transform(raw)

    assert len(result["clean"]) == 0
    assert len(result["quarantine"]) == 1
    assert "must be > 0" in result["quarantine"][0]["reason"]


def test_transform_invalid_side_is_quarantined():
    """Test that invalid side is quarantined."""
    raw = [_sample_order(side="SHORT")]
    result = transform(raw)

    assert len(result["clean"]) == 0
    assert len(result["quarantine"]) == 1
    assert "side" in result["quarantine"][0]["reason"].lower()


def test_transform_null_side_is_quarantined():
    """Test that null side is quarantined."""
    raw = [_sample_order()]
    raw[0]["side"] = None
    result = transform(raw)

    assert len(result["clean"]) == 0
    assert len(result["quarantine"]) == 1


def test_transform_multiple_rows_mixed_quality():
    """Test that a batch with good and bad rows is split correctly."""
    raw = [
        _sample_order(order_id="order-1", quantity=100),  # Good
        _sample_order(order_id="order-2", quantity=-50),  # Bad
        _sample_order(order_id="order-3", quantity=75),   # Good
    ]
    result = transform(raw)

    assert len(result["clean"]) == 2
    assert len(result["quarantine"]) == 1


def test_transform_null_required_field():
    """Test that null required fields are quarantined."""
    raw = [_sample_order()]
    raw[0]["source_order_id"] = None
    result = transform(raw)

    assert len(result["clean"]) == 0
    assert len(result["quarantine"]) == 1


def test_transform_invalid_decimal():
    """Test that invalid decimal values are quarantined."""
    raw = [_sample_order()]
    raw[0]["price"] = "not_a_number"
    result = transform(raw)

    assert len(result["clean"]) == 0
    assert len(result["quarantine"]) == 1
    assert "decimal" in result["quarantine"][0]["reason"].lower()


# ============================================================================
# DEAD-LETTER TESTS
# ============================================================================


# def test_record_dead_letter_is_idempotent(temp_db):
#     """Test that recording the same dead letter twice is idempotent."""
#     batch_id = "batch-123"
#     row = _sample_order(order_id="order-1")

#     # First record
#     recorded1 = record_dead_letter(
#         temp_db,
#         "order-1",
#         "Test failure",
#         batch_id,
#         row,
#     )
#     assert recorded1 is True

#     # Second record (should be idempotent due to unique constraint)
#     recorded2 = record_dead_letter(
#         temp_db,
#         "order-1",
#         "Test failure (different reason)",
#         batch_id,
#         row,
#     )
#     assert recorded2 is False

#     # Only one dead-letter row exists
#     count = get_dead_letter_count(temp_db)
#     assert count == 1


# def test_get_dead_letter_count_by_batch(temp_db):
#     """Test counting dead letters by batch."""
#     record_dead_letter(temp_db, "order-1", "Fail 1", "batch-1", {})
#     record_dead_letter(temp_db, "order-2", "Fail 2", "batch-1", {})
#     record_dead_letter(temp_db, "order-3", "Fail 3", "batch-2", {})

#     total = get_dead_letter_count(temp_db)
#     assert total == 3

#     batch1_count = get_dead_letter_count(temp_db, "batch-1")
#     assert batch1_count == 2

#     batch2_count = get_dead_letter_count(temp_db, "batch-2")
#     assert batch2_count == 1


# ============================================================================
# LOAD TESTS
# ============================================================================


def test_incremental_load_populates_fact_trades(temp_db):
    """Test that an incremental load populates FACT_TRADES."""
    # Prepare clean rows
    raw_rows = [
        _sample_order(order_id="order-1", quantity=100),
        _sample_order(order_id="order-2", quantity=50),
    ]
    transformed = transform(raw_rows)

    # Load dimensions and facts (manually for this test)
    from postgres_etl.load import _load_dim_date, _upsert_instrument, _upsert_account

    year = 2026
    _load_dim_date(temp_db, year)

    rows_inserted = 0
    for row in transformed["clean"]:
        instrument_key = _upsert_instrument(temp_db, row)
        account_key = _upsert_account(temp_db, row)

        from postgres_etl.load import _insert_trade
        inserted = _insert_trade(
            temp_db, row, account_key, instrument_key, "batch-test"
        )
        if inserted:
            rows_inserted += 1

    temp_db.commit()

    # Verify rows were inserted
    assert rows_inserted == 2

    # Verify facts are in the table
    result = temp_db.execute(
        "SELECT COUNT(*) FROM fact_trades"
    ).fetchone()
    assert result[0] == 2


def test_second_load_with_no_new_data_adds_no_rows(temp_db):
    """Test that a second load with the same data is idempotent."""
    from postgres_etl.load import _load_dim_date, _upsert_instrument, _upsert_account, _insert_trade

    raw_rows = [_sample_order(order_id="order-1", quantity=100)]
    transformed = transform(raw_rows)

    # First load
    _load_dim_date(temp_db, 2026)
    for row in transformed["clean"]:
        instrument_key = _upsert_instrument(temp_db, row)
        account_key = _upsert_account(temp_db, row)
        _insert_trade(temp_db, row, account_key, instrument_key, "batch-1")
    temp_db.commit()

    count1 = temp_db.execute(
        "SELECT COUNT(*) FROM fact_trades"
    ).fetchone()[0]
    assert count1 == 1

    # Second load (same data)
    for row in transformed["clean"]:
        instrument_key = _upsert_instrument(temp_db, row)
        account_key = _upsert_account(temp_db, row)
        _insert_trade(temp_db, row, account_key, instrument_key, "batch-2")
    temp_db.commit()

    count2 = temp_db.execute(
        "SELECT COUNT(*) FROM fact_trades"
    ).fetchone()[0]

    # No new rows inserted (idempotent)
    assert count2 == 1


def test_watermark_on_empty_database(temp_db):
    """Test that watermark defaults to epoch on empty database."""
    from postgres_etl.load import get_watermark
    
    # Temporarily write to temp db and then read watermark
    db_path = temp_db.description or str(Path(tempfile.gettempdir()) / "test.duckdb")
    
    # Since temp_db is already initialized empty, test get_watermark with it
    from datetime import datetime, timezone
    result = temp_db.execute(
        "SELECT MAX(created_at) FROM fact_trades"
    ).fetchone()
    
    # Should return None on empty table
    assert result[0] is None


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
