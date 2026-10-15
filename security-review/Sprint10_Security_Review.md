# Sprint 10 Security Review: the four extension modules

One OWASP Top Ten (2021) review across **preferences (P)**, **notifications (N)**, **watchlists and price alerts (W)**
and **portfolio and P&L (F)**, all packages inside the Trade REST API (`services/trade-api`). Each finding names the
module(s) it belongs to. Where a category lands differently on different modules, that is said in the row.

## Header

| Field | Value |
|-------|-------|
| Service | Trade REST API, Sprint 10 extension modules and the routes they add |
| Reviewed by | Team 2  |
| Date of review | 2026-10-06 |
| Commit reviewed | Working tree on `feature/refactor-folder-structure`. "Fixed" below names files and tests; the commit hash is to be added when the work is committed |
| Version of OWASP Top Ten used | 2021 |
| Scope | The routes, consumers, tables and Angular pages the four modules add, and every place they changed something the service already did (see "Changes to what already existed") |

## Routes and entry points reviewed

| Entry point | Module | Authenticated by | Authorised by |
|---|---|---|---|
| `GET, PUT /api/v1/accounts/{id}/preferences` | P | bearer token (existing verifier) | `ensureOwnAccount(id)`; PUT also `ensureOwnAccount(defaultAccountId)` |
| `GET /api/v1/accounts/{id}/notifications[/unread-count]`, `POST .../{nid}/read` | N | bearer token | `ensureOwnAccount(id)`; SQL keyed on `(id, account_id)` |
| `GET, POST /api/v1/accounts/{id}/alerts`, `DELETE .../{aid}` | W | bearer token | `ensureOwnAccount(id)`; SQL keyed on `(id, account_id)` |
| `GET /api/v1/portfolio/{id}[/positions\|/pnl]` | F | bearer token | `ensureOwnAccount(id)` |
| `GET /health` | F | none (the contract says `security: []`) | n/a: no customer data, no key |
| Kafka `trade-events`, group `notification-service` | N | n/a (internal topic) | source allow-list, event validation (see A08) |
| Kafka `trade-events`, group `portfolio-service` | F | n/a | same |
| Kafka `market-data`, group `watchlist-service` | W | n/a | n/a: reads quotes, updates alert rows |
| `ChannelResolver.resolve` (P to N), `AlertDelivery.deliver` (N to W) | P, N | **not routes**: in-process Java interfaces | n/a |

---

## Categories

### A01: Broken Access Control

| Aspect | Finding |
|--------|---------|
| **In scope** | Yes. Primary focus. All four modules |
| **Modules** | P, N, W, F |
| **Finding** | Every customer route calls `AccountAuthorizer.ensureOwnAccount(accountId)` before reading anything: the path account must be in the verified token's `accountId`/`accountIds` claim. A mismatch is `403 ACC-403` and is logged (`[ACCESS_DENIED] subject=... requestedAccountId=... tokenAccountIds=...`), never `404` and never an empty list. **An ADMIN token gets no exception** (the existing `ensureAccountAccess` does give admins read access to any account; it is deliberately not used for customer-owned data). Queries are keyed on the account from the path, never on an id from a body, and no request body has an account field (the JSON mapper refuses unknown fields). A notification id or alert id belonging to another account is answered exactly like one that does not exist (`NTF-404` / `ALR-404`), so ids cannot be probed. **Differences by module:** P also checks `defaultAccountId` against the token and against the same customer in SQL, so a customer cannot name someone else's account as a default. N writes rows only for the account named in an internal event. F additionally never writes to the trading tables. **Internal paths are not routes:** `ChannelResolver` and `AlertDelivery` are Java interfaces with no controller; nothing a customer's token can reach sends a message or resolves a channel for another account |
| **Disposition** | Fixed. Evidence: `AccountAuthorizerTest` (own account, other account, admin has no exception); `*ServiceTest` "another account is refused" for P, N, W, F; `ExtensionsChainIT.everyModuleRouteRefusesAnotherAccountsTokenWith403` runs real signed tokens over HTTP against all six customer routes and `everyModuleRouteRefusesARequestWithNoToken` the 401s; `notificationHistoryIsOnlyForItsOwner` and `alertsAreCappedPerAccountAndScopedToTheAccount` cover the id-probing case; `NotificationAlertDeliveryTest.deliveryIsAJavaInterfaceNotAnHttpRoute` |

