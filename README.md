# Enterprise Trading Platform (Team 2)

Enterprise Trading Platform (ETP) is a multi-service trading system where customers can authenticate, place orders, track positions, review portfolio and P&L, manage alerts and notifications, and use a modern Angular frontend.

The platform combines:
- transactional order processing (Trade API + Executor)
- authentication and session/token flows (Auth Service)
- event-driven processing (Kafka topics and consumers)
- portfolio pricing and P&L logic
- browser UI (Angular 21)
- analytics pipeline assets (ETL_Layer)

This README is for the full project, not only Sprint 10.

## What is in this repository

### Core services
- `services/trade-api` (Spring Boot, Java 21, MyBatis, Kafka)
- `services/executor` (Spring Boot, Java 21)
- `services/auth_service` (NestJS, TypeScript, PostgreSQL)
- `frontend` (Angular 21 standalone app)

### Platform infrastructure
- `docker-compose.yml` (platform orchestration with profiles)
- `config/application.yaml` (shared host / port / database / Kafka settings from the deployment PDF)
- `.env.example` and `.env` (every environment variable the platform reads; see below)
- `databases/postgreSQL/schema.sql`, `seed-data.sql`, `reset.sql`, `migrations/` (database build)
- `infrastructure/kafka/scripts/create-topics.sh` (topic provisioning)

### Layout against the deployment PDF ("ETP - System Architecture")

| PDF folder | In this repository |
|---|---|
| Contracts (API Schemas, Analytics Schemas) | `contracts/` (`*-api.yaml`, `analytics-schema.sql`) |
| Databases / DuckDB / analytics | `databases/DuckDB/analytics` |
| Databases / PostgreSQL (migrations, reset.sql, schema.sql, seed-data.sql) | `databases/postgreSQL/` |
| ETL Layer | `ETL_Layer/` |
| Frontend (port 4200) | `frontend/` |
| Infrastructure / Kafka | `infrastructure/kafka/` |
| Services: auth-service (3000), order-service (8081), executor-service (8082) | `services/auth_service`, `services/trade-api`, `services/executor` |
| Config / application.yml | `config/application.yaml` |

### Contracts and architecture records
- `contracts/` (OpenAPI and topic/data contracts)
- `decision-log/` (architecture and design decisions)
- `security-review/Sprint10_Security_Review.md` (combined OWASP review)

## Sprint delivery map (project-wide)

The repository contains work delivered across multiple sprints. The primary milestones are:

| Sprint | Main outcome | Key locations |
|---|---|---|
| Sprint 3 | Operational transactional schema foundations | `databases/postgreSQL/migrations/001_initial_schema.sql` |
| Sprint 4 | Analytics schema and ETL/dashboard assets | `contracts/analytics-schema.sql`, `ETL_Layer/` |
| Sprint 6 | Core Trade REST API + account/order routes | `services/trade-api`, `contracts/trade-api.yaml` |
| Sprint 7 | Event-driven execution, Kafka topics, consumers | `services/executor`, `contracts/kafka-topics.md`, `infrastructure/kafka/` |
| Sprint 8 | Auth service + JWT lifecycle and validation | `services/auth_service`, `contracts/auth-api.yaml` |
| Sprint 9 | Angular UI with generated auth client and guarded flows | `frontend/` |
| Sprint 10 | Extension modules and UI enhancements | `services/trade-api/src/main/java/com/capstone/etp/{preferences,notifications,alerts,portfolio,...}` and `docs/sprint-10/` |

For contract lineage, see `contracts/README.md` (`First used` column).

## Sprint 10 extension modules (inside Trade API)

These are packages inside `services/trade-api`, not separate deployables:

| Module | Package | Contract | README |
|---|---|---|---|
| Customer preferences | `preferences` | `contracts/preferences-api.yaml` | `services/trade-api/src/main/java/com/capstone/etp/preferences/README.md` |
| Customer notifications | `notifications` | `contracts/notifications-api.yaml` | `services/trade-api/src/main/java/com/capstone/etp/notifications/README.md` |
| Watchlists & price alerts | `alerts` | `contracts/alerts-api.yaml` | `services/trade-api/src/main/java/com/capstone/etp/alerts/README.md` |
| Portfolio & P&L | `portfolio` | `contracts/portfolio-api.yaml` | `services/trade-api/src/main/java/com/capstone/etp/portfolio/README.md` |

Additional modules:
- `services/trade-api/src/main/java/com/capstone/etp/advice/README.md`
- `services/trade-api/src/main/java/com/capstone/etp/automation/README.md`
- `services/trade-api/src/main/java/com/capstone/etp/assistant/README.md`

