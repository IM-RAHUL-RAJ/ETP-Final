# 0014 An alert can be switched off, and armed again after it fires

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | Sprint 10 UI enhancement work (drafted by Claude from the sprint brief; the team to confirm) |

## Context

Decision 0007 made an alert fire once and stay `TRIGGERED`. The Sprint 10 brief asks for an enable/disable switch, an edit, a delete and a history of triggers, and adds comparison operators (`>`, `>=`, `<`, `<=`, `==`). A customer who switches a fired alert back on expects to be told again.

## Options considered

| Option | For | Against |
|---|---|---|
| Keep `status` and add `enabled`; switching on or editing a fired alert sets it back to `ACTIVE`; one `price_alert_triggers` row per firing | Keeps 0007's guarded update (the alert still fires once per arming); history survives a re-arm; no new state machine | A fired alert can fire again, so the notification event id must include the firing |
| Fire on every quote past the level while enabled | Simplest | Exactly the flood 0007 rejected |
| Never re-arm; the customer creates a new alert | No change to 0007 | Makes the requested switch meaningless for a fired alert |

## Decision

Add `enabled` and `operator` to `price_alerts`; keep the single-fire guard (`UPDATE ... WHERE status = 'ACTIVE' AND enabled`). Each firing inserts a `price_alert_triggers` row (unique `event_id`, cascade on delete). The notification event id is now derived from `(alertId, triggerId)`, so a retried hand-over is still one message and a second firing is a second. Switching on, or editing, a fired alert re-arms it, but never while its hand-over is unresolved (409). Delete removes the alert and its history. `==` compares at the four decimals the level is stored at and is not refused when "already true".

## Consequences

0007 still holds for one arming; it no longer means "once, ever". The old `direction` column is kept and derived from the operator so existing rows and readers keep working (migration 004 backfills `ABOVE` as `>=` and `BELOW` as `<=`). Revisit if customers ask for "repeat every time" alerts: that is a different feature with its own rate limit.
