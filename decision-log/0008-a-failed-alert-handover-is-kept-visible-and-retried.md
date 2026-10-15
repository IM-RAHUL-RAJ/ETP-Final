# 0008 When handing a fired alert to notifications fails, keep it, show it, retry it

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Team 2, day one (drafted by Claude from the brief; the team to confirm) |

## Context

The call to notifications is in-process, so it cannot time out, but it can still raise: the database is
unreachable, the content guard refuses the text. By then the alert has already fired, which is a fact about the
price, and the customer asked to be told. The brief: "make the alert state readable either way".

## Options considered

| Option | For | Against |
|---|---|---|
| Keep the alert `TRIGGERED`, set `delivery_state = HANDOVER_FAILED`, retry on a timer up to 5 times; the alert cannot be removed while the hand-over is unresolved | Never drops the alert; the customer's page says it fired but was not queued yet; retries are safe because the delivery is idempotent on the alert id | A fired alert that never hands over stays on screen after 5 failures, as a visible problem |
| Roll the alert back to `ACTIVE` on failure | The next quote past the level would try again | A price that has since moved back would never retry; the customer is told nothing; a flapping provider means duplicated attempts |
| Log it and move on | Simple | "An alert written to a log is not an alert": the customer is never told and nobody knows |

## Decision

The trigger and the hand-over are two steps with a durable state between them (`PENDING_HANDOVER`, then
`HANDED_OVER` or `HANDOVER_FAILED`). `AlertHandoverRetrier` re-drives anything not yet `HANDED_OVER` every 30 s up
to `app.alerts.max-handover-attempts`. It also covers a crash between "fired" and "handed over".
`AlertDelivery.deliver` is idempotent on the alert id, so a retry can only complete a delivery, never repeat one.

## Consequences

Delivery is at-least-once between the two modules and exactly-once in effect. The customer-facing words for each
state are in the alerts page. An operator looks for `delivery_state = 'HANDOVER_FAILED'` on old rows: that is the
runbook entry. After the retry limit there is no automatic recovery; that would be the next piece of work.