## High-level architecture

1. Users sign in through the Auth Service and receive JWT tokens.
2. Frontend calls Trade API routes with bearer token for account-scoped operations.
3. Trade API writes transactional data to PostgreSQL and publishes/consumes Kafka events.
4. Executor processes eligible orders using market data and emits trade events.
5. Extension modules consume events for notifications and realised P&L booking.
6. Portfolio module prices holdings via Fauxnance (server-side only), with batching/caching/stale handling.

## Compose profiles

`docker-compose.yml` defines multiple profiles:

| Profile | Purpose |
|---|---|
| `platform` | Full runtime stack (Postgres, Auth Service, Order service (`backend`), Executor, Kafka, topic init, Kafbat UI) |
| `sonar` | SonarQube server |
| `sonar-scan` | Scanner containers for executor, trade-api, and ETL layer |

## Prerequisites

- Docker + Docker Compose
- Java 21 (for local service runs)
- Maven 3.9+
- Node.js 20+ (frontend) and Node.js 18+ (auth service)
- npm
- PostgreSQL client tools (`psql`) for manual migration/debug work

## Service ports (deployment PDF)

| Service | URL | Port |
|---|---|---|
| Frontend application | `http://localhost` | 4200 |
| auth-service | `http://localhost` | 3000 |
| order-service (`services/trade-api`) | `http://localhost` | 8081 |
| executor-service (`services/executor`) | `http://localhost` | 8082 |
| PostgreSQL (`trading_system_db`) | `localhost` | 5432 |
| Kafka (Docker network) | `kafka` | 29092 |

The same values are in `config/application.yaml`, `.env.example` and `.env`. Nothing else is written in code:
`npm`, `mvn` and `docker compose` all read them from the `.env` at the repository root.

## Setup from a fresh clone

```bash
cp .env.example .env            # then fill in the blank secrets (see "Environment configuration")
node scripts/validate-config.mjs
```

The platform runs in either of two ways, and the same `.env` serves both. Full prerequisites:
`docs/deployment-prerequisites.md`.

### A. Docker (recommended)

```bash
docker compose --profile platform up -d --build
```

Compose creates the database on first start from `databases/postgreSQL/schema.sql` and `seed-data.sql`, builds the
three services, and reaches Kafka as `kafka:29092` on the Docker network. Also published on the host: the Kafka
external listener (`KAFKA_EXTERNAL_PORT`, default 9092) and Kafbat UI (`KAFKA_UI_PORT`, default 8086).

```bash
docker compose --profile platform down     # stop
docker compose --profile platform config   # validate the file and the resolved values
```

### B. No Docker (services started directly on the machine)

1. Database: `bash databases/postgreSQL/scripts/apply.sh` (needs `psql`; creates `trading_system_db` if missing).
2. Kafka: set `KAFKA_BOOTSTRAP_SERVERS` in `.env`. A broker on the Linux VM is `<vm-ip>:9092`; a broker on this machine
   is `localhost:9092`. Changing that one line is all it takes to switch.
3. Start the services (see "Running services locally") in this order: auth service, order service, executor, frontend.

## Environment configuration

`.env.example` lists **every** variable the platform reads, grouped by service; `.env` (git-ignored because it holds real
secrets) has the same keys. `node scripts/validate-config.mjs` fails when the two differ, when a variable used in code is
missing from `.env.example`, when a deployment-PDF value is wrong, or when a hardcoded URL, IP or key is found in source.

How each part reads it:
- Spring Boot services: `application.properties` contains only `${NAME}` references. Started from `services/<name>`, they
  also read `../../.env`; variables already in the process environment win. A missing required variable stops start-up.
- Auth service: `ConfigModule` reads the root `.env`; a missing required variable stops start-up and names it.
- Frontend: `npm start`, `build`, `watch` and `test` first run `frontend/scripts/set-env.mjs`, which writes the
  git-ignored `src/environments/environment.ts` (API URLs) from `.env`; `npm start` serves on `FRONTEND_PORT`.
- Docker Compose: every value is `${NAME}` from `.env`; containers reach each other by service name.

Variables that whoever deploys must fill in: `JWT_SECRET` (the same value for all services), `DB_PASSWORD`,
`FAUXNANCE_API_KEY`, and optionally `SMTP_*` and `GEMINI_API_KEY` (without them e-mail is only logged and the assistant
answers from account data).

Helper script for remote Linux VM setup (copies `.env` to the VM and restarts the stack):

