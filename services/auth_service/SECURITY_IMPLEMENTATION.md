# Login Security Implementation: What It Means

## Executive Summary

This implementation closes **three critical security vulnerabilities** in the login endpoint:

1. **User Enumeration via Timing** - Attackers cannot use response time to determine if a username exists
2. **Brute-Force Attack** - Attackers cannot rapidly guess passwords  
3. **Inconsistent Error Messages** - Both failure cases return identical responses

---

## The Problem: Why This Matters

### Original Vulnerability #1: Timing-Based User Enumeration

**The Attack:**
An attacker with a list of 1,000 potential usernames can determine which ones exist in your system **without knowing any passwords**.

```
Unknown user "alice":
  POST /auth/login → Response in 2ms (database lookup only)

Valid user "bob" with wrong password:
  POST /auth/login → Response in 105ms (hash verification takes 100ms)

Attacker's observation:
  "If it takes 100ms+, the username exists. If it's 2ms, it doesn't."
  
Result: Customer database enumerated in seconds using a stopwatch.
```

**Why it happens:**
- Database lookup is fast (~1ms)
- Password hashing (argon2) is intentionally slow (~100ms) to prevent brute-force
- The difference creates a timing oracle

### Original Vulnerability #2: Weak Brute-Force Protection

**The Attack:**
An attacker can try 1,000 password guesses in 100 seconds (10/second) against a known valid username.

```
for password in wordlist.txt:
  curl -X POST /auth/login -d {"username": "alice", "password": password}
  
Result: Account compromised in minutes using automated tools
```

**Why it happens:**
- No rate limiting exists
- Fast requests can be issued in succession
- Attacker can use distributed bot network

### Original Vulnerability #3: Different Error Responses

**The Attack (Subtle):**
Even if timing is hidden, error messages or HTTP status codes can leak information.

```
Unknown user:     HTTP 404 "User not found"
Wrong password:   HTTP 401 "Invalid password"

Attacker's observation:
  404 = username doesn't exist
  401 = username exists but password is wrong
```

This is exactly the information-leakage we prevent by returning identical responses.

---

## The Solution: How It Works

### Fix #1: Timing-Attack Mitigation via Dummy Password Verification

**The Concept:**
When a username is not found, we still perform password verification work. We just throw away the result.

```typescript
// When username NOT found:
if (!user) {
  // Still verify password against dummy hash (takes ~100ms)
  await argon2.verify(DUMMY_PASSWORD_HASH, password);
  // Result is discarded ↑ (we don't check if it's true/false)
  
  // NOW we throw the same error as wrong password
  throw UnauthorizedException("AUTH-401", "Unauthorised");
}

// When password is wrong:
const valid = await argon2.verify(user.passwordHash, password);
if (!valid) {
  // Same error, but we got here via different path
  throw UnauthorizedException("AUTH-401", "Unauthorised");
}
```

**Why this works:**
- Both paths call `argon2.verify()` with identical cost parameters
- Both paths take ~100ms on standard hardware
- Timing variance is now just hardware/system load noise (±10%), not a signal
- Attacker cannot distinguish unknown user from wrong password by measuring time

**What the attacker measures:**
```
Before:  Unknown user 2ms, Wrong password 105ms → Easy to distinguish
After:   Unknown user 97ms, Wrong password 103ms → Impossible to distinguish reliably
```

### Fix #2: Login Throttle (Rate Limiting)

**The Concept:**
Track failed login attempts per username and block further attempts after 5 failures within 15 minutes.

```typescript
// Configuration
Max attempts:   5
Cooldown:       15 minutes (900 seconds)
Tracking:       Per-username
Scope:          Failed logins only (unknown user + wrong password)

// How it works
Attempt 1: ALLOWED  (4 remaining)
Attempt 2: ALLOWED  (3 remaining)
Attempt 3: ALLOWED  (2 remaining)
Attempt 4: ALLOWED  (1 remaining)
Attempt 5: ALLOWED  (0 remaining)
Attempt 6: BLOCKED  → Return same 401 error
Attempt 7: BLOCKED  → Return same 401 error
...15 minutes pass...
Attempt 21: ALLOWED (oldest attempt expires from 15-min window)
```

