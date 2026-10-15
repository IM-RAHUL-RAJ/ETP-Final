import json
from pathlib import Path
from pprint import pprint

import pytest

from analytics_etl.transform import transform_candles


PROJECT_ROOT = Path(__file__).resolve().parents[1]
FIXTURES_DIR = PROJECT_ROOT / "fixtures"


def load_fixture(filename: str) -> dict:
    path = FIXTURES_DIR / filename

    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def test_duplicate_date_is_quarantined():
    raw = load_fixture("candles-malformed.json")

    result = transform_candles(raw)

    matching_rows = [
        item
        for item in result["quarantine"]
        if "Duplicate candle date" in item["reason"]
    ]

    assert len(matching_rows) == 1

    duplicate_row = matching_rows[0]["raw"]

    assert duplicate_row["date"] == "2026-07-01"
    assert duplicate_row["close"] == 168.95

    july_1_rows = [
        row
        for row in result["clean"]
        if str(row["date"]) == "2026-07-01"
    ]

    assert len(july_1_rows) == 1
    assert july_1_rows[0]["close"] == 169.5


def test_missing_close_is_quarantined():
    raw = load_fixture("candles-malformed.json")

    result = transform_candles(raw)

    matching_rows = [
        item
        for item in result["quarantine"]
        if "close cannot be null" in item["reason"]
    ]

    assert len(matching_rows) == 1

    bad_row = matching_rows[0]["raw"]

    assert bad_row["date"] == "2026-07-02"
    assert "close" not in bad_row


def test_non_numeric_value_is_quarantined():
    raw = load_fixture("candles-malformed.json")

    result = transform_candles(raw)

    matching_rows = [
        item
        for item in result["quarantine"]
        if "Invalid open" in item["reason"]
    ]

    assert len(matching_rows) == 1

    bad_row = matching_rows[0]["raw"]

    assert bad_row["date"] == "2026-07-06"
    assert bad_row["open"] == "n/a"


def test_high_below_low_is_not_loaded():
    raw = load_fixture("candles-malformed.json")

    result = transform_candles(raw)

    clean_dates = [
        str(row["date"])
        for row in result["clean"]
    ]

    assert "2026-07-07" not in clean_dates

    matching_rows = [
        item
        for item in result["quarantine"]
        if "high cannot be lower than open" in item["reason"]
    ]

    assert len(matching_rows) == 1

    bad_row = matching_rows[0]["raw"]

    assert bad_row["date"] == "2026-07-07"
    assert bad_row["high"] == 168.1
    assert bad_row["low"] == 175.85


def test_negative_volume_is_quarantined():
    raw = load_fixture("candles-malformed.json")

    result = transform_candles(raw)

    matching_rows = [
        item
        for item in result["quarantine"]
        if "Volume cannot be negative" in item["reason"]
    ]

    assert len(matching_rows) == 1

    bad_row = matching_rows[0]["raw"]

    assert bad_row["date"] == "2026-07-08"
    assert bad_row["volume"] == -1


def test_dd_mm_yyyy_date_is_normalised():
    raw = load_fixture("candles-malformed.json")

    result = transform_candles(raw)

    july_9_rows = [
        row
        for row in result["clean"]
        if str(row["date"]) == "2026-07-09"
    ]

    assert len(july_9_rows) == 1

    candle = july_9_rows[0]

    assert candle["date_key"] == 20260709


def test_malformed_fixture():
    # Result has cleaned and quarantined rows

    raw = load_fixture("candles-malformed.json")

    result = transform_candles(raw)

    print("\n========== CLEAN CANDLES ==========")

    for row in result["clean"]:
        pprint(row)

    print("\n========== QUARANTINED CANDLES ==========")

    for item in result["quarantine"]:
        print("\nReason:")
        print(item["reason"])

        print("Original row:")
        pprint(item["raw"])

    assert len(result["clean"]) == 2
    assert len(result["quarantine"]) == 5

    reasons = [
        item["reason"]
        for item in result["quarantine"]
    ]

    assert any("Duplicate candle date" in r for r in reasons)
    assert any("close cannot be null" in r for r in reasons)
    assert any("Invalid open" in r for r in reasons)
    assert any("high cannot be lower than open" in r for r in reasons)
    assert any("Volume cannot be negative" in r for r in reasons)