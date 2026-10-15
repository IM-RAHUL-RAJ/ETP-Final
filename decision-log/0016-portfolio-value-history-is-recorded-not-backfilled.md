# 0016 Portfolio value history is what was recorded, not a reconstruction

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | Sprint 10 UI enhancement work (drafted by Claude from the sprint brief; the team to confirm) |

## Context

The portfolio redesign shows a value-over-time chart (30 days, 3 months, 1 year) and a "today" change. The platform has orders and current prices but no daily history of a customer's holdings, and the market-data feed carries the latest quote only.

## Options considered

| Option | For | Against |
|---|---|---|
| Record a snapshot each time the portfolio is valued (at most one per ten minutes) and chart those | True to what the customer saw; needs no price history; simple | Empty at first; sparse for someone who rarely visits |
| Rebuild history from orders and Fauxnance candles | Looks complete on day one | A reconstruction presented as a record; quota-heavy; wrong around splits, fees and cash movements |
| Draw sample data | Looks good in a demo | Fabricated numbers on a money screen |

## Decision

Add `portfolio_value_snapshots`. `GET /api/v1/portfolio/{id}/history?range=` values the portfolio, records a point if none exists in the last ten minutes, and returns the points and a day change. The day change is the move since the latest point at least 24 hours old, **minus net deposits and withdrawals since then** so adding cash is not shown as profit. With no such point it is null and the tile says so.

## Consequences

The chart says "your history builds as you use Quorum" until it has two points. Snapshots are taken on read, so an account nobody views has gaps. A scheduled daily snapshot is the follow-up if the team wants an unbroken line.
