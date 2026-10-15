# 0007 An alert fires once and then deactivates

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Team 2, day one (drafted by Claude from the brief; the team to confirm) |

## Context

"Crossed" is not obvious: a price sitting just past a level for an hour is past it on every quote. The brief
names three behaviours (deactivate, wait for a reset, fire on every quote past the level) and warns the third
sends dozens of messages a minute through a module owned by a different pair. `market-data` carries one message
per symbol per poll, delivered at-least-once.

## Options considered

| Option | For | Against |
|---|---|---|
| Fire once: `ACTIVE` to `TRIGGERED` in a guarded update (`WHERE status = 'ACTIVE'`), the alert stays visible as fired | Cannot message twice, even with a redelivered quote, a rebalance, two instances or a cancel racing a quote; the customer sees that it fired | A customer who wants to be told again must create another alert |
| Fire, then wait for the customer to re-arm it | Reusable | A reset action to build and explain; an alert nobody re-arms is silently dead |
| Fire on every quote past the level | Trivial | Forty messages a minute; a flooded channel and a flooded notification ledger |

## Decision

Fire once. ABOVE fires on the first non-stale quote at or above the level, BELOW on the first at or below it. A
quote flagged `stale` is ignored (not evidence the market crossed anything). An alert is refused at creation if
the current non-stale price has already crossed the level, so "crossed" always means the price moved through it
after the customer asked. The guarded update is the idempotency mechanism: whoever updates zero rows does nothing.

## Consequences

An alert is a one-shot. The per-symbol index is a cache of the table and may be stale on another instance for up
to 30 s; the guarded update makes a stale entry harmless. A flapping price crossing and re-crossing a level produces
one message, by design.
