import pandas as pd
import plotly.express as px

from analytics_etl.config import COMPANY_NAMES


def calculate_risk_return(
    df: pd.DataFrame,
) -> pd.DataFrame:

    data = df.copy()

    data = data.sort_values(
        ["symbol", "date"]
    )

    # Calculate daily percentage movement
    data["daily_return"] = (
        data.groupby("symbol")["close"]
        .pct_change()
    )

    result = (
        data.groupby("symbol")
        .agg(
            start_price=("close", "first"),
            end_price=("close", "last"),
            volatility=("daily_return", "std"),
        )
        .reset_index()
    )

    # Overall price return
    result["return_pct"] = (
        (
            result["end_price"]
            - result["start_price"]
        )
        / result["start_price"]
    ) * 100

    # Convert volatility to percentage
    result["volatility_pct"] = (
        result["volatility"] * 100
    )

    # Add company names for visualization
    result["company"] = (
        result["symbol"]
        .map(COMPANY_NAMES)
        .fillna(result["symbol"])
    )

    return result


def plot_risk_return(
    result: pd.DataFrame,
):

    fig = px.scatter(
        result,
        x="volatility_pct",
        y="return_pct",
        text="company",
        hover_name="company",
        hover_data={
            "symbol": False,
            "company": False,
            "return_pct": ":.2f",
            "volatility_pct": ":.2f",
            "start_price": ":.2f",
            "end_price": ":.2f",
        },
        labels={
            "volatility_pct": "Daily Volatility (%)",
            "return_pct": "Total Return (%)",
        },
        title=(
            "Apple Delivered a Positive Return While "
            "Infosys Had the Largest Loss"
        ),
    )

    # Display company names next to each point
    fig.update_traces(
        textposition="top center",
        marker=dict(size=12),
    )

    # Reference line showing zero return
    fig.add_hline(
        y=0,
        line_dash="dash",
        annotation_text="Zero Return",
        annotation_position="bottom right",
    )

    fig.update_layout(
        xaxis_title="Daily Volatility (%)",
        yaxis_title="Total Return (%)",
    )

    return fig


def save_risk_return_chart(
    result: pd.DataFrame,
    output_path: str = "risk_return.png",
) -> None:

    fig = plot_risk_return(result)

    fig.write_html(
        output_path,
        include_plotlyjs=True,
    )