```bash
./setup-jwt-docker.sh <vm-user> <vm-ip>
```

## Running services locally (without Docker orchestration)

### Frontend

```bash
cd frontend
npm ci
npm start        # http://localhost:4200 (FRONTEND_PORT); API URLs come from .env
```

Also available:

```bash
npm run build
npm test
npm run e2e:install
npm run e2e
npm run scan:bundle
```

### Auth service

```bash
cd services/auth_service
npm install
npm run start:dev        # port AUTH_SERVICE_PORT (3000)
```

The first admin is created with `ADMIN_PASSWORD='<12+ characters>' npm run create-admin -- <username> <accountId>`.

Tests:

```bash
npm test
npm run test:cov
```

### Trade API

```bash
cd services/trade-api
mvn spring-boot:run
```

Tests:

```bash
mvn test        # unit tests need no environment; ExtensionsChainIT / KafkaChainIT run when TEST_DB_URL is set
```

### Executor

```bash
cd services/executor
mvn spring-boot:run
```

## Database

One PostgreSQL database (`DB_NAME`, `trading_system_db`) with two schemas:

| Schema | Owner | Contents |
|---|---|---|
| `trade` | order-service and executor-service | clients, accounts, portfolios, instruments, holdings, orders, transactions, watchlist, preferences, notifications, price alerts, P&L, cash movements, automation |
| `auth` | auth-service | `auth_users` (credentials, roles), `refresh_tokens` |

Each service selects its schema through its connection (`DB_SCHEMA_TRADE` / `DB_SCHEMA_AUTH`), so no SQL carries a
schema prefix. Credentials exist only in `auth.auth_users`: the `clients` table has no password column.
One client has exactly one account (`accounts.client_id` is UNIQUE).

Files in `databases/postgreSQL/`:
- `schema.sql` - the complete schema, safe to run repeatedly
- `seed-data.sql` - baseline data: 20 clients, each with exactly one account (client N owns account N), portfolios, instruments, orders, watchlists
- `reset.sql` - drops both schemas (destructive)
- `migrations/001..007` - the same schema built step by step (`apply.sh --migrations`); verified to produce an identical schema
- `scripts/apply.sh` - creates the database if needed, then runs `schema.sql` and `seed-data.sql`
  (options `--reset`, `--migrations`, `--no-seed`, `--queries`)

Logins are not seeded: create them through the auth service (`POST /auth/register` with an existing `accountId`).

## Contracts and API-first development

Contracts are in `contracts/` and are binding between modules/teams.

Useful commands:

```bash
npx @redocly/cli lint contracts/trade-api.yaml
npx @redocly/cli preview-docs contracts/trade-api.yaml
```

For frontend generated clients:

```bash
cd frontend
npm run generate:clients
```

## Testing strategy

- **Frontend**: Angular unit tests + Playwright e2e.
- **Trade API**: unit tests by default; integration tests for extension chains/kafka paths when configured.
- **Auth service**: Jest tests for auth flows, repositories, and password logic.
- **Executor**: Maven/JUnit tests.
- **ETL_Layer**: Python tests under `ETL_Layer/tests`.

ETL quick run (if working on analytics):

```bash
cd ETL_Layer
python -m pip install -e .[dev]
pytest
```

## Security and operational references

- Combined security review: `security-review/Sprint10_Security_Review.md`
- Decision log: `decision-log/`
- Trade API runbook queries: `services/trade-api/README.md`

## Troubleshooting

- Verify compose configuration:

```bash
docker compose --profile platform config
```

- Check service logs:

```bash
docker compose logs backend
docker compose logs auth-service
docker compose logs trade-executor
```

- Kafka dead letters:
	- inspect `trade-events.DLT` in Kafbat UI (`http://localhost:8086`)

- If frontend calls fail locally:
	- verify `.env` (ports and URLs), then run `node scripts/validate-config.mjs`
	- the dev-server proxy is `frontend/proxy.conf.mjs` and also reads `.env`

## Related docs

- Sprint 10 seams: `docs/sprint-10/seams.md`
- Sprint 10 demo script: `docs/sprint-10/demo.md`
- Sprint 10 scope/backlog: `docs/sprint10/scope-and-backlog.md`
- Trade API details: `services/trade-api/README.md`
- Auth service details: `services/auth_service/README.md`
- Deployment prerequisites: `docs/deployment-prerequisites.md`
- Frontend details: `frontend/README.md`
- Contracts guide: `contracts/README.md`
- Decision template: `decision-log/TEMPLATE.md`

