# 0009 Authorisation: every route compares the token's account; consumers act on events, never through a route

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Team 2, day one (drafted by Claude from the brief; the team to confirm) |

## Context

The Trade REST API verifies signature, expiry and algorithm on everything under `/api/v1/`. It cannot know
whether this caller may reach this account. The existing `AccountAuthorizer.ensureAccountAccess` lets any ADMIN
through to any account, which is right for the admin dashboard and wrong for a customer's preferences,
notifications, alerts and portfolio. Two kinds of work run with nobody logged in: the `trade-events` and
`market-data` consumers.

## Options considered

| Option | For | Against |
|---|---|---|
| New strict `ensureOwnAccount`: the path account must be in the token's `accountId`/`accountIds`, admin or not; used on every new customer route | A leak needs the customer's token; mismatch is 403 ACC-403 and logged | An admin cannot read a customer's portfolio through these routes (they still can on the Sprint 6 read routes) |
| Reuse `ensureAccountAccess` | No new code | An admin token reads every customer's inbox and portfolio; the brief names this the first finding the review looks for |
| Have consumers call the HTTP routes with a service token | One authorisation path | A service token that can read everyone; a loop through the front door; the brief rules out borrowing a token |

## Decision

1. **Routes.** Every route under `/api/v1/accounts/{id}/{preferences,notifications,alerts}` and
   `/api/v1/portfolio/{id}` calls `ensureOwnAccount(accountId)` first. Every query then filters on that account,
   never on an id from the body, and the request bodies have no account field (the strict JSON mapper refuses one).
   A notification or alert id of another account answers exactly like one that does not exist.
2. **Internal paths are not routes.** `ChannelResolver` and `AlertDelivery` are Java interfaces. No controller
   exposes either; a test asserts the notifications controller has no method that takes an alert delivery request
   or looks like a send, and review has to hold the line for new controllers.
3. **Consumers.** The `trade-events` listeners write only rows for the account named in a message from an internal
   topic, accept only `trade-executor`/`trade-api` as the producer, read nothing on anyone's behalf, and serve
   nothing. What they write is read by customers only through the routes in (1). The quote consumer only updates
   alert rows and calls `AlertDelivery`.
4. `/health` is public by contract (`portfolio-api.yaml`: `security: []`); it carries no customer data and no key.

## Consequences

The consumer boundary rests on topic access being limited to our producers; Kafka ACLs are documented as the
production plan in `contracts/kafka-topics.md` and are not implemented locally. The only unauthenticated route
this work adds is `/health`.
