# 0006 The offset is committed when the notification is recorded, not when a provider confirms

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Team 2, day one (drafted by Claude from the brief; the team to confirm) |

## Context

`trade-events` is keyed by account across three partitions, so every account on a partition waits behind the
message at the head of it. E-mail and SMS providers are slow and sometimes down. Kafka is at-least-once, so the
same event will sometimes arrive twice.

## Options considered

| Option | For | Against |
|---|---|---|
| Record the notification (status QUEUED) in Postgres and return; a separate dispatcher sends it. Commit the offset on return | A provider outage cannot stall a partition; delivery state (QUEUED, SENT, FAILED, HELD) is tracked separately from the offset; retries do not need Kafka | Two moving parts and a table |
| Send inside the listener and commit after the provider answers | Least code; no ledger needed except for idempotency | One bad provider afternoon backs up every account on the partition and triggers Kafka retries that re-send |
| Commit first, then send | Never stalls | A crash between the two loses the notification silently |

## Decision

The listener inserts into `notifications` with `ON CONFLICT (event_id) DO NOTHING` (the idempotency step) and
returns. The dispatcher claims due rows with `UPDATE ... FOR UPDATE SKIP LOCKED` and a lease, resolves the channel
at send time (0004, 0013), sends, and records `SENT` / retries with backoff / `FAILED`. A message that cannot ever
succeed (malformed JSON, an event from an unexpected producer, text that looks like it carries an address)
is dead-lettered on the first attempt to `trade-events.DLT`; a database blip is retried four times and then
dead-lettered, never skipped.

## Consequences

A message is delivered at-least-once: a crash after a provider accepted it but before `SENT` was written re-sends
after the lease. Idempotency lives upstream (event id), not at the provider. Replaying any event shows one
notification and one send (`KafkaChainIT.aReplayedTradeEventIsRecordedAndSentOnceAndTheOffsetIsCommittedAfterwards`).
Retry with backoff that tells a transient from a permanent provider error is out of scope: every provider error is retried up to five times.