**Why these numbers?**
- **5 attempts**: Humans rarely fail 5 times in a row. Legitimate users get retries, attackers get limited tries
- **15 minutes**: Long enough to be effective, short enough that legitimate users aren't permanently locked out
- **Per-username**: Attackers may try many usernames; we limit each one separately

**Important: Maintains Timing Consistency**
```typescript
// Even during throttle, we DON'T short-circuit:
if (!throttle.isAllowed(username)) {
  // WRONG: This would be 1ms response (timing oracle during throttle)
  throw 401;
}

// CORRECT: We still verify password (maintains timing)
if (!throttle.isAllowed(username)) {
  await argon2.verify(DUMMY_PASSWORD_HASH, password); // ~100ms
  throw 401;
}
```

### Fix #3: Unified Error Responses

All these cases now return **identical responses**:

```
401 Unauthorised
```

Covers:
- ✅ Unknown username
- ✅ Wrong password
- ✅ Expired access token
- ✅ Invalid/tampered token (wrong signature)
- ✅ Malformed Authorization header
- ✅ Throttled (too many attempts)

**The benefit:** No information leaked through error codes or messages.

---

## Security Properties Achieved

### Property 1: User Non-Enumeration
**Claim**: Attacker cannot determine which usernames exist by timing login responses

**Evidence**:
- Both success and failure paths perform `argon2.verify()` with same cost
- Measured response time is dominated by argon2 (~100ms) not database lookup (~1ms)
- Timing variance is hardware/load noise, not information

**Limitations**:
- Timing is still affected by system load (loaded server adds latency to both paths equally)
- Timing may differ by 20-50ms between requests due to CPU cache, memory paging, GC pauses
- A consistent 20ms gap is still suspicious—indicates one path isn't doing the work

**How to verify**:
```bash
# Run 100 login attempts with unknown user, measure median time
# Run 100 login attempts with wrong password, measure median time
# Both should be within ±10% of each other (e.g., 95ms vs 105ms is OK, 95ms vs 150ms is NOT OK)
```

### Property 2: Brute-Force Limited
**Claim**: Attacker cannot rapidly guess passwords against a known username

**Evidence**:
- After 5 failures, all further attempts are throttled for 15 minutes
- Each attempt still takes ~100ms (argon2 verification)
- Rate: ~10 attempts per 100 seconds = 6 per minute maximum

**Limitations**:
- Distributed attacks from multiple IP addresses bypass per-username throttle
- A throttle per IP + per username would be better, but requires IP tracking
- Current implementation is best-effort for single-instance deployment

### Property 3: Information Hiding
**Claim**: Error responses don't leak whether username exists or password is wrong

**Evidence**:
- Same HTTP status (401)
- Same error code (AUTH-401)
- Same message ("Unauthorised")
- For all 6 failure types (unknown user, wrong password, expired token, etc.)

---

## Implementation Details

### Argon2 Configuration (Matching Real Passwords)

Both real passwords and dummy passwords use identical parameters:

```typescript
{
  type: argon2.argon2id,  // Memory-hard, resists GPU attacks
  memoryCost: 65536,      // 64 MB
  timeCost: 3,            // 3 iterations
  parallelism: 4          // 4 threads
}
```

**Verification time**: ~100ms on standard 2020s-era CPU (varies by hardware)

**Why these parameters?**
- High memory requirement prevents GPU/ASIC acceleration
- Multiple iterations add CPU work
- Must be expensive enough to deter brute-force but not so expensive that legitimate login feels slow

### Login Throttle Storage

Current implementation:

```typescript
// In-memory Map: { username → [timestamp1, timestamp2, ...] }
private failedAttempts = new Map<string, number[]>();

// Cleanup runs every 60 seconds
// Old entries (>15 minutes old) are removed
```

