---
name: portfolio-redesign
description: "Use when working on the portfolio page, the watchlist candle chart, price freshness dots, or the CSV/Excel/PDF download on the portfolio and order blotter."
applyTo: "frontend/src/app/components/users/portfolio/**|frontend/src/app/components/users/watchlist/**|frontend/src/app/core/export/**|frontend/src/app/components/users/shared/download-menu/**"
---

# Portfolio, Watchlist Chart and Downloads

## Portfolio layout (Zerodha/Groww-inspired)

1. **Tiles**: Total value (large, dot-matrix figure), Today, All-time gain/loss, Current value (with Invested beneath).
   Gain is a green dot plus `+`, loss a red dot plus `-`. Today comes from the value history and is `n/a` until
   there is a point a day old; it removes cash added or taken out so a deposit never looks like performance.
2. **Charts** (`<app-echart>`, Apache ECharts, loaded on first use): value over time (30D / 3M / 1Y, remembered in
   `localStorage`) and a donut of current value by holding with a legend.
3. **Holdings table**: Instrument, Quantity, Average cost, Current price (+ freshness dot), Invested, Current value,
   Gain/Loss, %. Sortable headers (`aria-sort`). The row's left edge is tinted by the sign; the text is not.
   The currency is shown per row only when more than one currency is held.
4. **Filters**: All / Equity / Crypto / Forex / Other, only those that hold something, with counts, remembered.
   Category comes from the instrument's `assetClass` (`US_EQUITY`, `INDIAN_EQUITY` -> Equity, `CRYPTO`, `FX` -> Forex).
5. **Details** (collapsed): cash, invested, gain on holdings, gain from sales, number of holdings.
6. **Gains from sales, by date**: the date range applies to realised gains only; the gain on holdings is always now.

### Words

| Instead of | Say |
|---|---|
| Cost basis | Invested |
| Market value | Current value |
| Unrealised P&L | Gain/Loss (on holdings) |
| Realised P&L | Gain/Loss from sales |

### Value history is recorded, not invented

There is no historical price feed for a customer's holdings, so `GET /api/v1/portfolio/{id}/history?range=30d|3m|1y`
returns what was **recorded** each time the portfolio was valued (`portfolio_value_snapshots`, at most one per ten
minutes). It starts empty and fills with use. The chart says so when it has fewer than two points; never
backfill a past that nobody saw.

## Price freshness

Prices come from the platform, which reads the market-data topic first and the provider second. The page never
calls a quote provider. Each price has a dot:

| Age of `priceAsOf` | Dot | Meaning |
|---|---|---|
| under 5 min | `.dot.live` green | Live |
| 5 to 15 min | `.dot.delayed` amber | Delayed |
| over 15 min | `.dot.stale` red | Stale |
| no price | grey | Unavailable |

The dot has `role="img"`, an `aria-label` and a `title` with the exact time. A legend sits under the table.

## Candle chart (watchlist)

- Wide screens: the instrument list on the left, the detail panel docked on the right (`.page--detail`); under
  1000px the panel is a dialog.
- `GET /api/v1/quotes/candles/{symbol}?period=1d|4h|1h|15m&limit=N` -> `{symbol, currency, period, candles[], asOf}`.
  The platform fetches from Fauxnance (`/candles/{symbol}`), so the API key never reaches the browser, and
  caches each answer for 60s (`app.candles.cache-seconds`). A provider failure is **503**, never a drawn guess.
- `<app-candle-chart>` uses TradingView Lightweight Charts (lazy). Up candles `--ok`, down `--reject`, volume
  bars beneath, crosshair legend with O/H/L/C/V. Period buttons 1D/4H/1H/15M, remembered. Re-themes live.
- Bars per period: 1D 60, 4H 90, 1H 120, 15M 120.

## Download (`app-download-menu` + `ExportService`)

- Button "Download" beside Refresh on **Order blotter** and **Portfolio**; CSV, Excel (.xlsx) or PDF.
- When a filter narrows the list the menu asks **Current filter (n)** or **Everything (m)**. The file's header
  says which filters applied. With no narrowing there is no choice to make.
- PDF can include charts, **drawn natively in the PDF** (bars, and a line for value over time), so there is no
  page screenshot. Portfolio: value history and allocation. Blotter: orders by status.
- CSV: UTF-8 with BOM, quotes doubled, and a cell starting `= + - @` gets a leading `'` (formula injection).
  XLSX (exceljs) and PDF (jsPDF + autotable) load on first download and stay out of the main bundle.
- File names are `order-blotter-2026-10-07-14-05.csv`; nothing from the data is used in a name.

## Tests

`portfolio.component.spec.ts`, `candle-chart.component.spec.ts`, `export.service.spec.ts`,
`download-menu.component.spec.ts`, `order-history.export.spec.ts`; `PortfolioHistoryServiceTest`, `CandleServiceTest`.
