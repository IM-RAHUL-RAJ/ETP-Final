import pandas as pd
import plotly.express as px

from analytics_etl.config import COMPANY_NAMES


def calculate_trading_volume(
    df: pd.DataFrame,
) -> pd.DataFrame:

    data = df.copy()

    # Add company names
    data["company"] = (
        data["symbol"]
        .map(COMPANY_NAMES)
        .fillna(data["symbol"])
    )

    data = data.sort_values(
        ["company", "date"]
    )

    result = (
        data.groupby(
            ["date", "symbol", "company"],
            as_index=False,
        )
        .agg(
            daily_volume=("volume", "sum"),
        )
    )

    return result


def plot_trading_volume(
    result: pd.DataFrame,
):

    # Find the company/date with the highest volume
    highest_volume_row = result.loc[
        result["daily_volume"].idxmax()
    ]

    highest_company = (
        highest_volume_row["company"]
    )

    highest_volume = (
        highest_volume_row["daily_volume"]
    )

    highest_date = (
        highest_volume_row["date"]
    )

    fig = px.line(
        result,
        x="date",
        y="daily_volume",
        color="company",
        labels={
            "date": "Trading Date",
            "daily_volume": "Trading Volume (shares)",
            "company": "Company",
        },
        title=(
            f"{highest_company} Recorded the Highest "
            f"Daily Trading Volume of "
            f"{highest_volume:,.0f} Shares on "
            f"{highest_date}"
        ),
    )

    fig.update_layout(
        xaxis_title="Trading Date",
        yaxis_title="Trading Volume (shares)",
    )

    return fig


def save_trading_volume_chart(
    result: pd.DataFrame,
    output_path: str = "volume_by_date.html",
) -> None:

    fig = plot_trading_volume(result)

    fig.write_html(
        output_path,
        include_plotlyjs=True,
    )