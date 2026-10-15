# 0001 The four modules are packages in the Trade REST API, joined only by published interfaces

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Team 2, day one (drafted by Claude from the brief; the team to confirm) |

## Context

The Sprint 10 brief puts preferences, notifications, watchlists/alerts and portfolio inside the Trade REST
API as packages, not as services. A compiler no longer stops one module reaching into another's tables, so
the boundary is something we hold by convention. Three of the four form a chain: notifications needs a
channel from preferences, alerts needs delivery from notifications.

## Options considered

| Option | For | Against |
|---|---|---|
| One package per module; the only things that cross a boundary are Java interfaces and the records they use | The seam can be built against on Monday and replaced on Thursday; a module can fail independently; easy to say who calls what | Nothing enforces it except review and tests |
| One package per module, but modules call each other's mappers/services directly | Fastest to write on Tuesday | Cannot be replaced; a change to a table breaks a stranger; "who reads this table" has no answer |
| Four new services behind HTTP | A real network boundary | Four Dockerfiles, ports and token verifiers before any feature; the brief rules it out |

## Decision

Package per module (`preferences`, `notifications`, `alerts`, `portfolio` under `com.capstone.etp`). Everything
in a module is package-private except what another module is allowed to use:

| Published by | Used by | What |
|---|---|---|
| `preferences` | `notifications` | `ChannelResolver`, `ResolvedChannel`, `AlertChannel`, `ChannelSource`, `PreferencesUnavailableException` |
| `notifications` | `alerts` | `AlertDelivery`, `AlertDeliveryRequest`, `DeliveryReceipt`, `AlertDeliveryException` |
| `kafka.consumer` (platform) | `alerts` | `QuoteObserver` |

`portfolio` publishes nothing and consumes nothing from the other three. Each module reads and writes only its
own tables (`customer_preferences`, `notifications`, `price_alerts`, `portfolio_realised_pnl`); anything about
accounts, positions or instruments goes through the platform's `AccountService` / `AccountMapper`.

## Consequences

We are trusting ourselves: a test per seam (`NotificationAlertDeliveryTest.deliveryIsAJavaInterfaceNotAnHttpRoute`,
the `ChannelResolver` tests that call it with no token) and review are the enforcement. If the modules are ever
split into services, `ChannelResolver` and `AlertDelivery` are the two places an HTTP client would go.
