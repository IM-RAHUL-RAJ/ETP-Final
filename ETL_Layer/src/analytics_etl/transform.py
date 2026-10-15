from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from typing import Any



def parse_date(value: Any) -> date:

    if not isinstance(value, str):
        raise ValueError("date must be a string")

    value = value.strip()

    if not value:
        raise ValueError("date cannot be empty")


    try:
        return date.fromisoformat(value)

    except ValueError:
        pass


    try:
        return datetime.strptime(
            value,
            "%d/%m/%Y"
        ).date()

    except ValueError:
        raise ValueError(
            f"Invalid date format: {value}. "
            f"Expected YYYY-MM-DD or DD/MM/YYYY."
        )


def to_decimal(value: Any, field_name: str) -> Decimal:

    if value is None:
        raise ValueError(
            f"{field_name} cannot be null"
        )

    try:
        decimal_value = Decimal(
            str(value).strip()
        )

    except (InvalidOperation, ValueError, TypeError):
        raise ValueError(
            f"Invalid {field_name}: {value}"
        )

    if not decimal_value.is_finite():
        raise ValueError(
            f"{field_name} must be a finite number"
        )

    return decimal_value


def to_volume(value: Any) -> int | None:


    if value is None:
        return None

    if isinstance(value, float) and not value.is_integer():
        raise ValueError(
            f"Volume must be an integer: {value}"
        )

    if isinstance(value, Decimal):
        if value != value.to_integral_value():
            raise ValueError(
                f"Volume must be an integer: {value}"
            )
            
    if isinstance(value, str):

        value = value.strip()

        if not value:
            raise ValueError(
                "Volume cannot be empty"
            )

        try:
            decimal_volume = Decimal(value)

        except InvalidOperation:
            raise ValueError(
                f"Invalid volume: {value}"
            )

        if not decimal_volume.is_finite():
            raise ValueError(
                f"Invalid volume: {value}"
            )

        if decimal_volume != decimal_volume.to_integral_value():
            raise ValueError(
                f"Volume must be an integer: {value}"
            )

    try:
        volume = int(value)

    except (ValueError, TypeError):
        raise ValueError(
            f"Invalid volume: {value}"
        )

    if volume < 0:
        raise ValueError(
            f"Volume cannot be negative: {volume}"
        )

    return volume


def parse_bool(
    value: Any,
    field_name: str
) -> bool:
    if not isinstance(value, bool):
        raise ValueError(
            f"{field_name} must be a boolean"
        )

    return value

def validate_ohlc(
    open_price: Decimal,
    high: Decimal,
    low: Decimal,
    close: Decimal,
) -> None:

    if high < open_price:
        raise ValueError(
            "high cannot be lower than open"
        )

    if high < close:
        raise ValueError(
            "high cannot be lower than close"
        )

    if high < low:
        raise ValueError(
            "high cannot be lower than low"
        )

    if low > open_price:
        raise ValueError(
            "low cannot be higher than open"
        )

    if low > close:
        raise ValueError(
            "low cannot be higher than close"
        )

def validate_symbol(value: Any) -> str:

    if not isinstance(value, str):
        raise ValueError(
            "symbol must be a string"
        )

    symbol = value.strip()

    if not symbol:
        raise ValueError(
            "symbol cannot be empty"
        )

    if len(symbol) > 20:
        raise ValueError(
            "symbol cannot exceed 20 characters"
        )

    return symbol



def validate_interval(value: Any) -> str:

    if not isinstance(value, str):
        raise ValueError(
            "interval must be a string"
        )

    interval = value.strip()

    if not interval:
        raise ValueError(
            "interval cannot be empty"
        )

    return interval

def validate_currency(value: Any) -> str:
    
    if not isinstance(value, str):
        raise ValueError(
            "currency must be a string"
        )

    currency = value.strip().upper()

    if len(currency) != 3:
        raise ValueError(
            f"Invalid currency: {currency}"
        )

    if not currency.isalpha():
        raise ValueError(
            f"Invalid currency: {currency}"
        )

    return currency


def transform_candle(
    candle: dict,
    row_number: int,
    symbol: str,
) -> dict:
    
    if not isinstance(candle, dict):
        raise ValueError(
            f"Row {row_number}: candle must be an object"
        )


    candle_date = parse_date(
        candle.get("date")
    )

    open_price = to_decimal(
        candle.get("open"),
        "open"
    )

    high = to_decimal(
        candle.get("high"),
        "high"
    )

    low = to_decimal(
        candle.get("low"),
        "low"
    )

    close = to_decimal(
        candle.get("close"),
        "close"
    )

    adjclose = to_decimal(
        candle.get("adjclose"),
        "adjclose"
    )

    volume = to_volume(
        candle.get("volume")
    )


    synthetic = parse_bool(
        candle.get("synthetic", False),
        "synthetic"
    )

    for field_name, value in {
        "open": open_price,
        "high": high,
        "low": low,
        "close": close,
        "adjclose": adjclose,
    }.items():

        if value <= 0:
            raise ValueError(
                f"{field_name} must be greater than zero"
            )

    validate_ohlc(
        open_price,
        high,
        low,
        close,
    )


    return {
        "symbol": symbol,

        "date": candle_date,

        "date_key": int(
            candle_date.strftime("%Y%m%d")
        ),

        "open": open_price,
        "high": high,
        "low": low,
        "close": close,
        "adjclose": adjclose,

        "volume": volume,

        "synthetic": synthetic,
    }


def transform_candles(
    raw_data: dict,
    reject_malformed: bool = True,
) -> dict:

    if not isinstance(raw_data, dict):
        raise ValueError(
            "API response must be a dictionary"
        )

    data = raw_data.get("data")

    if not isinstance(data, dict):
        raise ValueError(
            "API response is missing 'data'"
        )

    symbol = validate_symbol(
        data.get("symbol")
    )
    interval = validate_interval(
        data.get("interval")
    )


    currency = validate_currency(
        data.get("currency")
    )

    candles = data.get("candles")

    if not isinstance(candles, list):
        raise ValueError(
            "'candles' must be a list"
        )

    if not candles:
        raise ValueError(
            "'candles' cannot be empty"
        )

    transformed = []

    rejected = []

    # Track dates to detect duplicates.
    seen_dates = set()

    for row_number, candle in enumerate(
        candles,
        start=1,
    ):

        try:

            transformed_candle = transform_candle(
                candle=candle,
                row_number=row_number,
                symbol=symbol,
            )

            candle_date = transformed_candle["date"]

            # Duplicate date detection
            

            if candle_date in seen_dates:
                raise ValueError(
                    f"Duplicate candle date: "
                    f"{candle_date}"
                )

            seen_dates.add(candle_date)

            transformed.append(
                transformed_candle
            )

        except ValueError as exc:

            rejected.append({
                "row": row_number,
                "reason": str(exc),
                "raw": candle,
                "symbol": symbol,
            })

            if not reject_malformed:
                raise

    transformed.sort(
        key=lambda row: row["date"]
    )

    # Metadata INFO
    meta = raw_data.get("meta")

    if not isinstance(meta, dict):
        meta = {}

    return {
        "symbol": symbol,
        "interval": interval,
        "currency": currency,

        "clean": transformed,

        "quarantine": rejected,

        "meta": {
            "as_of": meta.get("asOf"),
            "source": meta.get("source"),
            "stale": meta.get("stale"),
            "partial": meta.get("partial"),
            "available_from": meta.get("availableFrom"),
        },
    }


def transform(raw_data: dict) -> dict:

    return transform_candles(raw_data)
