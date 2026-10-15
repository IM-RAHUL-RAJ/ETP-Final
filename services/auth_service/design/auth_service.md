# Auth Service - Team 2 Capstone Project


## Overview

The Auth Service provides secure credential management, JWT token generation, and cross-database account validation for the trading platform. It acts as the authoritative token issuer for the entire microservices ecosystem, with tokens verified by the Trade API without any code changes—only configuration updates.

**Key Achievement:** Trade REST API protection implemented purely through environment configuration, with no Java code modifications required.

## Features

- ** Secure Credential Storage**
  - Argon2id password hashing with configurable cost parameters
  - No plaintext passwords stored or logged
  - ~100ms password verification on standard CPU
  
- ** Cross-Database Account Validation**
  - Validates account existence in SpringBoot database
  - Verifies username matches client name
  - Ensures account status is ACTIVE
  - Prevents account mismatch vulnerabilities
  
- ** JWT Token Management**
  - Stateless token generation and verification
  - Configurable token expiration (default 15 minutes)
  - Refresh token support for extended sessions
  - Issuer-based token validation chain
  
- ** Security Hardening**
  - RedactedLogger prevents sensitive field leakage in logs
  - Automatic redaction of passwords, tokens, secrets, and API keys
  - Global exception filter with sensitive data removal
  - Comprehensive password validation rules
  
- ** Seamless Trade API Integration**
  - Single JWT_SECRET shared via docker-compose orchestration
  - No code changes required in Trade API
  - Configuration-driven token verification chain
  - Environment variable substitution at runtime

##  Quick Start

### Prerequisites
- Node.js 20+
- npm or yarn
- PostgreSQL 13+ (shared with Trade API)
- Docker & Docker Compose (for orchestration)

### Local Development Installation

```bash
# Install dependencies
npm install

# Configure environment variables (root .env file)
# JWT_SECRET=dev-jwt-secret-at-least-32-chars-long-12345678
# JWT_ISSUER=auth-service
# DB_NAME=auth_db
# DB_NAME=trading_system_db

# Run database migrations
npm run typeorm migration:run

# Start the service
npm start
```

Service available at: `http://localhost:3000`

##  API Endpoints

### 1. Register New User

**Endpoint Image Name:** `auth-register-endpoint.png`

**Request**
```http
POST /auth/register
Content-Type: application/json

{
  "username": "trader_john",
  "password": "SecurePass123",
  "accountId": 42,
  "roles": ["CUSTOMER"]
}
```

**Response (201 Created)**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "trader_john",
  "accountId": 42,
  "roles": ["CUSTOMER"],
  "createdOn": "2026-09-25T10:30:00Z"
}
```

**Status Codes:**
- `201 Created` - User registered successfully
- `400 Bad Request` - Invalid input or account validation failed
  ```json
  {
    "statusCode": 400,
    "message": "Account validation failed: account status is not ACTIVE",
    "errorCode": "AUTH-400"
  }
  ```
- `409 Conflict` - Username already exists
  ```json
  {
    "statusCode": 409,
    "message": "Username already registered",
    "errorCode": "AUTH-409"
  }
  ```

**Validation Rules:**
- `username`: 3-12 characters, alphanumeric + . _ -
- `password`: 8-12 characters, must be strong
- `accountId`: Positive integer, must exist in the trade schema of trading_system_db
- `roles`: Optional array of role strings

---

### 2. Login User

**Endpoint Image Name:** `auth-login-endpoint.png`

**Response Image Name:** `auth-login-success-response.png`

**Request**
```http
POST /auth/login
Content-Type: application/json

