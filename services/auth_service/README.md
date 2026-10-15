# Auth Service - Sprint 8

Enterprise Trading Platform Authentication Service built with NestJS.

## Overview

The Auth Service handles registration, login, token refresh, and current-user profile endpoints according to the contract specified in `contracts/auth-api.yaml`. This service issues JWT tokens for use across the platform.

## Quick Start

### Prerequisites
- Node.js 18+
- PostgreSQL 12+
- npm or yarn

### Installation

```bash
# Install dependencies
npm install

### Database Setup

```bash
# The auth service uses the schema "auth" of the platform database (DB_NAME). From the repository root:
cp .env.example .env            # fill in JWT_SECRET and DB_PASSWORD
bash databases/postgreSQL/scripts/apply.sh

# (migrations/001_initial_schema.sql here is the same auth schema, for running it on its own)
```

### Start the Service

```bash
# Development
npm run start:dev

# Production
npm run build
npm run start:prod
```

The service will start on port 3000 (or the PORT environment variable).

## OpenAPI Documentation

The running service publishes OpenAPI generated from NestJS controller and DTO decorators.

- Swagger UI: `/docs`
- OpenAPI JSON: `/docs/json`

Local URLs:

- `http://localhost:3000/docs`
- `http://localhost:3000/docs/json`

## API Endpoints

All endpoints return responses wrapped in the platform error envelope:

```json
{
  "errorCode": "AUTH-401|AUTH-409|VAL-422",
  "message": "Descriptive message"
}
```

### POST /auth/register

Register a new user and link to an existing trading account.

**Request:**
```json
{
  "username": "priya.menon",
  "password": "correct horse battery staple",
  "accountId": 1,
  "roles": ["CUSTOMER"]  // Optional, defaults to ["CUSTOMER"]
}
```

**Response (201):**
```json
{
  "id": "8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f",
  "username": "priya.menon",
  "accountId": 1,
  "roles": ["CUSTOMER"],
  "createdOn": "2026-10-05T08:00:00Z"
}
```

**Errors:**
- `AUTH-409`: Username already taken
- `VAL-422`: Validation error (see constraints below)

**Validation Rules:**
- `username`: 3-64 chars, pattern `^[a-zA-Z0-9._-]+$`
- `password`: 12-128 chars (no character-class requirements)
- `accountId`: positive integer, must exist in trade-api accounts

### POST /auth/login

Authenticate with credentials and receive token pair.

**Request:**
```json
{
  "username": "priya.menon",
  "password": "correct horse battery staple"
}
```

**Response (200):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "9c1f7a2e4b6d8e0a2c4e6a8c0e2a4c6e8a0c2e4a6c8e0a2c",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Errors:**
- `AUTH-401`: Unknown user or invalid password (identical response for both)
- `VAL-422`: Validation error

### POST /auth/refresh

Exchange a refresh token for a new token pair.

**Request:**
```json
{
  "refreshToken": "9c1f7a2e4b6d8e0a2c4e6a8c0e2a4c6e8a0c2e4a6c8e0a2c"
}
```

**Response (200):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "new-refresh-token-here",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Errors:**
- `AUTH-401`: Invalid/expired/consumed refresh token
- `VAL-422`: Validation error

**Important:** Double-use of a refresh token is treated as theft and revokes all tokens for that user.

### GET /auth/me

Get the authenticated user profile. Requires bearer token.

**Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response (200):**
```json
{
  "id": "8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f",
  "username": "priya.menon",
  "accountId": 1,
  "roles": ["CUSTOMER"],
  "createdOn": "2026-10-05T08:00:00Z"
}
```

**Errors:**
- `AUTH-401`: Invalid/expired/missing token

## JWT Claims Contract

All access tokens carry these claims:

| Claim | Type | Meaning |
|---|---|---|
| `sub` | string | User ID (UUID) - stable for life of user |
| `accountId` | number | Trading account key from `accounts.id` |
| `roles` | string[] | Authorization roles: `CUSTOMER` or `ADMIN` |
| `iat` | number | Issued-at (seconds since Unix epoch) |
| `exp` | number | Expiry (15 minutes after iat) |
| `iss` | string | Issuer: `auth-service` |

Example decoded token:
```json
{
  "sub": "8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f",
  "accountId": 1,
  "roles": ["CUSTOMER"],
  "iat": 1790000000,
  "exp": 1790000900,
  "iss": "auth-service"
}
```

## Security Features

### Password Storage
- Passwords are hashed with **argon2id** (cost 3, memory 64MB)
- Never logged or exposed in error messages
- Minimum 12 characters required

### Token Lifecycle
- **Access tokens**: 15 minutes, not revocable (short-lived)
- **Refresh tokens**: 7 days, stored server-side, revocable
- Refresh tokens are rotated on each refresh
- Tokens are signed with HS256 and a shared secret

### Threat Mitigations

