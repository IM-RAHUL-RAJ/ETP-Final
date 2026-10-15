# Deployment prerequisites

Everything needed to recreate the environment from a clean machine. The values below are the deployment PDF's
("ETP - System Architecture"); they are the defaults in `.env.example` and `config/application.yaml`.

## 1. Software

| Needed for | Software |
|---|---|
| Docker route | Docker Engine + Docker Compose v2 |
| Services without Docker | Java 21 and Maven 3.9+ (order, executor); Node.js 20+ and npm (auth, frontend) |
| Database without Docker | PostgreSQL 16+ and the `psql` client |
| Messaging | An Apache Kafka 3.8 broker (compose starts one; or the Linux VM's broker) |
| Checks | Node.js 18+ for `scripts/validate-config.mjs` |

## 2. Configuration

1. `cp .env.example .env` at the repository root.
2. Fill in the secrets that are blank or specific to your environment:
   - `JWT_SECRET` - at least 32 characters, **identical for auth, order and executor** (`openssl rand -base64 48`)
   - `DB_PASSWORD` - the PostgreSQL password for `DB_USER`
   - `FAUXNANCE_API_KEY` - market-data key (the base URL is already set)
   - optional: `SMTP_HOST/PORT/USER/PASS/FROM` (welcome and notification e-mail), `GEMINI_API_KEY` (assistant)
3. Choose the Kafka address for services started without Docker: `KAFKA_BOOTSTRAP_SERVERS=<vm-ip>:9092` for the Linux VM
   broker, `localhost:9092` for a local one. Docker containers always use `KAFKA_HOST:KAFKA_PORT` (`kafka:29092`).
4. `node scripts/validate-config.mjs` must print "Configuration check passed".

| Component | Setting | Value |
|---|---|---|
| Frontend | `FRONTEND_URL` / `FRONTEND_PORT` | `http://localhost` / `4200` |
| auth-service | `AUTH_SERVICE_URL` / `AUTH_SERVICE_PORT` | `http://localhost` / `3000` |
| order-service | `ORDER_SERVICE_URL` / `ORDER_SERVICE_PORT` | `http://localhost` / `8081` (API base `/api/v1`) |
| executor-service | `EXECUTOR_SERVICE_URL` / `EXECUTOR_SERVICE_PORT` | `http://localhost` / `8082` |
| PostgreSQL | `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` | `localhost` `5432` `trading_system_db` `postgres` |
| Schemas | `DB_SCHEMA_TRADE` / `DB_SCHEMA_AUTH` | `trade` / `auth` |
| Kafka (Docker network) | `KAFKA_HOST` / `KAFKA_PORT` | `kafka` / `29092` |

## 3. Database

```bash
bash databases/postgreSQL/scripts/apply.sh            # creates trading_system_db, then schema.sql and seed-data.sql
bash databases/postgreSQL/scripts/apply.sh --reset    # wipe and rebuild (destructive)
```

With Docker, `docker compose --profile platform up -d` does the same on the first start (empty volume).
After a schema change on an existing volume, run `docker compose down -v` first (this deletes the data).

## 4. Start-up order

1. PostgreSQL, then Kafka (topics `orders`, `trade-events`, `market-data` and the dead-letter topics are created by
   `kafka-init`, or by `infrastructure/kafka/scripts/create-topics.sh` with `KAFKA_BOOTSTRAP=<host:port>`)
2. auth-service, order-service, executor-service
3. frontend (`cd frontend && npm ci && npm start`)

## 5. First login

Logins are not seeded. Create the first admin, then customers through the admin screen or the API:

```bash
cd services/auth_service
ADMIN_PASSWORD='<at least 12 characters>' npm run create-admin -- <username> <existing accountId, e.g. 1>
curl -X POST http://localhost:3000/auth/register -H 'Content-Type: application/json' \
     -d '{"username":"john.doe","password":"<12+ characters>","accountId":1}'
```

## 6. Verification

- `curl http://localhost:3000/health`, `curl http://localhost:8081/actuator/health`, `curl http://localhost:8082/actuator/health`
- Sign in at `http://localhost:4200`.
