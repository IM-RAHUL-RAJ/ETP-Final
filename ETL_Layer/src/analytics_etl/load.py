from pathlib import Path
import duckdb
from datetime import date, timedelta

from .config import SYMBOL_UNIVERSE

BASE_DIR = Path(__file__).resolve().parent

SCHEMA_FILE = BASE_DIR / "contracts" / "analytics-schema.sql"
DATABASE_FILE = BASE_DIR / "analytics.duckdb"


EXPECTED_TABLES = {
    "dim_account",
    "dim_instrument",
    "dim_date",
    "fact_trades",
    "fact_candles",
}


EXPECTED_COLUMNS = {
    "dim_account": {
        "account_key",
        "account_id",
        "holder_name",
        "status",
        "effective_date",
        "end_date",
        "is_current",
        "source_id",
        "loaded_at",
    },
    "dim_instrument": {
        "instrument_key",
        "symbol",
        "name",
        "asset_class",
        "currency",
        "exchange",
        "tradable",
        "loaded_at",
    },
    "dim_date": {
        "date_key",
        "full_date",
        "day",
        "month",
        "year",
        "quarter",
        "day_of_week",
        "day_name",
        "month_name",
        "is_weekday",
    },
    "fact_trades": {
        "trade_key",
        "account_key",
        "instrument_key",
        "date_key",
        "side",
        "quantity",
        "price",
        "status",
        "executed_price",
        "trade_value",
        "source_order_id",
        "created_at",
        "loaded_at",
    },
    "fact_candles": {
        "candle_key",
        "instrument_key",
        "date_key",
        "open",
        "high",
        "low",
        "close",
        "adjclose",
        "volume",
        "synthetic",
        "loaded_at",
    },
}



def create_schema(connection):

    if not SCHEMA_FILE.exists():

        raise FileNotFoundError(f"Schema not found: {SCHEMA_FILE}")

    schema_sql = SCHEMA_FILE.read_text(encoding="utf-8")

    connection.execute(schema_sql)

    print("Schema created")



def load_dim_date(connection):

    start_date = date(2026, 1, 1)
    end_date = date(2026, 12, 31)

    current = start_date

    rows = 0

    while current <= end_date:

        date_key = int(current.strftime("%Y%m%d"))

        exists = connection.execute(
            """
            SELECT 1
            FROM dim_date
            WHERE date_key = ?
            """,
            [date_key],
        ).fetchone()

        if not exists:

            connection.execute(
                """
                INSERT INTO dim_date
                (
                    date_key,
                    full_date,
                    day,
                    month,
                    year,
                    quarter,
                    day_of_week,
                    day_name,
                    month_name,
                    is_weekday
                )

                VALUES
                (?,?,?,?,?,?,?,?,?,?)
                """,
                [
                    date_key,
                    current,
                    current.day,
                    current.month,
                    current.year,
                    ((current.month - 1) // 3) + 1,
                    current.weekday() + 1,
                    current.strftime("%A"),
                    current.strftime("%B"),
                    current.weekday() < 5,
                ],
            )

            rows += 1

        current += timedelta(days=1)

    print(f"DIM_DATE loaded: {rows} rows")

# Load DIM INSTRUMENT
def load_dim_instrument(connection, transformed_data):
    symbol = transformed_data["symbol"]

    existing = connection.execute(
        """
        SELECT instrument_key
        FROM dim_instrument
        WHERE symbol = ?
        """,
        [symbol],
    ).fetchone()

    name = SYMBOL_UNIVERSE.get(symbol, symbol)

    if existing:
        # Update name if previously null
        connection.execute(
            """
            UPDATE dim_instrument
            SET name = COALESCE(name, ?)
            WHERE symbol = ?
            """,
            [name, symbol],
        )
        return existing[0]

    instrument_key = connection.execute("""
        SELECT COALESCE(
            MAX(instrument_key),
            0
        ) + 1

        FROM dim_instrument
        """).fetchone()[0]

    meta = transformed_data["meta"]

    connection.execute(
        """
        INSERT INTO dim_instrument
        (
            instrument_key,
            symbol,
            name,
            currency,
            interval,
            source,
            stale,
            partial,
            available_from,
            as_of,
            tradable
        )


        VALUES
        (?,?,?,?,?,?,?,?,?,?,?)

        """,
        [
            instrument_key,
            symbol,
            name,
            transformed_data["currency"],
            transformed_data["interval"],
            meta.get("source"),
            meta.get("stale"),
            meta.get("partial"),
            meta.get("available_from"),
            meta.get("as_of"),
            True,
        ],
    )

    print(f"DIM_INSTRUMENT loaded: {symbol} ({name})")

    return instrument_key


def load_fact_candles(connection, transformed_data):

    instrument_key = connection.execute(
        """
        SELECT instrument_key
        FROM dim_instrument
        WHERE symbol = ?
        """,
        [transformed_data["symbol"]],
    ).fetchone()[0]

    rows = 0

    for candle in transformed_data["clean"]:

        candle_key = int(f"{instrument_key}" f"{candle['date_key']}")

        result = connection.execute(
            """
            INSERT INTO fact_candles
            (
                candle_key,
                instrument_key,
                date_key,
                open,
                high,
                low,
                close,
                adjclose,
                volume,
                synthetic
            )

            VALUES
            (?,?,?,?,?,?,?,?,?,?)

            ON CONFLICT DO NOTHING

            RETURNING candle_key

            """,
            [
                candle_key,
                instrument_key,
                candle["date_key"],
                candle["open"],
                candle["high"],
                candle["low"],
                candle["close"],
                candle["adjclose"],
                candle["volume"],
                candle["synthetic"],
            ],
        )

        # Increment ONLY if actual insertion happened
        inserted = result.fetchone()

        if inserted:
            rows += 1

    print(f"FACT_CANDLES inserted: {rows} rows")

    return rows


def verify_schema(connection):

    print("\nFACT_CANDLES SCHEMA")

    result = connection.execute("""
        DESCRIBE fact_candles
        """).fetchall()


def display_loaded_tables(connection):

    print("\n==============================")
    print("DIM_INSTRUMENT")
    print("==============================")

    rows = connection.execute("""
        SELECT *
        FROM dim_instrument
        """).fetchall()

    df = connection.execute("""
            SELECT *
            FROM dim_instrument
            """).df()
    print(df)

    print("\n==============================")
    print("FACT_CANDLES")
    print("==============================")

    rows = connection.execute("""
        SELECT *
        FROM fact_candles
        ORDER BY date_key
        """).fetchall()


def load(transformed_data):
    # Return the number of rows inserted in each table

    connection = duckdb.connect(str(DATABASE_FILE))

    try:

        print("=" * 60)

        print("MARKET ANALYTICS LOAD")

        print("=" * 60)

        # 1. Create schema

        create_schema(connection)

        # 2. Load dim_date

        load_dim_date(connection)

        # 3. Load instrument

        load_dim_instrument(connection, transformed_data)

        # 4. Load candles

        rows_loaded = load_fact_candles(connection, transformed_data)

        connection.commit()

        verify_schema(connection)

        display_loaded_tables(connection)

        print("\nLOAD COMPLETED SUCCESSFULLY")

        return rows_loaded

    except Exception:

        try:
            connection.rollback()
        except Exception:
            pass  # ignore if no active transaction

        raise

    finally:

        connection.close()