#### 1. Unified Login Failure Response (Timing-Attack Mitigation)
**Problem**: Unknown username and wrong password can be distinguished by response time.
- Unknown user path returns immediately after failed lookup (fast, ~1ms)
- Wrong password path verifies hash (slow, ~100ms)
- Attacker with username list uses stopwatch to enumerate valid accounts

**Solution**: 
- When username is **not found**, verify password against a dummy hash (using same argon2id parameters)
- Result is discarded, but operation takes ~100ms (same as real password verification)
- Both paths now return identical status (401), identical message ("Unauthorised"), and comparable timing
- Response variance between paths is deterministically masked

#### 2. Login Throttle (Brute-Force Protection)
**Configuration**:
- **Max attempts**: 5 failed login attempts
- **Cooldown window**: 15 minutes (900 seconds)
- **Tracking**: Per-username
- **Behavior**: Both unknown-user and wrong-password paths count as failures

**How it works**:
- Records timestamp of each failed login attempt
- Blocks new attempts if 5 failures recorded within cooldown window
- Automatically clears old attempts after cooldown expires
- Neither path returns early during throttle (maintains timing consistency)

**Important Notes**:
- Throttle is applied **after** timing-attack mitigation
- Throttle itself is not a timing oracle—it just blocks attempts
- The dummy password verification still runs even during throttle
- Distributed systems: Current implementation is single-instance in-memory (suitable for auth service with session affinity)

#### 3. Other Protections
- **Token reuse**: Consuming a token twice revokes all tokens for that user
- **Uniform responses**: Same response for unknown user, wrong password, expired token, invalid signature, malformed header
- **Privilege escalation**: Clients cannot self-assign roles during registration

### Logging
- All auth events logged with `[AUTH_EVENT]` prefix
- Never logs passwords, tokens, or PII
- Events include: `register_attempt`, `login_attempt`, `login_throttled`, `refresh_attempt`, `token_reuse_detected`

## Database Schema

### auth_users
```sql
CREATE TABLE auth_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(64) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    account_id BIGINT NOT NULL,
    roles TEXT[] NOT NULL DEFAULT ARRAY['CUSTOMER'],
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### refresh_tokens
```sql
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);
```

## Testing

```bash
# Run all tests
npm run test

# Watch mode
npm run test:watch

# Coverage report
npm run test:cov
```

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DB_HOST` | Yes | - | PostgreSQL host |
| `DB_PORT` | Yes | - | PostgreSQL port (usually 5432) |
| `DB_USERNAME` | Yes | - | PostgreSQL user |
| `DB_PASSWORD` | Yes | - | PostgreSQL password |
| `DB_NAME` | Yes | - | PostgreSQL database name |
| `JWT_SECRET` | Yes | - | Secret for JWT signing (min 32 chars) |
| `PORT` | No | 3000 | Service port |
| `NODE_ENV` | No | development | Environment mode |

## Integration with Trade API

The Trade API validates incoming access tokens:

1. **Same JWT_SECRET**: Both services must use the same secret for HS256 verification
2. **Issuer validation**: Trade API checks the `iss` claim is `auth-service`
3. **Account isolation**: Trade API compares `accountId` claim against request path/body
4. **Token verification**: Every request validates signature, expiry, and issuer

Example Trade API integration:
```typescript
// Verify token sent from client
const payload = jwt.verify(token, process.env.JWT_SECRET, {
  algorithms: ['HS256'],
  issuer: 'auth-service'
});

// Ensure user's accountId matches request
if (payload.accountId !== requestedAccountId) {
  throw new HttpException('ACC-403', HttpStatus.FORBIDDEN);
}
```

## Deployment Checklist

- [ ] Database created with proper schema
- [ ] Environment variables set (especially JWT_SECRET)
- [ ] Service can connect to database
- [ ] **Login throttle configured**: 5 attempts per 15 minutes (900 seconds)
- [ ] **Timing-attack mitigation verified**: Test unknown-user and wrong-password paths take comparable time
- [ ] CORS configured for UI domain
- [ ] Monitoring alerts set for auth failures and throttle events
- [ ] JWT_SECRET rotation procedure documented
- [ ] Logs captured and retained per compliance requirements

## Common Issues

**Issue**: "Cannot find module 'argon2'"
- **Solution**: Run `npm install` to ensure all dependencies are installed

**Issue**: Database connection refused
- **Solution**: Check DB_HOST, DB_PORT, and credentials in .env

**Issue**: "JWT_SECRET not set"
- **Solution**: Generate with `openssl rand -base64 48` and set in .env

**Issue**: "Port 3000 already in use"
- **Solution**: Set `PORT` environment variable or kill process on port 3000

## API Documentation

OpenAPI/Swagger documentation is available at:
- **Live**: http://localhost:3000/docs
- **JSON**: http://localhost:3000/docs/json

## License

Instructional use only. See root LICENSE file.