### A02: Cryptographic Failures

| Aspect | Finding |
|--------|---------|
| **In scope** | Yes |
| **Modules** | P, N, F (W holds no personal data) |
| **Finding** | **Personal data (P, N):** an e-mail address or phone number is not copied anywhere. `customer_preferences` holds only the *choice* of channel; the destination is read from `clients` for one send (decision 0005). The API returns it masked (`j***@example.com`, `******3210`). The notification ledger stores no destination. `ResolvedChannel.toString()` and `OutboundMessage.toString()` print a placeholder/masked form, so a stray log line or exception cannot leak it. No contact detail appears in any Kafka payload the modules read or write. **Secrets (F):** the Fauxnance key is read from `FAUXNANCE_API_KEY`, sent only as a request header, and never appears in a response, log line, URL or exception text (`FauxnanceQuoteClientTest.aServerErrorIsAProviderFailureWithoutLeakingTheKeyOrUrl`, `theHealthCheckIsPublicAndReportsItsDependencies`). SMTP credentials come from the environment. **In transit:** local development runs plaintext HTTP to Postgres, Kafka and SMTP (`STARTTLS` is requested by default for SMTP) |
| **Disposition** | Mitigated for data at rest and secrets (nothing sensitive is stored, logged or returned unmasked). **Accepted** for transport in local development: TLS to Kafka and Postgres is the documented production plan (`contracts/kafka-topics.md`), not implemented, and is not claimed as implemented. Residual risk: anyone on the local network segment can read broker and database traffic |

### A03: Injection

| Aspect | Finding |
|--------|---------|
| **In scope** | Yes |
| **Modules** | P, N, W, F |
| **Finding** | **SQL:** every statement in the four mappers uses MyBatis `#{}` parameter binding; there is no `${}` substitution. The only concatenation is of a compile-time column-list constant in `AlertMapper`. Alerts differ most here: **W takes a symbol a customer typed** (`CreateAlertRequest`), which is validated against `^[A-Za-z0-9.:^=\-]+$` (max 20), checked against the `instruments` table, and used only as a bound parameter; **P, N and F take no free text from a customer** (a channel from a fixed enum, an id, a date). **Log injection (W):** the validated symbol cannot contain a line break. **Mail header injection (N):** messages are plain text, built from fixed fields; `ContentGuard` now refuses any control character, so a line break cannot reach a subject (found while writing this review and fixed: `ContentGuardTest.aLineBreakIsRefusedSoASubjectCannotInjectAMailHeader`). **Quote URL (F):** the symbols sent to Fauxnance come from the account's holdings in the database, are URL-encoded by the HTTP client, and are not customer-typed. No template engine or HTML is produced (e-mail is plain text) |
| **Disposition** | Fixed (control characters) and otherwise mitigated by design. `AlertControllerTest.aSymbolWithUnexpectedCharactersIsRefused`; `ExtensionsChainIT.anAlertOnAnUnknownSymbolOrWithABadThresholdIsRefused` |

### A04: Insecure Design

| Aspect | Finding |
|--------|---------|
| **In scope** | Yes |
| **Modules** | N, W, F mainly; P lightly |
| **Finding** | The design decisions that carry security weight are in `decision-log/`: **at-least-once delivery handled by idempotency, not hope** (N: unique `event_id`; W: guarded `ACTIVE`-to-`TRIGGERED` update; F: ledger primary key on `event_id`; replay proven against real Kafka in `KafkaChainIT`). **Resource limits (W):** at most 25 alerts per account, enforced inside the insert so concurrent requests cannot exceed it (decision 0012); the quote consumer's per-quote cost is a hash lookup plus the alerts on that symbol. **Fail safe (N, W):** a failure never drops a message (decision 0013). **Stale is a state (F):** a price that is old is served and marked, never hidden or passed off as live. **No caller-supplied destination (N, P):** there is no field for one, by design (see A10). **Not done:** there is no per-route rate limit on the new routes; this is the existing platform's position, and the creation route that matters (alerts) is bounded by the cap. **Known design limit (P):** the default-account preference is meaningful only when a token carries more than one account, which the auth service does not issue today (decision 0002) |
| **Disposition** | Fixed for the controls above. **Accepted**: no general rate limiting (residual risk: a customer can make many cheap reads; no write route can grow a table without bound). Owner and date in Outstanding items |

