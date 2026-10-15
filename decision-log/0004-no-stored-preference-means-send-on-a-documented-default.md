# 0004 With no stored preference, notifications sends on a documented default

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Team 2, day one (drafted by Claude from the brief; the team to confirm) |

## Context

Nobody has stored a preference for any seeded customer, so the very first fill after deployment meets this
case. The brief says both answers (hold, or send on a default) are defensible and that choosing nothing loses
the message. `ChannelResolver` is the seam, and what it answers here decides what the notifier does.

## Options considered

| Option | For | Against |
|---|---|---|
| Send on a documented default: e-mail to the profile address, or the in-app inbox if the profile has none | The customer hears about a rejection they never set up alerts for; no message waits on a setup step | We contact someone on a channel they did not pick |
| Hold every message until a preference exists | Never contacts anyone unasked | A customer who never opens Settings is never told anything, including that an order failed; the inbox fills with held items |
| Fail closed and drop | Simplest | The message is lost and nobody finds out, which is the one outcome the brief rules out |

## Decision

`ChannelResolver.resolve` answers a `DEFAULT` channel, never an error, when nothing is stored: EMAIL to the
address already on the customer's profile, else PUSH (the in-app inbox, which needs no contact detail). The
notification records `channelSource = DEFAULT` and the inbox says "your default", so the customer can see why
and change it. The one case that is NOT defaulted is a customer who *chose* a channel their profile cannot serve
(SMS with no phone): that returns empty and the message is HELD with the reason `NO_CONTACT`, because quietly
using a different channel than the one they chose is worse than waiting and saying so (see 0013).

## Consequences

The default e-mail goes to an address the customer gave us for the account, not to a stranger, which is why we
were comfortable defaulting rather than holding. With no `SMTP_HOST` the e-mail channel only logs a masked stub
line and the record says `STUB_DELIVERY`, so a demonstration must set SMTP to show a real send. If product
decides the default should be "ask first", it is one method (`PreferencesService.platformDefault`).
