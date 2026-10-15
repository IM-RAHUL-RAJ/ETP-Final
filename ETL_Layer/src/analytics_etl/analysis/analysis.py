from pathlib import Path

import duckdb
import pandas as pd


def load_candles(
    database_path = Path(__file__).resolve().parent.parent / "analytics.duckdb",
) -> pd.DataFrame:

    conn = duckdb.connect(
        database_path,
        read_only=True,
    )

    query = """
    SELECT
        fc.candle_key,
        di.symbol,
        fc.instrument_key,
        fc.date_key,
        dd.full_date AS date,
        fc.open,
        fc.high,
        fc.low,
        fc.close,
        fc.adjclose,
        fc.volume,
        fc.synthetic,
        fc.loaded_at
    FROM fact_candles fc
    JOIN dim_instrument di
        ON fc.instrument_key = di.instrument_key
    JOIN dim_date dd
        ON fc.date_key = dd.date_key
    ORDER BY di.symbol, dd.full_date
    """
    df = conn.execute(query).fetchdf()

    conn.close()

    return df