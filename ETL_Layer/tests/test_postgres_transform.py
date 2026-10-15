from datetime import datetime, timezone
from decimal import Decimal
import pytest

from postgres_etl.transform import transform, transform_row


def test_transform_valid_row():
    raw_row = {
        "source_order_id": "order-filled-aapl-buy-001",
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
    }

    result = transform([raw_row])
    assert len(result["clean"]) == 1
    assert len(result["quarantine"]) == 0

    clean_row = result["clean"][0]
    assert clean_row["source_order_id"] == "order-filled-aapl-buy-001"
    assert clean_row["side"] == "BUY"
    assert clean_row["status"] == "FILLED"
    assert clean_row["quantity"] == 10
    assert clean_row["price"] == Decimal("150.00")
    assert clean_row["executed_price"] == Decimal("150.00")
    assert clean_row["trade_value"] == Decimal("1500.00")
    assert clean_row["date_key"] == 20260101


def test_transform_quarantine_bad_data():
    invalid_rows = [
        # Invalid side
        {
            "source_order_id": "bad-side",
            "side": "INVALID",
            "status": "NEW",
            "quantity": 5,
            "price": 100.0,
            "created_at": datetime.now(timezone.utc),
            "symbol": "AAPL",
            "instrument_id": 1,
            "account_id": 1,
            "account_ref": "ACC0001",
            "holder_name": "Test",
            "account_status": "ACTIVE",
        },
        # Quantity <= 0
        {
            "source_order_id": "bad-qty",
            "side": "BUY",
            "status": "NEW",
            "quantity": 0,
            "price": 100.0,
            "created_at": datetime.now(timezone.utc),
            "symbol": "AAPL",
            "instrument_id": 1,
            "account_id": 1,
            "account_ref": "ACC0001",
            "holder_name": "Test",
            "account_status": "ACTIVE",
        },
        # Negative price
        {
            "source_order_id": "bad-price",
            "side": "SELL",
            "status": "NEW",
            "quantity": 10,
            "price": -50.0,
            "created_at": datetime.now(timezone.utc),
            "symbol": "AAPL",
            "instrument_id": 1,
            "account_id": 1,
            "account_ref": "ACC0001",
            "holder_name": "Test",
            "account_status": "ACTIVE",
        },
    ]

    result = transform(invalid_rows)
    assert len(result["clean"]) == 0
    assert len(result["quarantine"]) == 3