### A05: Security Misconfiguration

| Aspect | Finding |
|--------|---------|
| **In scope** | Yes |
| **Modules** | P, N, W, F, plus shared configuration |
| **Finding** | Error responses use the `{errorCode, message}` envelope only; Spring's default error page, stack traces and exception messages are not returned (`ExtensionExceptionHandler`, scoped to the four modules and ordered first so it cannot change how the Sprint 6 routes answer). `/health` is public as the contract requires and exposes only status, dependency names and the remaining quota number. The consumers have an explicit `group.id` each, auto-commit off. Docker: the existing image already runs as a non-root user. **Found, pre-existing, not introduced here:** `SecurityConfig` allows every origin, method and header (`allowedOriginPatterns("*")`). Bearer tokens are sent in a header, not a cookie, so this does not by itself enable cross-site request forgery, but it is wider than it needs to be. **Found, pre-existing:** `spring.datasource.password` has the placeholder default `<password>`. The old `docker-compose.yml` pointed at pre-refactor folders and could not start; paths are fixed and validated with `docker compose config` |
| **Disposition** | Fixed for the modules' own configuration. **Open (pre-existing):** wildcard CORS, owned by whoever owns `SecurityConfig`: in Outstanding items |

### A06: Vulnerable and Outdated Components

| Aspect | Finding |
|--------|---------|
| **In scope** | Yes |
| **Modules** | N (new dependency), all (shared) |
| **Finding** | One new backend dependency: `spring-boot-starter-mail` (managed by the Spring Boot 3.5.5 BOM, so it takes the BOM's reviewed Jakarta Mail version; it is inert unless `SMTP_HOST` is set). No new frontend dependency. `npm audit --omit=dev` on the frontend reports **0 vulnerabilities** (run 2026-10-06). **Not run:** a dependency-vulnerability scan of the Maven tree (OWASP dependency-check or equivalent) |
| **Disposition** | Accepted for the backend pending the scan; Mitigated by using only BOM-managed versions. Owner in Outstanding items |

### A07: Identification and Authentication Failures

| Aspect | Finding |
|--------|---------|
| **In scope** | Yes, limited: the modules add no login, no credential and no session |
| **Modules** | P, N, W, F (inherited) |
| **Finding** | Authentication is the existing `JwtAuthenticationFilter` on `/api/v1/**`; every new route is under that prefix and none is exempted (`ExtensionsChainIT.everyModuleRouteRefusesARequestWithNoToken`). The modules handle no passwords and send none: the content guard refuses text containing credential words, and the welcome-email password flow belongs to the auth service. **Inherited behaviour worth recording:** the access token carries a `sessionId` that the auth service uses to end an older sign-in, but the Trade REST API verifies signature, expiry, issuer and algorithm only. A token from a session that has since been replaced is therefore accepted by the new routes until it expires (at most 15 minutes), exactly as by the Sprint 6 routes. The Angular app also validates the session when navigating |
| **Disposition** | Accepted (inherited, not changed by this work). Residual risk: a replaced session's token can read its owner's own inbox and portfolio for up to 15 minutes. Owner in Outstanding items |

### A08: Software and Data Integrity Failures

| Aspect | Finding |
|--------|---------|
| **In scope** | Yes |
| **Modules** | N, F (consume `trade-events`); W (consumes `market-data`) |
| **Finding** | Consumers treat topic messages as untrusted input. They read JSON into a tree (no polymorphic or class-name deserialisation), require a valid UUID `eventId`, a known `eventType`, a positive account id and quantity, and (N) accept only the producers `trade-executor` and `trade-api`. Anything malformed is dead-lettered on the first attempt to `<topic>.DLT` with the reason in a header and does not block the partition (`KafkaChainIT.aPoisonMessageGoesToTheDeadLetterTopicAndDoesNotBlockTheMessagesBehindIt`). Money in events and quotes is parsed as exact decimals (found while testing: the default mapper turned `120.00` into a double and dropped its scale; fixed in `EventJson`). The messages are **not signed**: a producer with write access to `trade-events` can cause a notification for any account. Browser side: `active_account_id` in `sessionStorage` can be edited, but is ignored unless the token carries it (`account-context.service.spec.ts`) and the server checks again |
| **Disposition** | Mitigated. **Accepted**: unsigned events. The control is who may write to the topic: per-topic Kafka ACLs are the documented production plan and are not implemented locally. Residual risk: a compromised producer can fabricate a fill notification |

### A09: Security Logging and Monitoring Failures

| Aspect | Finding |
|--------|---------|
| **In scope** | Yes |
| **Modules** | P, N, W, F |
| **Finding** | Access refusals are logged with the token's own subject, never caller-typed text. Each module logs its events under a stable tag: `[PREFERENCES]`, `[NOTIFY]`, `[ALERTS]`, `[PORTFOLIO]`, `[ACCESS_DENIED]`. Logs contain ids, statuses and masked destinations only: no tokens, no keys, no unmasked contact detail, no message bodies (`OutboundMessage.toString()` prints the destination masked and nothing else). **Gaps:** nothing alerts on a growing `trade-events.DLT`, on notifications stuck in `QUEUED`/`HELD`/`FAILED`, or on alerts in `HANDOVER_FAILED`; they are queryable (the runbook is in `services/trade-api/README.md`) but nobody is paged. **Found, pre-existing:** `AccountController.getAccount` prints the account id with `System.out.println` |
| **Disposition** | Fixed for what the modules log. **Accepted**: no automated alerting on the failure states above, with the queries to find them documented. Owner in Outstanding items |

### A10: Server-Side Request Forgery

| Aspect | Finding |
|--------|---------|
| **In scope** | Yes. One of the two risks the brief names for this week |
| **Modules** | N (outbound channels), P (where a channel is chosen), F (quote provider); W makes no outbound request |
| **Finding** | The platform makes three kinds of outbound call from these modules, and **no destination of any of them is supplied by a caller**. (1) **Channels (N, P):** a channel is one of three enum values (`EMAIL`, `SMS`, `PUSH`); each has exactly one sender fixed in code; the destination for a message is the address/number already on the customer's own profile. There is no webhook channel and no field for a URL, host or address: a request that carries an undefined field is refused `422` (`PreferencesControllerTest.aCustomerCannotSupplyADestination`, `ExtensionsChainIT.preferencesPersistAreScopedAndRefuseADestination`), and so is a channel outside the set. The SMTP host is configuration. (2) **Quotes (F):** the Fauxnance base URL is configuration; the only caller-influenced part of a request is the symbol list, taken from the holdings in the database and URL-encoded. (3) **Alerts (W):** none. Because the e-mail address comes from the profile, a customer who can change their own profile e-mail can direct their own alerts to it; that is not SSRF (it is a mailbox, not a network location the platform fetches) |
| **Disposition** | Mitigated by design: no sink takes a caller-supplied destination. If a webhook channel is ever added it needs its own review (allow-list of hosts, no redirects, no private address ranges) before it is built |

---

## Changes to what already existed

These are the rows that matter: where a module changed something the service already did.

| What | Change | Why it matters |
|---|---|---|
| `AccountAuthorizer` | Added `ensureOwnAccount` (no admin exception); both methods now log a refusal | New strict check; the old lenient one is unchanged for the Sprint 6 routes |
| `MarketDataConsumer` | Group id changed from `trade-api-market-data` to `watchlist-service`; now calls every `QuoteObserver`; behaviour on a bad message unchanged | The old group's committed offsets are abandoned; the new group starts at `latest` and the quote cache refills on the next poll |
| Kafka error handling | One `DefaultErrorHandler` bean (bounded retry, then `<topic>.DLT`) now applies to every `@KafkaListener` in the service | Previously Spring's default (retry then skip); the DLT topics already exist in `create-topics.sh` |
| `AccountMapper` | Added read-only `getInstrumentCurrency` | A read; no write to a trading table |
| `application.properties` | No default consumer group; auto-commit off; MyBatis underscore-to-camel-case mapping on | Existing mappers use explicit result maps and are unaffected (the Sprint 6 tests still pass) |
| `pom.xml` | `spring-boot-starter-mail` | See A06 |
| `docker-compose.yml` | Paths fixed for the new folder layout; extension migration mounted; Fauxnance and SMTP environment added | The stack could not start before |
| Trading tables | **Nothing writes to `accounts`, `orders`, `positions`, `transactions`, `portfolios`, `portfolio_holdings` or `instruments` from any new code.** Each module writes only its own table | The portfolio module shares the order book's database connection; this is held by review and by the mapper contents |
| Public routes | One added: `GET /health` (contract-mandated) | See A05 |

---

## Evidence

| Check | How it was performed | Result |
|-------|---------------------|--------|
| Access control, every customer route | `ExtensionsChainIT`: real Postgres, real HTTP, real HS256 tokens signed with the service's secret; each of six routes called with no token (401) and with another account's token (403 `ACC-403`) | Pass |
| Id probing | `ExtensionsChainIT.notificationHistoryIsOnlyForItsOwner`, `alertsAreCappedPerAccountAndScopedToTheAccount`; unit tests per service | Pass |
| No destination can be supplied | Unit (`PreferencesControllerTest`) and over HTTP (`ExtensionsChainIT`): an undefined field, a channel outside the set, an `accountId` in an alert body, all `422` | Pass |
| Idempotency against a real broker | `KafkaChainIT`: the same `trade-events` message sent three times, embedded Kafka; one notification row, `attempts = 1`; committed offset read from the consumer group afterwards | Pass |
| Poison message | `KafkaChainIT`: invalid JSON followed by a valid event; the valid one is processed; the bad one is on `trade-events.DLT` once per consuming group with the original value and the exception header | Pass |
| Group ids | `KafkaChainIT.theListenersUseOneGroupEachAndNoGroupIsShared` | Pass |
| No sensitive content in messages | `ContentGuardTest`, `TradeEventListenerTest.theMessageNeverCarriesInternalIdentifiers`, `PreferencesServiceTest.theResponseNeverCarriesTheFullContactDetail`, `ExtensionsChainIT` (no column that could hold a contact detail; no address or long number in any stored body) | Pass |
| Key stays server-side | `FauxnanceQuoteClientTest` (key in header only, never in URL or exception); health response contains no key | Pass |
| Sprint 6 not regressed | Full backend suite: 306 unit tests, 4 failures, **the same 4 `OrderValidatorTest` failures that were failing before any change** (118 tests, same 4 failures at baseline). `ExtensionsChainIT.theSprintSixRoutesStillAnswerExactlyAsBefore` | No new failures. The 4 pre-existing failures are outstanding and not caused by this work |
| Frontend | `ng test`: 228 tests pass (163 before this work); `ng build` clean | Pass |
| Key never in the browser bundle | `node frontend/scripts/scan-bundle.mjs` against the production build (patterns for API keys, `x-api-key`, the quote provider's name, JWT secrets and tokens) | "Bundle scan clean" |
| Frontend dependencies | `npm audit --omit=dev` | 0 vulnerabilities |
| Backend dependencies | Not run | See Outstanding items |
| Compose file | `docker compose --profile platform config` resolves every path | Pass. The containers themselves were **not started** (no Docker daemon in the review environment) |

---

## Outstanding items

| Item | Owner | Target date |
|------|-------|------------|
| Run a dependency scan of the Maven tree (OWASP dependency-check or equivalent) and remediate anything high or critical | Team 2 | Before the cloud week |
| Narrow the wildcard CORS in `SecurityConfig` to the application's origin (pre-existing) | Team 2, owner of `SecurityConfig` | Before the cloud week |
| Make the Trade REST API reject a token whose `sessionId` has been replaced (inherited; today it is honoured until expiry) | Team 2 with the auth-service owner | Decide before the cloud week |
| Alerting on `trade-events.DLT` depth, notifications in `QUEUED`/`HELD`/`FAILED`, and alerts in `HANDOVER_FAILED` (queries are in the module README) | Team 2 / Ops | Cloud week |
| Per-topic Kafka ACLs and TLS to Kafka and Postgres (documented plan, not implemented) | Team 2 / DevOps | Cloud week |
| Rate limiting on the new read routes (none exists platform-wide) | Team 2 | Cloud week, or accept at the review |
| Fix the 4 failing `OrderValidatorTest` cases (pre-existing, unrelated to these modules) | Team 2 | Before the demonstration |
| Provision a real SMS gateway and set `SMTP_HOST` for a live e-mail send; until then the channels record `STUB_DELIVERY` | Team 2 | Before the demonstration, if a live send is to be shown |
| Add the commit hashes to the "fixed" rows once the work is committed | Team 2 | When committing |

---

## Notes

1. **A02 and A10 are the two where the module-by-module difference matters most.** P is where the customer chooses a channel, and it cannot be made to accept a destination. N is where something is actually sent, and its destination comes from one place only. F is the only module that calls a third party with a secret, and the secret never leaves the server. W calls nobody.
2. **The consumer paths have no token** (decision 0009). They do not reach customer data through a route; they write rows for the account an internal event names, and customers can read those rows only through the authorised routes in A01.
3. **What was not tested.** No live SMTP or SMS send, no running container, no load test of the quote consumer, no penetration test. The Kafka and database behaviour above was run against an embedded broker and a local Postgres 18.


## Addendum: Sprint 10 UI enhancements

Drafted by Claude on 2026-10-07; the team to confirm. Covers what was added after the review above: alert
operators, enable/disable, trigger history, notification filters and kinds, funds, candles, portfolio history,
e-mail templates, and CSV/Excel/PDF export.

| Area | Risk considered | What was done | Evidence |
|---|---|---|---|
| Alert edit, switch, history, delete | Another account's alert read or changed (A01) | Every call authorises the token's own account, then looks the alert up by id **and** account; a foreign id is 404, not 403 | `PriceAlertServiceTest.anotherAccountIsRefused...`, `ExtensionsChainIT` (account 5 on account 1's alert) |
| Alert request fields | Mass assignment (A04) | Records with fixed fields; the strict mapper refuses unknown fields (422) | `PriceAlertControllerTest`, `ExtensionsChainIT` |
| Funds | Overdraw by two requests; negative or oversized amounts; moving money on another account (A01, A04) | One conditional UPDATE; `@DecimalMin(0.01)`; per-movement cap; `ensureOwnAccount`; a failed inbox message never undoes the movement | `FundsServiceTest`, `ExtensionsChainIT.funds...` |
| Candles | The market-data key reaching the browser; path or parameter injection into the provider call (A02, A10) | The call is made by the platform; the symbol must match `^[A-Za-z0-9.:^=-]{1,20}$`, period is an enum, limit is 1 to 500; the provider URL is configuration, not input; failure is 503, never an invented chart | `CandleServiceTest`, bundle scan clean |
| Notification type filter | SQL injection through `type` (A03) | The value is checked against a fixed list in the service and bound as a parameter in the query | `ExtensionsChainIT.theInboxCanBeFiltered...` |
| Preferences: mascot and kinds | Unknown values silencing real messages (A04) | Mascot is an enum; kinds are checked against the platform's own list (422 otherwise); an unreadable preference answers "send" | `ExtensionsChainIT.theMascotAndNotification...` |
| E-mail templates | HTML injection through a subject or body (A03) | Values are HTML-escaped; only our own template file is inserted raw | `EmailTemplateRendererTest.customerTextIsEscaped...` |
| Export | CSV formula injection; leaking more than is on screen (A03) | A cell starting `= + - @` is prefixed with `'`; files are built in the browser from data already on screen; PDF and Excel libraries load on demand | `export.service.spec.ts` |
| Mascot artwork | XSS through `innerHTML` (A03) | The only trusted HTML is SVG generated from constants in `mascot-svg.ts`; no customer text reaches it (the label is from a fixed table) | `ui.spec.ts` |
| Portfolio history | Reading another account's values (A01) | `ensureOwnAccount` first; the snapshot query is keyed on the account | `PortfolioHistoryServiceTest` |

Outstanding: the welcome e-mail template exists (`email/welcome.html`, `renderWelcome`) but the sign-up flow lives
in the auth service and does not call it yet; SMS still exists on the server for accounts that stored it (the UI no
longer offers it).
