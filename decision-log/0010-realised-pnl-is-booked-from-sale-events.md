# 0010 Realised profit and loss is booked from sale events, once, in the account's currency

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Team 2, day one (drafted by Claude from the brief; the team to confirm) |

## Context

The contract defines realised P&L as `(fillPrice - averageCostAtSale) * quantitySold`, booked at the sale and
"nothing to do with today's price". The tables do not keep the average cost at the time of a sale: `orders` and
`transactions` hold the fill price, and `portfolio_holdings.avg_price` is overwritten as positions change.
`trade-events` carries `executedPrice`, `quantity` and `averageCostAfter` for every fill, and a sell leaves
average cost unchanged, so `averageCostAfter` IS the cost at the sale, even when the position closes.

One trap found while reading the executor (`SettlementService`): `executedPrice` in the event is converted to the
ACCOUNT's currency, but `averageCostAfter` is the holding's `avg_price`, in the INSTRUMENT's currency.

## Options considered

| Option | For | Against |
|---|---|---|
| Consume `trade-events` (group `portfolio-service`), convert the average cost to the account currency, and insert one ledger row per sale keyed on `eventId` | Exactly the contract's definition; replay-proof; cross-currency correct | Sales older than the topic's 30-day retention are not in the ledger |
| Recompute from `orders` joined to a price | Covers old orders | Needs a price, which is precisely the error the contract warns about; the cost at sale is not stored |
| Read `positions` and infer | None | Cannot tell what was sold |

## Decision

First option. `RealisedPnlBook.bookSale` converts `averageCostAfter` from the instrument's currency to the
account's, computes `(executedPrice - averageCostBase) * quantity`, and inserts into `portfolio_realised_pnl`
with `ON CONFLICT (event_id) DO NOTHING`. The group starts from `earliest` so the retained 30 days are booked on
first run; booking is idempotent, so replays and re-reads are harmless. Realised P&L is summed from the ledger and
never recomputed. Unrealised P&L is computed from live prices at read time and never stored.

## Consequences

Sales before the retention window (or before the module first ran with a retained topic) are missing from the
lifetime figure. A backfill from a database export is the follow-up if that matters to the business. The
averages use the platform's 2-decimal currency rounding, the same as the executor, so a booked sale agrees with the cash that moved.
