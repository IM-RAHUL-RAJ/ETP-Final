# 0002 Preferences belong to the customer, not to one account

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Team 2, day one (drafted by Claude from the brief; the team to confirm) |

## Context

The brief says "a preference record per customer" and asks for a default account. In the data model a client
(`clients`) can own several accounts (client 1 owns accounts 1 and 2), and an account is what a token names.
A preference keyed on the account would give one customer two channels that disagree.

## Options considered

| Option | For | Against |
|---|---|---|
| Key the row on `client_id`, reached from the account in the path | One preference per person; a default account is a real choice between that person's accounts | Needs a join through `accounts` on every read |
| Key the row on `account_id` | Simplest; matches the route | Two accounts of one customer can hold different channels; "default account" would always point at itself |

## Decision

`customer_preferences.client_id` is the primary key. The route stays `/api/v1/accounts/{accountId}/preferences`
because the token names accounts; the module resolves the client from the account. A default account must be
(a) one the signed-in token carries, so the browser can never widen what the token allows, and (b) one of the
same client's accounts (checked in SQL).

## Consequences

The auth service issues a token carrying a single `accountId` today, so for most customers the only valid
default is their own account and the setting is, honestly, a no-op until tokens carry `accountIds`. The
verifier and `AccountContextService` already understand an `accountIds` claim, so nothing here changes the day the
auth service issues one. The Angular settings page says so when there is only one account to choose.
