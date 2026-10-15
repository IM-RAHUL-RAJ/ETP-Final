# Caching the RAW response to disk
# -----------------------
# If data was fetched for example : [2026-04-01, 2026-07-11] it is cached as:
#
#   INFY.NS__2026-04-01_2026-07-11.json
#
# A later request arrived for [2026-05-01, 2026-06-01] (a sub-range)  will:
#   1. Find the existing file whose range covers [2026-05-01, 2026-06-01].
#   2. Load it from cache folder.
#   3. Filter data.candles to only the candles whose date falls inside
#      [2026-05-01, 2026-06-01].
#   4. Return the filtered response dict
#
#So API is never hit

from __future__ import annotations

import json
import os
import re
from datetime import date
from pathlib import Path


# Matches filenames like: INFY.NS__2026-04-01_2026-07-11.json
_FILENAME_RE = re.compile(
    r"^(?P<safe_symbol>.+?)__"
    r"(?P<from_date>\d{4}-\d{2}-\d{2})"
    r"_"
    r"(?P<to_date>\d{4}-\d{2}-\d{2})\.json$"
)


def _cache_key(symbol: str, from_date: str, to_date: str) -> str:
    safe_symbol = symbol.replace("/", "_").replace(":", "_")
    return f"{safe_symbol}__{from_date}_{to_date}.json"


def _safe_symbol(symbol: str) -> str:
    return symbol.replace("/", "_").replace(":", "_")


def _parse_date(value: str) -> date | None:
    try:
        return date.fromisoformat(value)
    except (ValueError, TypeError):
        return None


def _filter_candles(raw_response: dict, from_date: date, to_date: date) -> dict:
    import copy
    filtered = copy.deepcopy(raw_response)
    candles = filtered.get("data", {}).get("candles", [])
    kept = []
    for candle in candles:
        candle_date = _parse_date(candle.get("date", ""))
        if candle_date is not None and from_date <= candle_date <= to_date:
            kept.append(candle)
    filtered["data"]["candles"] = kept
    return filtered


class RawResponseCache:

    def __init__(self, cache_dir: str):
        self.cache_dir = Path(cache_dir)
        self.cache_dir.mkdir(parents=True, exist_ok=True)

    def _exact_path(self, symbol: str, from_date: str, to_date: str) -> Path:
        return self.cache_dir / _cache_key(symbol, from_date, to_date)

    def _find_covering_file(
        self,
        symbol: str,
        requested_from: date,
        requested_to: date,
    ) -> Path | None:
        
        safe = _safe_symbol(symbol)
        for entry in self.cache_dir.iterdir():
            if not entry.is_file() or not entry.name.endswith(".json"):
                continue
            m = _FILENAME_RE.match(entry.name)
            if m is None:
                continue
            # Only consider files for the same symbol
            if m.group("safe_symbol") != safe:
                continue
            cached_from = _parse_date(m.group("from_date"))
            cached_to   = _parse_date(m.group("to_date"))
            if cached_from is None or cached_to is None:
                continue
            if cached_from <= requested_from and cached_to >= requested_to:
                return entry
        return None

    def get(self, symbol: str, from_date: str, to_date: str) -> dict | None:

        exact = self._exact_path(symbol, from_date, to_date)
        if exact.exists():
            with open(exact, "r", encoding="utf-8") as f:
                return json.load(f)

        # 2. Sub-range match
        req_from = _parse_date(from_date)
        req_to   = _parse_date(to_date)

        if req_from is not None and req_to is not None:
            covering = self._find_covering_file(symbol, req_from, req_to)
            if covering is not None:
                with open(covering, "r", encoding="utf-8") as f:
                    raw = json.load(f)
                return _filter_candles(raw, req_from, req_to)

        return None

    def set(self, symbol: str, from_date: str, to_date: str, raw_response: dict) -> None:
        path     = self._exact_path(symbol, from_date, to_date)
        tmp_path = path.with_suffix(".json.tmp")
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump(raw_response, f)
        os.replace(tmp_path, path)