**For production at scale:**
- Consider Redis-backed throttle for multi-instance deployments
- Track by IP + username for distributed attack resistance
- Consider graduated backoff (1st block = 1 min, 2nd = 5 min, 3rd = 15 min)

---

## What About Edge Cases?

### Q: Does the throttle apply to successful logins?
**A**: No. Only failed attempts count. Successful login doesn't increment the counter and doesn't block future attempts.

### Q: What if an attacker has multiple accounts?
**A**: Throttle is per-username. An attacker targeting username "alice" is limited to 5 attempts per 15 minutes. But they could try username "bob" separately. This is why we need IP-based throttling for distributed defense—current implementation assumes attack comes from single source.

### Q: Can attackers bypass dummy password verification?
**A**: The dummy verification is mandatory. There's no code path that skips it when username is unknown. But sophisticated attackers could:
- Use timing variance + statistical analysis across many requests
- Exploit CPU cache side-channels (very advanced)
- Use shared hosting to measure server resource usage (impractical)

The defense works against simple timing attacks with stopwatch, not expert cryptanalysis.

### Q: What if database lookup takes variable time?
**A**: That's why we don't rely on it. The user lookup might take 1-10ms depending on indexes, cache state, query complexity. The dummy password verification takes ~100ms, completely drowning out that variance. The final response time is dominated by argon2, not the database.

### Q: Is 15 minutes too short? Too long?
**A**: 15 minutes is a reasonable balance:
- **Too short (<5 min)**: Legitimate users with poor typing skills get frustrated
- **Too long (>30 min)**: Attacker can make 5 attempts every 30 minutes = 8 attempts per hour
- **15 minutes**: Humans don't repeatedly fail login 5 times in a row naturally; if they do, waiting 15 min is not unreasonable

For distributed attacks, you'd want per-IP throttling on top of this.

---

## Security Guarantees vs. Limitations

### What This Prevents ✅
1. **Timing-based username enumeration** — Attacker can't use stopwatch
2. **Rapid brute-force guessing** — Limited to 5 attempts per 15 minutes per username
3. **Information leakage via error codes/messages** — All failures look identical
4. **Token signature attacks** — Invalid tokens return same error as unknown user
5. **Malformed request exploitation** — Missing/bad auth header returns same 401

### What This Does NOT Prevent ❌
1. **Distributed brute-force from many IPs** — Throttle is per-username, not per-IP
2. **Offline dictionary attacks** — If password database is stolen, hashing (argon2) is the only defense
3. **Compromised database** — An attacker with database access can see all password hashes
4. **Phishing** — If user enters password on fake site, this doesn't help
5. **Compromised client device** — If user's browser/phone is malware-infected, tokens can be stolen
6. **Advanced timing side-channels** — CPU cache analysis or sophisticated statistical analysis could potentially leak information (requires expertise)

---

## Configuration Summary

### Login Throttle
- **Location**: `src/auth/services/login-throttle.service.ts`
- **Max Attempts**: 5
- **Cooldown**: 15 minutes (900 seconds)
- **Tracking**: Per-username
- **Storage**: In-memory Map (production: migrate to Redis)

### Timing-Attack Mitigation
- **Location**: `src/auth/auth.service.ts`
- **Mechanism**: Dummy password verification for unknown users
- **Dummy Hash**: Pre-computed argon2id hash (same cost parameters as real passwords)
- **Cost**: ~100ms per login attempt (intentional for security)

### Error Responses
- **Status**: 401 Unauthorized
- **Body**: `{"errorCode": "AUTH-401", "message": "Unauthorised"}`
- **Applies to**: Unknown user, wrong password, expired token, invalid signature, malformed header, throttled

### Logging
- All failures logged with `[AUTH_EVENT]` prefix
- Includes: `login_attempt_started`, `login_throttled`, `login_rejected_user_not_found`, `login_rejected_invalid_password`
- Passwords/tokens never logged

---


