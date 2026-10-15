# Sprint 10: scope and backlog

Written before the code, as the sprint asks. This covers story 803 (day one) and the backlog for all four modules.
The four briefs are the source; the contracts are in `contracts/`; decisions are in `decision-log/`.

## Scope confirmed with the instructor (to be confirmed, see the note at the end)

### What we are building by Friday

| # | Module | Package | Built to |
|---|---|---|---|
| 1 | Customer preferences | `com.capstone.etp.preferences` | `contracts/preferences-api.yaml` (ours) |
| 2 | Customer notifications | `com.capstone.etp.notifications` | `contracts/notifications-api.yaml` (ours) |
| 3 | Watchlists and price alerts | `com.capstone.etp.alerts` | `contracts/alerts-api.yaml` (ours), plus the existing watchlist routes |
| 4 | Portfolio and P&L | `com.capstone.etp.portfolio` | `contracts/portfolio-api.yaml` (given, binding) |

Build order: 1, then 2, then 3. Module 4 depends on none of them and can run in parallel. The package names are the
boundary (decision 0001).

### What we have decided not to build

* Digest batching, retry that tells a transient provider error from a permanent one, and read receipts (notifications).
* Multiple contact points per channel, preference history and revert, and a change event so callers need not ask (preferences).
* Percentage-move alerts, a browser push channel, and alert history with per-delivery status (alerts).
* Performance attribution, time-weighted returns, tax lots, anything needing price history (portfolio).
* The two stretch modules (trade advice, automated strategy execution).
* A real SMS gateway: SMS and push record `STUB_DELIVERY`; e-mail is live when `SMTP_HOST` is set.

### Routes we propose to expose for the three modules with no contract

| Module | Route | Purpose |
|---|---|---|
| Preferences | `GET, PUT /api/v1/accounts/{accountId}/preferences` | Read and set the default account and alert channel |
| Notifications | `GET /api/v1/accounts/{accountId}/notifications[?unreadOnly&limit]` | History, newest first |
| | `GET .../notifications/unread-count` | The inbox badge |
| | `POST .../notifications/{id}/read` | Mark one read |
| Alerts | `GET, POST /api/v1/accounts/{accountId}/alerts` | List and create |
| | `DELETE /api/v1/accounts/{accountId}/alerts/{id}` | Cancel an active alert, or remove a finished one |
| Portfolio | the contract's three routes and `/health` | as specified |

Deliberately **not** routes: channel resolution (`ChannelResolver`) and alert delivery (`AlertDelivery`). See `seams.md`.

## Who does what

Split by the order of the chain, not one person per module, so everyone can walk any module.

| Phase | Work | Pairing |
|---|---|---|
| Mon | Read all four briefs; agree the two seams as Java interfaces (`seams.md`); migration `003_extensions.sql`; skeleton routes that answer | whole team |
| Mon to Tue | Preferences: API, persistence, settings screen, default account at sign-in | pair A |
| Mon to Fri | Portfolio and P&L (the contract is fixed; the third party is the risk) | person B |
| Tue to Wed | Notifications: listener, ledger, dispatcher, history, inbox | pair A moves on |
| Wed to Thu | Alerts: index, evaluator, hand-over, cap, alerts screen | pair A moves on; B reviews preferences hardening |
| Thu | End-to-end on live data; security review; decision log complete | all |
| Fri | Rotate, rehearse, demonstrate | all |

Files everyone wants at once (`pom.xml`, `application.properties`, `SecurityConfig`, `docker-compose.yml`): one person at a time, on their branch, merged first.

## Backlog

Acceptance criteria are the sprint's. Each story has the tests that prove it.

### 803. Day one: read the briefs, confirm scope, write the backlog

* Every member has read all four briefs; scope confirmed with an instructor on Monday. **(team)**
* This file exists before any code is written.

### 804. Agree the two integration seams (`seams.md`)

* The interface notifications calls on preferences, and the one alerts calls on notifications, are written down with response shapes and behaviour when nothing is stored.
* How one module calls another is a named Java interface, not an HTTP hop with a borrowed customer token.
* Every consumer has an explicit group id, listed in the README and shared with no other module.
* Tests: `PreferencesServiceTest` (resolve, no token), `NotificationAlertDeliveryTest`, `KafkaChainIT.theListenersUseOneGroupEachAndNoGroupIsShared`.

### 805. Customer preferences and personalisation

* A preference record holds a default account and an alert channel with its contact detail and survives a restart.
* The stored default is applied at the next sign-in in the Angular application.
* A resolution path exists for notifications, and no customer token can reach it.
* Every read and write is scoped to the account in the verified token.
* Tests: saved and read back; survives a restart (a row in Postgres, `ExtensionsChainIT.preferencesPersistAreScopedAndRefuseADestination`); another account refused not empty; default applied at sign-in (`preferences.service.spec.ts`, `login.component.spec.ts`).

### 806. Customer notifications

* Consumes `trade-events` in its own group; handles filled, rejected and cancelled.
* The channel is resolved from preferences on every send, and the resolved channel is recorded on the notification.
* A replayed event produces no second message; history is readable only by the owning account.
* Tests: filled and rejected each produce a notification; changing the preference changes the channel; replay does not send twice (`KafkaChainIT`); history refused to a non-owner.

### 807. Watchlists and price alerts

* Consumes `market-data` in its own group; a customer can keep a watchlist and see a live price beside each entry.
* An alert has a threshold and a direction, is evaluated against the stream, and a crossed threshold is delivered through notifications.
* Alert state is visible to the customer; alerts are capped per account.
* Tests: crossing triggers, a quote short of it does not, delivered through notifications not a log, creation capped.

### 808. Portfolio and P&L

* Implements `contracts/portfolio-api.yaml` exactly: three routes and `/health`, field names and error codes.
* Positions are priced from live Fauxnance quotes, batched and cached, each carrying when it was observed and whether it is stale.
* Pricing unavailable for every holding gives `503 MKT-503`; for some, a partial answer marked as such.
* Realised P&L is accumulated from sales, not recomputed; the module writes only its own table.
* Tests: priced from a live quote; cache reuse; 503; partial; realised booked at the sale; replay does not double-count; another account refused.

### 809. End-to-end integration on live data

* A stored channel preference drives a trade notification, and a watchlist alert is delivered on the same channel.
* Each module authorises every route it adds, and each is reachable from the Angular application.
* The demonstration runs on real quotes, real messages and real rows. Fixtures belong in the test suite.
* See `demo.md`.

### 810. One combined OWASP security review

* `security-review/Sprint10_Security_Review.md`: one document, full Top Ten, module named per finding, every finding dispositioned.

### 811. Architecture decision log

* `decision-log/`: one file per decision in the template's shape, more than the floor of six.

### 812. Platform demonstration

* The running platform and the four extensions against a live stack; every member can walk any module.
* The repository leaves a local stack that starts cleanly (`docker-compose.yml` paths fixed; see `services/trade-api/README.md`).

## Note on confirmation

Scope "confirmed on day one is an acceptance criterion". This file records what the team proposes to bring to the
instructor; the instructor's confirmation has to be given by the instructor, so add the date and name here when it has been.

| Confirmed by | Date | Notes |
|---|---|---|
| (instructor) | | |