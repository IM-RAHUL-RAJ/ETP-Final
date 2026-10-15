import json
from datetime import date
from pathlib import Path
import pytest

from analytics_etl.cache import RawResponseCache, _filter_candles


def test_filter_candles():
    sample = {
        "data": {
            "symbol": "INFY.NS",
            "candles": [
                {"date": "2026-04-10", "open": 100, "close": 110},
                {"date": "2026-05-01", "open": 105, "close": 115},
                {"date": "2026-06-01", "open": 110, "close": 120},
                {"date": "2026-07-11", "open": 115, "close": 125},
            ]
        }
    }
    filtered = _filter_candles(sample, date(2026, 5, 1), date(2026, 6, 1))
    candles = filtered["data"]["candles"]
    assert len(candles) == 2
    assert candles[0]["date"] == "2026-05-01"
    assert candles[1]["date"] == "2026-06-01"


def test_subrange_cache_hit(tmp_path):
    cache = RawResponseCache(str(tmp_path))
    symbol = "INFY.NS"
    from_date = "2026-04-10"
    to_date = "2026-07-11"

    full_payload = {
        "data": {
            "symbol": symbol,
            "candles": [
                {"date": "2026-04-10", "open": 100, "close": 110},
                {"date": "2026-05-01", "open": 105, "close": 115},
                {"date": "2026-06-01", "open": 110, "close": 120},
                {"date": "2026-07-11", "open": 115, "close": 125},
            ]
        }
    }

    cache.set(symbol, from_date, to_date, full_payload)

    exact_hit = cache.get(symbol, from_date, to_date)
    assert exact_hit is not None
    assert len(exact_hit["data"]["candles"]) == 4

    sub_hit = cache.get(symbol, "2026-05-01", "2026-06-01")
    assert sub_hit is not None
    candles = sub_hit["data"]["candles"]
    assert len(candles) == 2
    assert candles[0]["date"] == "2026-05-01"
    assert candles[1]["date"] == "2026-06-01"

    miss = cache.get(symbol, "2026-01-01", "2026-04-09")
    assert miss is None

    partial_miss = cache.get(symbol, "2026-03-01", "2026-05-01")
    assert partial_miss is None