{
  "username": "trader_john",
  "password": "SecurePass123"
}
```

**Response (200 OK)**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Status Codes:**
- `200 OK` - Login successful
- `401 Unauthorized` - Invalid username or password
  ```json
  {
    "statusCode": 401,
    "message": "Invalid credentials",
    "errorCode": "AUTH-401"
  }
  ```
- `404 Not Found` - User not found
  ```json
  {
    "statusCode": 404,
    "message": "User not found",
    "errorCode": "AUTH-404"
  }
  ```

**Token Details:**
- `accessToken`: JWT signed with HS256, expires in 15 minutes
- `refreshToken`: Stored in database, expires in 7 days
- `tokenType`: Always "Bearer" for HTTP Authorization header
- `expiresIn`: Token lifetime in seconds

---

### 3. Refresh Access Token

**Endpoint Image Name:** `auth-refresh-token-endpoint.png`

**Response Image Name:** `auth-refresh-token-success.png`

**Request**
```http
POST /auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200 OK)**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Status Codes:**
- `200 OK` - Token refreshed successfully
- `401 Unauthorized` - Invalid or expired refresh token
  ```json
  {
    "statusCode": 401,
    "message": "Refresh token is invalid or expired",
    "errorCode": "AUTH-401"
  }
  ```

**Token Rotation:**
- Old accessToken is invalidated
- New accessToken issued with fresh expiration
- RefreshToken can be rotated or reused based on config

---

### 4. Get Current User Info

**Endpoint Image Name:** `auth-get-current-user-endpoint.png`

**Response Image Name:** `auth-get-current-user-success.png`

**Request** (JWT Protected)
```http
GET /auth/me
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response (200 OK)**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "trader_john",
  "accountId": 42,
  "roles": ["CUSTOMER"],
  "createdOn": "2026-09-25T10:30:00Z"
}
```

**Status Codes:**
- `200 OK` - User info retrieved successfully
- `401 Unauthorized` - Missing, invalid, or expired JWT
  ```json
  {
    "statusCode": 401,
    "message": "Unauthorized: Invalid or expired token",
    "errorCode": "AUTH-401"
  }
  ```
- `400 Bad Request` - Malformed Authorization header
  ```json
  {
    "statusCode": 400,
    "message": "Invalid Authorization header format",
    "errorCode": "AUTH-400"
  }
  ```

**Authorization Header Format:**
- Must include: `Authorization: Bearer <accessToken>`
- Token automatically decoded and validated
- User claims extracted and returned

---

##  Integration with Trade REST API

### Configuration-Only Integration

No Java code changes required in Trade API. Integration achieved through environment variable orchestration:

**1. Root Environment File (`.env`)**
```env
JWT_SECRET=dev-jwt-secret-at-least-32-chars-long-12345678
JWT_ISSUER=auth-service
```

**2. Docker Compose Pass-Through (`docker-compose.yml`)**
```yaml
backend:  # Trade API
  environment:
    JWT_SECRET: ${JWT_SECRET}        # Passed from root .env
    JWT_ISSUER: ${JWT_ISSUER}        # Passed from root .env
    AUTH_BASE_URL: http://auth-service:3000
  depends_on:
    auth-service:
      condition: service_started
```

**3. Trade API Configuration (`application.properties`)**
```properties
app.auth.jwt.secret=${JWT_SECRET}
app.auth.jwt.issuer=${JWT_ISSUER:auth-service}
app.auth.jwt.algorithm=${JWT_ALGORITHM:HS256}
```

**4. JWT Verification in Trade API (`JwtVerifier.java`)**
```java
@Component
public class JwtVerifier {
    private final Key signingKey;
    private final String expectedIssuer;
    
    public JwtVerifier(
        @Value("${app.auth.jwt.secret}") String secret,
        @Value("${app.auth.jwt.issuer:auth-service}") String issuer
    ) {
        this.signingKey = Keys.hmacShaKeyFor(
            secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expectedIssuer = issuer;
    }
    
    public JwtClaims verify(String token) {
        // Verifies signature using JWT_SECRET
        // Verifies issuer matches 'auth-service'
        // Returns validated claims
    }
}
```

### Usage in Protected Routes

The Trade API can now protect routes by verifying tokens issued by auth-service:

