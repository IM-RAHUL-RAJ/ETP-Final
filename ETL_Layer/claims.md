# Market Analytics Business Claims

## Claim 1: Risk vs Return Analysis

### Business Claim
During the observation period, **Apple Inc. (AAPL) generated the highest total return among the analysed companies while maintaining moderate daily volatility compared with Infosys Limited and Reliance Industries Limited.**

### Chart Artefact
`risk_return.html`

### Business Question
Which company generated the highest return, and was that return achieved with high or low risk?

### Analysis Performed

The analysis compares company performance using return and risk measures.

**Total Return (%)**

Measures the overall change in closing price from the start to the end of the period.

Formula:

```text
Return (%) =
((Ending Price - Starting Price) / Starting Price) × 100
```

**Daily Return**

Measures the daily percentage price movement.

Formula:

```text
Daily Return =
((Today's Closing Price - Yesterday's Closing Price)
 / Yesterday's Closing Price) × 100
```

**Daily Volatility (%)**

Measures the variation in daily returns. Higher volatility represents larger price movements and greater uncertainty.

Formula:

```text
Volatility =
Standard Deviation of Daily Returns
```

### Chart Interpretation

- X-axis: Daily Volatility (%)
- Y-axis: Total Return (%)

Companies positioned higher on the chart generated stronger returns. Companies positioned further right experienced larger daily price movements.

The chart shows whether higher returns were achieved with relatively stable or volatile price behaviour.

### Screenshot

![Risk vs Return](src/analytics_etl/analysis/charts/risk_return.png)


---

## Claim 2: Trading Activity Analysis

### Business Claim
During the observation period, **the company with the highest daily trading volume demonstrated the strongest trading activity and investor participation among the analysed companies.**

### Chart Artefact
`volume_by_date.html`

### Business Question
Which company experienced the highest level of trading activity during the analysis period?

### Analysis Performed

Trading activity was measured using the number of shares traded each day.

Formula:

```text
Daily Trading Volume =
Total number of shares traded on a trading day
```

The data was grouped by:

- Company
- Trading date
- Trading volume

Trading volume was selected instead of traded monetary value because the companies operate in different currencies:

- Apple Inc. → USD
- Infosys Limited → INR
- Reliance Industries Limited → INR

Using share volume provides a consistent comparison of market activity without requiring currency conversion.

### Chart Interpretation

- X-axis: Trading Date
- Y-axis: Trading Volume (shares)

Higher values indicate stronger market participation and increased buying and selling activity.

### Screenshot

![Trading Volume Chart](src/analytics_etl/analysis/charts/volume_by_date.png)


---

## Claim 3: Maximum Drawdown Analysis

### Business Claim
During the observation period, **the company with the largest maximum drawdown experienced the greatest peak-to-trough decline, indicating the highest downside risk among the analysed companies.**

### Chart Artefact
`drawdown.html`

### Business Question
Which company experienced the largest decline from a previous high price level?

### Analysis Performed

Maximum drawdown measures the largest percentage decline from a previous peak closing price.

Calculation steps:

1. Identify the highest closing price reached so far (running peak).

2. Calculate the decline from that peak.

Formula:

```text
Drawdown (%) =
((Current Price - Previous Peak Price)
 / Previous Peak Price) × 100
```

3. Select the lowest drawdown value for each company.

A more negative value represents a larger decline and greater downside risk.

### Chart Interpretation

- X-axis: Company
- Y-axis: Maximum Drawdown (%)

The chart highlights which company experienced the largest temporary loss from its previous high price.

### Screenshot

![Maximum Drowdown Chart](src/analytics_etl/analysis/charts/drawdown.png)


---

# Summary of Chart Artefacts

| Business Question | Chart Artefact | Measures |
|---|---|---|
| Which company generated the best return relative to risk? | `risk_return.html` | Total Return (%) vs Daily Volatility (%) |
| Which company had the highest trading activity? | `volume_by_date.html` | Daily Trading Volume (shares) |
| Which company experienced the largest decline from a peak? | `drawdown.html` | Maximum Drawdown (%) |