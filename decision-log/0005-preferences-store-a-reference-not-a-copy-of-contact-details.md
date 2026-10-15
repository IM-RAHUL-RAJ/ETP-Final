# 0005 Preferences hold the choice, and the contact detail stays where it already lives

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Team 2, day one (drafted by Claude from the brief; the team to confirm) |

## Context

The brief asks for "an alert channel with its contact detail" and warns that a second copy of an e-mail address
or phone number doubles the places it can leak and creates a reconciliation problem. `clients.email` and
`clients.phone` already hold both, on the table the platform already protects and already maintains.

## Options considered

| Option | For | Against |
|---|---|---|
| Store the channel only; read the destination from `clients` when a message is sent | One copy of each contact detail; a changed address takes effect on the next message; a leak of the preferences table discloses no contact detail; nothing to encrypt or to keep in step | A customer cannot set an alert address different from their profile |
| Copy the e-mail/phone into `customer_preferences` | A customer could choose a different address for alerts | Two copies to keep in step; a new place to protect; Kafka/log hygiene gets harder |
| Copy it and encrypt it | Smaller exposure than plaintext | Still two copies; key management for a one-week module |

## Decision

`customer_preferences` has `client_id`, `default_account_id`, `alert_channel`, `updated_at` and nothing else (the
integration test asserts there is no column that could hold a contact detail). The settings API returns the
destination only MASKED (`j***@example.com`, `******3210`). Resolution reads the unmasked value for a single send;
`ResolvedChannel.toString()` and `OutboundMessage.toString()` print `<not logged>` / a masked form, and the
notification ledger deliberately does not store the destination. No contact detail goes into a Kafka payload, a
log line, or an error message.

## Consequences

A customer who wants alerts at a different address must change their profile (which is the right place, and also
changes where everything else goes). "SMS" is offered only when the profile has a phone, and refused with a clear
message otherwise. If a second contact point per channel is ever wanted (out of scope this week) it needs its own
table and its own decision.
