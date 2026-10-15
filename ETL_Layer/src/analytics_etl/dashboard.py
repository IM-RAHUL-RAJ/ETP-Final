import duckdb
import pandas as pd
import plotly.graph_objects as go
from plotly.offline import plot
from pathlib import Path

from analytics_etl.config import SYMBOL_UNIVERSE

# Path
BASE_DIR = Path(__file__).resolve().parent
DATABASE_FILE = str(BASE_DIR / "analytics.duckdb")
OUTPUT_FILE = "market_analytics_dashboard.html"

INCLUDE_SYNTHETIC = False


def load_market_data(connection):
    query = """
    WITH ranked_candles AS (
        SELECT
            c.*,
            ROW_NUMBER() OVER (
                PARTITION BY c.instrument_key, c.date_key
                ORDER BY c.loaded_at DESC, c.candle_key DESC
            ) AS rn
        FROM fact_candles c
    )
    SELECT
        i.symbol,
        i.name,
        d.full_date,

        r.open,
        r.high,
        r.low,
        r.close,
        r.synthetic

    FROM ranked_candles r
    JOIN dim_instrument i ON r.instrument_key = i.instrument_key
    JOIN dim_date d       ON r.date_key       = d.date_key
    WHERE r.rn = 1
    ORDER BY i.symbol, d.full_date
    """
    
    df = connection.execute(query).df()

    # Populate/fallback instrument name from SYMBOL_UNIVERSE
    df["name"] = df["name"].fillna(df["symbol"].map(SYMBOL_UNIVERSE)).fillna(df["symbol"])

    if not INCLUDE_SYNTHETIC:
        df = df[df["synthetic"] == False].copy()

    return df


#Generate a HTML dashboard with the candles data

def generate_html(df):
    html = """
<html>
<head>
<title>Market Candlestick Dashboard</title>
<style>
body { font-family: Arial, sans-serif; background:#f5f7fa; margin:40px; color:#222; }
h1 { color:#1a1a1a; }
h2 { margin-top:40px; border-bottom:2px solid #e0e0e0; padding-bottom:8px; }
.cards { display:flex; flex-wrap:wrap; gap:16px; }
.card {
    background:white; padding:18px 22px; border-radius:12px;
    text-align:center; box-shadow:0px 2px 8px #ccc; min-width:170px;
}
.card h3 { margin:0 0 8px 0; font-size:13px; color:#666; text-transform:uppercase; letter-spacing:0.5px; }
.card h2 { margin:0; border:none; padding:0; font-size:20px; color:#111; }
table { background:white; border-collapse:collapse; width:100%; margin-top:10px; }
th,td { padding:10px; border:1px solid #ddd; text-align:left; font-size:14px; }
th { background:#f0f2f5; }
</style>
</head>
<body>
<h1>\U0001F4C8 Market Candlestick Dashboard</h1>
"""

    metrics = {
        "Total Instruments": df["symbol"].nunique(),
        "Total Trading Days": df["full_date"].nunique(),
        "Trading Date Range": f"{df['full_date'].min().date()} \u2192 {df['full_date'].max().date()}",
    }

    html += '<div class="cards">'
    for key, value in metrics.items():
        html += f'<div class="card"><h3>{key}</h3><h2>{value}</h2></div>'
    html += "</div>"

    html += "<h2>Instruments</h2>"
    instrument_table = df[["symbol", "name"]].drop_duplicates().rename(
        columns={"symbol": "Ticker", "name": "Name"}
    )
    html += instrument_table.to_html(index=False)


    html += "<h2>Candlestick Charts</h2>"
    for i, symbol in enumerate(df.symbol.unique()):
        stock = df[df.symbol == symbol]
        candle = go.Figure(data=[go.Candlestick(
            x=stock.full_date, open=stock.open, high=stock.high,
            low=stock.low, close=stock.close
        )])
        candle.update_layout(title=f"{symbol} Candlestick Chart", xaxis_rangeslider_visible=False)
        html += plot(candle, output_type="div", include_plotlyjs="cdn" if i == 0 else False)

    html += "</body></html>"

    Path(OUTPUT_FILE).write_text(html, encoding="utf-8")
    print(f"[OK] Dashboard generated: {OUTPUT_FILE}")



def generate_dashboard():
    connection = duckdb.connect(DATABASE_FILE, read_only=True)
    try:
        df = load_market_data(connection)
        if df.empty:
            raise Exception("No candle data available")
        generate_html(df)
    finally:
        connection.close()

