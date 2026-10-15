import pandas as pd
import plotly.express as px

from analytics_etl.config import COMPANY_NAMES


def calculate_max_drawdown(
    df: pd.DataFrame,
) -> pd.DataFrame:

    data = df.copy()

    # Add company names
    data["company"] = (
        data["symbol"]
        .map(COMPANY_NAMES)
        .fillna(data["symbol"])
    )

    # Ensure chronological order
    data = data.sort_values(
        ["symbol", "date"]
    )

    # Highest closing price reached so far
    data["running_peak"] = (
        data.groupby("symbol")["close"]
        .cummax()
    )

    # Percentage decline from the previous peak
    data["drawdown_pct"] = (
        (
            data["close"]
            - data["running_peak"]
        )
        / data["running_peak"]
    ) * 100

    # Worst drawdown for each company
    result = (
        data.groupby(
            ["symbol", "company"],
            as_index=False,
        )
        .agg(
            maximum_drawdown_pct=(
                "drawdown_pct",
                "min",
            ),
        )
        .sort_values(
            "maximum_drawdown_pct"
        )
    )

    return result


def plot_max_drawdown(
    result: pd.DataFrame,
):

    # The most negative value is the largest drawdown
    worst_company = result.iloc[0]["company"]

    worst_drawdown = result.iloc[0][
        "maximum_drawdown_pct"
    ]

    fig = px.bar(
        result,
        x="company",
        y="maximum_drawdown_pct",
        text="maximum_drawdown_pct",
        labels={
            "company": "Company",
            "maximum_drawdown_pct": (
                "Maximum Drawdown (%)"
            ),
        },
        title=(
            f"{worst_company} Experienced the "
            f"Largest Peak-to-Trough Decline "
            f"of {worst_drawdown:.2f}%"
        ),
    )

    fig.update_traces(
        texttemplate="%{text:.2f}%",
        textposition="outside",
    )

    fig.update_layout(
        xaxis_title="Company",
        yaxis_title="Maximum Drawdown (%)",
    )

    return fig


def save_max_drawdown_chart(
    result: pd.DataFrame,
    output_path: str = "drawdown.html",
) -> None:

    fig = plot_max_drawdown(result)

    fig.write_html(
        output_path,
        include_plotlyjs=True,
    )