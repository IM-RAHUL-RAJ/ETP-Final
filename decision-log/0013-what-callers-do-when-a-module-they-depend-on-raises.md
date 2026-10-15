# 0013 What a caller does when the module it depends on raises or cannot answer

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Team 2, day one (drafted by Claude from the brief; the team to confirm) |

## Context

Story 804 asks, for both seams, what each caller does when the module it depends on raises or takes too long.
Calls are in-process, so "too long" means a slow database. Preferences is on the path that decides whether a
customer hears about their own trade, so availability matters more than feature surface.

## Options considered

| Option | For | Against |
|---|---|---|
| Fail safe: keep the message, record why, retry. Preferences unreadable gives QUEUED/`PREFERENCES_UNAVAILABLE`; no usable contact gives HELD/`NO_CONTACT`; both visible in the inbox | A preferences outage delays a message and never loses or misroutes one | A message can be late |
| Fail open to the default channel when preferences raises | Message goes out at once | Sends on a channel the customer may have declined; hides the outage |
| Fail closed and drop | None | The message is lost and nobody finds out |

## Decision

| Seam | Dependency says | Caller does |
|---|---|---|
| notifications calls `ChannelResolver` | stored channel | sends on it; records `channel`, `channelSource = STORED` |
| | nothing stored | sends on the platform default; records `channelSource = DEFAULT` (0004) |
| | `Optional.empty()` (chosen channel unusable, or unknown account) | HELD with reason `NO_CONTACT`; tried again every 5 minutes; never another channel |
| | raises `PreferencesUnavailableException` | stays QUEUED with reason `PREFERENCES_UNAVAILABLE`; retried with backoff |
| alerts calls `AlertDelivery` | receipt | `HANDED_OVER` |
| | raises `AlertDeliveryException` | `HANDOVER_FAILED`, retried (0008) |
| provider (e-mail, SMS) raises | | QUEUED and retried with backoff; `FAILED` after 5 attempts |

## Consequences

Nothing in this table drops a message. It also means a long preferences outage shows up as a growing pile of
QUEUED rows with a reason, which is something an operator can query. There is no circuit breaker: the dispatcher
is a batch loop, not a request path.