```java
@RestController
@RequestMapping("/api/v1")
public class AccountController {
    
    @GetMapping("/accounts/{id}")
    @PreAuthorize("isAuthenticated()")  // JWT must be valid
    public ResponseEntity<Account> getAccount(@PathVariable Long id) {
        // Automatic JWT verification via JwtAuthenticationFilter
        // AuthContext contains user claims from JWT
        return ResponseEntity.ok(accountService.getAccount(id));
    }
}
```

**Protected Endpoints in Trade API:**
- `GET /api/v1/accounts/{id}` - Requires valid JWT
- `POST /api/v1/orders` - Requires valid JWT
- `GET /api/v1/orders` - Requires valid JWT
- All routes automatically verify: signature, expiration, issuer

---

##  Integration Testing

### Test Scenario: Complete Authentication Flow

**1. Register a User**

**Bruno Image Name:** `auth-register-request.png`

```bash
curl -X POST http://localhost:3000/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "TestPass123",
    "accountId": 1,
    "roles": ["CUSTOMER"]
  }'
```

**Expected Response (201 Created):**
```json
{
  "id": "...",
  "username": "testuser",
  "accountId": 1,
  "roles": ["CUSTOMER"],
  "createdOn": "..."
}
```

---

**2. Login & Obtain Token**

**Bruno Image Name:** `auth-login-request.png`

```bash
curl -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "TestPass123"
  }'
```

**Expected Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

---

**3. Call Protected Trade API Route WITH Valid Token**

**Bruno Image Name:** `trade-api-protected-route-with-token.png`

```bash
# Use the accessToken from login response
curl -X GET http://localhost:8081/api/v1/accounts/1 \
  -H "Authorization: Bearer <accessToken from login>"
```

**Expected Response (200 OK):**
```json
{
  "accountId": 1,
  "accountRef": "ACC0000001",
  "status": "ACTIVE",
  "clientId": 42,
  "balance": 100000.00
}
```

---

**4. Call Protected Trade API Route WITHOUT Token**

**Bruno Image Name:** `trade-api-no-token-request.png`

```bash
curl -X GET http://localhost:8081/api/v1/accounts/1
```

**Expected Response (401 Unauthorized):**
```json
{
  "statusCode": 401,
  "message": "Unauthorized: Missing or invalid JWT",
  "errorCode": "AUTH-401"
}
```

---

**5. Call Protected Trade API Route WITH Wrong Key Token**

**Bruno Image Name:** `trade-api-wrong-signature-request.png`

This test requires simulating a token signed with a different JWT_SECRET. Tools like jwt.io can generate test tokens.

```bash
# Simulate: Create token with SECRET_KEY="wrong-key-123456789"
# Then call Trade API with that token:
curl -X GET http://localhost:8081/api/v1/accounts/1 \
  -H "Authorization: Bearer <token-signed-with-wrong-key>"
```

**Expected Response (401 Unauthorized):**
```json
{
  "statusCode": 401,
  "message": "Invalid token signature",
  "errorCode": "AUTH-401"
}
```

---

**6. Get Current User Info (JWT Protected)**

**Bruno Image Name:** `auth-me-request.png`

```bash
curl -X GET http://localhost:3000/auth/me \
  -H "Authorization: Bearer <accessToken>"
```

**Expected Response (200 OK):**
```json
{
  "id": "...",
  "username": "testuser",
  "accountId": 1,
  "roles": ["CUSTOMER"],
  "createdOn": "..."
}
```

---

**7. Refresh Token**

**Bruno Image Name:** `auth-refresh-request.png`

```bash
curl -X POST http://localhost:3000/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<refreshToken from login>"
  }'
```

**Expected Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

---

## Development

### Build
```bash
npm run build
```

### Start Development Server
```bash
npm run start:dev
```

### Debug Mode
```bash
npm run start:debug
```

### Linting
```bash
npm run lint
npm run lint:fix
```