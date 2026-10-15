# Quorum: trading UI

Angular 21 workspace: standalone components and signals only, no `NgModule`. Node 20.19, 22.12 or 24+.

```bash
npm ci
npm run build
npm test
npm start            # dev server on :4200, proxying to the real services (proxy.conf.json)
```

## Feature tree

```text
src/
  environments/environment.ts        API bases (empty = this origin, via the dev proxy)
  generated/auth-api/                GENERATED from contracts/auth-api.yaml. Committed. Never edited.
  app/
    app.config.ts                    the one interceptor is registered here, once
    app.routes.ts                    /sign-in is the only unguarded route
    components/users/
      sign-in/                       sign-in form (data-testid: username, password, submit)
      dashboard/                     cash, positions, working orders, recent orders
      order-ticket/                  validated ticket, read-only account, returned status
      order-history/                 the blotter: badges, bounded re-read while anything is NEW
      account/ market/ api-status/   supporting screens
      shared/app-header/             top navigation, user, sign out
      shared/status-badge/           NEW / FILLED / REJECTED / CANCELLED: word plus shape
    core/
      auth/auth.service.ts           in-memory session, silent refresh, sign-out
      interceptors/auth.interceptor.ts   the only place an Authorization header is set
      guards/auth.guard.ts, return-url.ts   route guard and open-redirect check
      services/error-catalogue.ts    every error code -> one sentence (completeness by type)
      services/api-error-message.service.ts
      services/order|account|...     wrappers over HttpClient for the Trade REST API
      models/                        trade-api shapes, taken from the backend DTOs
e2e/                                 Playwright journeys (login.spec.ts, place-order.spec.ts)
scripts/scan-bundle.mjs              searches the built bundle for keys, secrets and hosts
```

## Generated clients

The auth client is generated from `../contracts/auth-api.yaml` with OpenAPI Generator
(`typescript-angular`), version pinned in [openapitools.json](openapitools.json) (`7.17.0`, and the CLI
wrapper is pinned exactly in `package.json`). `skipValidateSpec` is on because the generator's 3.1
validator demands `info.license.identifier`, which the OpenAPI specification makes optional and the
contracts do not carry; the contracts are correct and are not edited.

```bash
npm run generate:clients      # needs a JVM (java on PATH); a clone that only builds needs neither
git diff src/generated        # a contract change arrives as a diff a reviewer has to read
```

The output is committed (see `.gitignore`), regenerated on every contract change in the same commit as
the code that adapts to it, and never edited by hand. `core/auth/auth.service.ts` wraps it.

**Trade API:** `contracts/trade-api.yaml` is not in the repository yet, so there is no trade client to
generate. `core/models` and the services beside it follow the Spring Boot DTOs and controllers in
`backend/trade-api` (the source of truth for now). When `trade-api.yaml` lands, add a second entry to
`openapitools.json`, regenerate, and replace those hand-written shapes.

## Look and feel

Sage and forest, in light and dark. Every colour is a token on `:root` in `src/styles.css`; the dark
values live under `:root[data-theme='dark']`. `ThemeService` (`core/theme`) switches the attribute,
remembers the choice in localStorage (a colour preference is not sensitive) and falls back to the
system setting; a small script in `index.html` applies it before first paint so there is no flash.
Motion (page enter, count-up figures, sliding Buy/Sell thumb, drawn tick, badge pulse) is all CSS or
`requestAnimationFrame` and switches off under `prefers-reduced-motion`.

## Security notes

- The token lives in memory only (a signal). Never localStorage or sessionStorage.
- `authInterceptor` is an allow list of platform origin plus protected path: `/api/v1/**` on the Trade
  REST API and `/auth/me` on the auth service. `/auth/login`, `/auth/register` and `/auth/refresh` take
  no header. Any other host, and any other path on this origin, gets no token.
- The route guard is a usability control, not a security control. Authorisation is the Trade REST API's
  decision on every `/api/v1` call. A return address is followed only if it is a path on this origin.
- The dev server proxies `/api`, `/auth` and `/actuator` (proxy.conf.json) because the Nest auth
  service does not enable CORS. If you serve the build from another origin, set absolute bases in
  `environment.ts` and enable CORS on those services.
- The UI never calls the Fauxnance API and holds no key or secret.

## Order status and the blotter

Execution is asynchronous: `POST /api/v1/orders` answers with the state at acceptance, normally `NEW`.
The ticket shows that as returned. The blotter re-reads order history every 3 s while anything is `NEW`,
at most 10 times, then stops and says so; Refresh opens a new window. It never re-posts an order.

## End-to-end and bundle checks

```bash
cp .env.example .env          # fill in E2E_USERNAME / E2E_PASSWORD, export the values
npm run e2e:install           # once: downloads Chromium
npm run e2e                   # both journeys, against the real stack
npx playwright test e2e/login.spec.ts        # run each file on its own before review
npm run build && npm run scan:bundle         # add your own key/secret literals as arguments
```
