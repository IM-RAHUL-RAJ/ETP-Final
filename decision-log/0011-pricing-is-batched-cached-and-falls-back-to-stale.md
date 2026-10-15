# 0011 Pricing is batched, cached, and falls back to a stale price rather than to nothing

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Team 2, day one (drafted by Claude from the brief; the team to confirm) |

## Context

The Fauxnance quota is 2000 requests a day, shared with the executor's poller (up to 1,920 a day at its 45 s
floor for a small universe, per `services/executor/design/market-data-poller-quota.md`). One request carries up to 25 symbols and costs one unit. Quotes can be delayed and
can be served stale. The contract: "unavailable for every symbol" is `503 MKT-503`; "for some" is a 200 with
`partial: true`, the unpriced positions' price fields null and `stale: true`.

## Options considered

| Option | For | Against |
|---|---|---|
| Batch (max 25) + 60 s in-process cache + 15 s cooldown after a failure + fall back to the last known price (expired cache entry, then the last `market-data` quote), always marked stale | Ten holdings cost one call; a refresh storm costs one call per minute; an outage costs one slow request, not one per customer; a customer sees a number and its age instead of a blank | A price served in an outage can be minutes old (but is marked) |
| One call per position, no cache | Simplest | Ten times the quota; a few customers on the page exhausts the day |
| Read prices only from the `market-data` topic, never call Fauxnance | Zero quota | Not the contract (prices must be traceable to a Fauxnance quote); only covers symbols the poller polls |

## Decision

First option. Staleness is decided in one place: the provider's own `stale` flag, or an observation older than
`app.portfolio.stale-after-seconds` (900 s), or any price served because the provider failed. MKT-503 is raised only
when an account holds something and NO source has a price for ANY of it; an account with no holdings needs no
prices and is never an outage. Currency conversion uses the platform's `CurrencyConverter` (the same table the
executor uses, so figures agree with settled cash); a holding in a currency it cannot convert is left out of the
totals and flagged `partial`, never silently summed.

## Consequences

The cache is per instance (60 s), so N instances can each spend one call per minute per symbol set; at three
instances that is still far under the quota. The key is read from `FAUXNANCE_API_KEY` only on the server;
the browser calls `/api/v1/portfolio/...` and never Fauxnance. `/health` asks `/usage` at most every 5 minutes because it costs quota.
