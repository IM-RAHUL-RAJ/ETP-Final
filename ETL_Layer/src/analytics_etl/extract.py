# EXTRACT PIPELINE
from __future__ import annotations

from .cache import RawResponseCache
from .client import FauxnanceClient
from .config import CACHE_DIR


def extract_candles(
    symbol: str,
    from_date: str | None = None,
    to_date: str | None = None,
    client: FauxnanceClient | None = None,
    cache: RawResponseCache | None = None,
) -> dict:
    cache  = cache  or RawResponseCache(CACHE_DIR)
    client = client or FauxnanceClient()

    # Normalise missing dates to the string "default" for cache keying
    cache_from = from_date or "default"
    cache_to   = to_date   or "default"

    cached = cache.get(symbol, cache_from, cache_to)
    if cached is not None:
        return cached

    raw = client.get_candles(symbol, from_date=from_date, to_date=to_date)
    cache.set(symbol, cache_from, cache_to, raw)
    return raw
