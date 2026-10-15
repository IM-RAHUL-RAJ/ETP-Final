# Trade Advice

> **Trade Advice provides recommendations and does not execute trades.** Nothing in this package can place, change or cancel an order.

## Purpose

For the signed-in customer, it reads what they **hold** (positions) and what they **watch** (watchlist), looks at each instrument once, and returns a deterministic BUY, SELL or HOLD with the reasons that produced it.

## Architecture

```
GET /api/v1/trade-advice ──> TradeAdviceController
                                   │
                                   ▼
                            TradeAdviceService ──> AccountAuthorizer (the account must be the token's own)
                                   │               AccountService (positions, watchlist: existing)
                                   ▼
              PriceHistoryProvider (CandlePriceHistoryProvider: daily closes from CandleService, existing)
                                   │
                                   ▼
                AdviceStrategy (MovingAverageAdviceStrategy: the rules, and only the rules)
```

The rules are isolated in `AdviceStrategy`; swap or add one without touching the service or the API.

## API

| Request | Answer |
|---|---|
| `GET /api/v1/trade-advice` | A list: one advice per distinct instrument held or watched |
| `GET /api/v1/trade-advice/{symbol}` | One advice; 404 `ADV-404` if the instrument is neither held nor watched |

Both take an optional `accountId`, which must be one of the token's own accounts (otherwise 403 `ACC-403`). Without it the token's first account is used. No token, or a bad one: 401 `AUTH-401`.

```json
[
  {
    "symbol": "AAPL",
    "signal": "BUY",
    "reasoning": [
      "Latest close 232.10 is above the 10-observation moving average 229.40",
      "The 10-observation moving average 229.40 is above the 50-observation moving average 221.10",
      "Price moved +2.30% over the last 10 observations (context only)",
      "BUY: the short-term and the long-term trend are both positive"
    ],
    "timestamp": "2026-10-07T12:00:00Z"
  }
]
```

## Rules (`MovingAverageAdviceStrategy`)

| Signal | Condition |
|---|---|
| BUY | latest close above the short MA **and** short MA above the long MA |
| SELL | latest close below the short MA **and** short MA below the long MA |
| HOLD | enough data, neither of the above |
| INSUFFICIENT_DATA | fewer closes than the long period |

Periods: `app.advice.short-period` (default 10) and `app.advice.long-period` (default 50), daily closes.

## Market indicators

Beside each signal the response carries the market behind it (`indicators`) and a chart series (`chart`), also shown on the Advice page:

| Indicator | Meaning |
|---|---|
| `trend` | **BULLISH** (price above the short average, which is above the long one), **BEARISH** (the reverse), else **NEUTRAL** |
| `rsi`, `rsiState` | Wilder RSI (14): 70 or more is OVERBOUGHT, 30 or less OVERSOLD |
| `momentumPercent` | change over the short period |
| `volatilityPercent` | standard deviation of recent daily returns |
| `chart[]` | each close with the short and long averages at that point |

**RSI guard:** a BUY with RSI at 70 or more becomes HOLD (overbought), and a SELL with RSI at 30 or less becomes HOLD (oversold), with the reasoning saying so.

The Advice page also shows an overall **market mood** (how many of your instruments are bullish, neutral, bearish).

## Insufficient data

No BUY, SELL or HOLD is given. The answer is `INSUFFICIENT_DATA` with:

```
Signal unavailable: insufficient historical price data.
Required: 50 observations.
Available: 17.
```

A market-data provider that is down gives the same answer with `Available: 0.` rather than a guess.

## Run and test

```
mvn spring-boot:run                    # part of trade-api
mvn test -Dtest='MovingAverageAdviceStrategyTest,TradeAdviceServiceTest,TradeAdviceControllerSecurityTest'
```
