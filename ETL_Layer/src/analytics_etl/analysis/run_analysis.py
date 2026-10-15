from analytics_etl.analysis.analysis import (
    load_candles,
)

from analytics_etl.analysis.risk_return import (
    calculate_risk_return,
    save_risk_return_chart,
)

from analytics_etl.analysis.volume import (
    calculate_trading_volume,
    save_trading_volume_chart,
)

from analytics_etl.analysis.drawdown import (
    calculate_max_drawdown,
    save_max_drawdown_chart,
)

from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
ANALYSIS_DIR = BASE_DIR / "charts"
ANALYSIS_DIR.mkdir(
    parents=True,
    exist_ok=True
)



def run_analysis():

    df = load_candles()

    print("\nDATA LOADED")
    print("=" * 40)
    print(f"Rows: {len(df)}")
    print(f"Companies: {df['symbol'].unique()}")

    # CLAIM 1: RISK VS RETURN
    risk_return = calculate_risk_return(df)

    print("\nRISK VS RETURN")
    print("=" * 40)
    print(risk_return)

    save_risk_return_chart(
        risk_return,
        str(ANALYSIS_DIR / "risk_return.html"),
    )

    print("risk_return.html created")

    # CLAIM 2: TRADING VOLUME
    volume = calculate_trading_volume(df)
    print("\nTRADING VOLUME")
    print("=" * 40)
    print(volume.head())

    save_trading_volume_chart(
        volume,
        str(ANALYSIS_DIR / "volume_by_date.html"),
    )

    print("volume_by_date.html created")

    # CLAIM 3: MAXIMUM DRAWDOWN
    drawdown = calculate_max_drawdown(df)

    print("\nMAXIMUM DRAWDOWN")
    print("=" * 40)
    print(drawdown)

    save_max_drawdown_chart(
        drawdown,
        str(ANALYSIS_DIR / "drawdown.html"),
    )

    print("drawdown.html created")

    print("\n" + "=" * 40)
    print("ALL ANALYSIS COMPLETED")
    print("=" * 40)


