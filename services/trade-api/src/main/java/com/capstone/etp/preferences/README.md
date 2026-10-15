# Preferences

A customer's **default account** and **alert channel**. Package `com.capstone.etp.preferences`, inside the Trade REST API.
No new port, container or token verifier.

| | |
|---|---|
| Contract | `contracts/preferences-api.yaml` |
| Routes | `GET, PUT /api/v1/accounts/{accountId}/preferences` |
| Table | `customer_preferences` (one row per **client**; no e-mail or phone column) |
| Kafka | none consumed, none produced; **no consumer group** |
| Published to other modules | `ChannelResolver`, `ResolvedChannel`, `AlertChannel`, `ChannelSource`, `PreferencesUnavailableException` (see `docs/sprint-10/seams.md`) |
| Called by | `notifications` (`NotificationDispatcher`), and nothing else |

## What it does

* The customer picks `EMAIL`, `SMS` or `PUSH` and, if they have several accounts, which one to open on.
* **It stores the choice, not the contact detail.** The destination is read from `clients.email` / `clients.phone` when a message is sent (decision 0005). The API shows it masked.
* **When nothing is stored**, `ChannelResolver` answers the platform default (e-mail to the profile address, else the in-app inbox) and says it is the default (decision 0004).
* **At sign-in** the Angular app reads the stored default account and opens on it, if the token carries it (`PreferencesService.applyDefaultAccountAtSignIn`).

## What it does not do

* It exposes no route for channel resolution. That is the in-process `ChannelResolver`; no token reaches it.
* It accepts no address, number or URL. A request with a field the contract does not define, or a channel outside the three, is `422 VAL-422`.
* It does not copy anything `accounts` or `clients` own.

## Access control

Every route calls `AccountAuthorizer.ensureOwnAccount(accountId)`: the path account must be in the token's claim. An admin
token gets no exception. A mismatch is `403 ACC-403`, logged.

## Failure behaviour

`resolve` throws `PreferencesUnavailableException` only when the database cannot be read. The caller keeps the
message and tries again (decision 0013).

## Tests

`PreferencesServiceTest`, `PreferencesControllerTest`, `ContactMaskerTest`; end to end in `ExtensionsChainIT`.