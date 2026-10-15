---
name: security-guardrails
description: "Use when implementing authentication, authorization, API calls, token handling, or any security-sensitive feature in sprint-09-trading-ui. Enforces bearer token security, origin validation, error handling, and prevents common vulnerabilities like token leakage, open redirects, and unvalidated API calls."
applyTo: "sprint-09-trading-ui/**"
---

# Security Guardrails for Trading UI

## Critical Security Context

You are building a **trading platform UI** that handles:
- **Live customer data**: Account balances, positions, personal information
- **Bearer tokens**: Session credentials that grant full trading authority
- **Financial transactions**: Orders with real money impact
- **Sensitive logs**: Your token will appear in third-party access logs if leaked

A security incident here is **reportable with a named customer attached**, not a bug to fix next sprint. Security is **not a sprint 10 feature**. It is a requirement of every line of code you write in Sprint 9.

## Guardrail 1: Bearer Token Security (CRITICAL)

### Rule: Tokens Are Credentials, Not Data

A bearer token is equivalent to a customer's username and password. Whoever holds it can trade on their account. If a token escapes:

- **Third-party access logs**: Analytics, error trackers, CDNs store the token and customer identity
- **Browser DevTools**: Visible in Network tab to anyone with dev-tools access
- **localStorage vulnerabilities**: XSS attacks can exfiltrate tokens
- **Log aggregation**: Internal logging systems may capture headers
- **Error reports**: Unredacted API failures leak tokens

### Allowed Actions
✅ Store token in memory or Session Storage (expires on page close)
✅ Attach token to your platform APIs only (Trade API, Auth Service)
✅ Send token over HTTPS only (enforced via environment config)
✅ Refresh token silently before expiry (using refresh token)
✅ Clear token immediately on logout

### Forbidden Actions
❌ Store token in localStorage (persistent, vulnerable to XSS)
❌ Attach token to third-party URLs (Fauxnance API, analytics, error trackers)
❌ Log token in browser console, error messages, or HTTP logs
❌ Pass token in query parameters (visible in browser history and referer headers)
❌ Set Authorization header in `fetch()` or XMLHttpRequest (not intercepted, easy to miss)
❌ Attach token to CDN or static asset requests

### Implementation Pattern

```typescript
// ✅ CORRECT: Token in memory, attached by interceptor, cleared on logout

@Injectable({ providedIn: 'root' })
export class AuthService {
  private tokenSignal = signal<string | null>(null);
  
  getToken(): string | null {
    return this.tokenSignal();
  }
  
  setToken(token: string): void {
    this.tokenSignal.set(token);
    // DO NOT store in localStorage
  }
  
  logout(): void {
    this.tokenSignal.set(null);
    // Token is immediately unavailable
  }
  
  silentRefresh(): Observable<void> {
    // Refresh before expiry to avoid user-facing timeout
    return this.http.post<AuthResponse>('/auth/refresh', { refreshToken: this.refreshToken })
      .pipe(
        tap(response => this.setToken(response.token)),
        catchError(() => {
          this.logout();
          return throwError(() => new Error('Session expired'));
        })
      );
  }
}

// Interceptor: controlled token attachment
export function authInterceptor(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
  const allowedOrigins = environment.apiOrigins;
  const isOwnOrigin = allowedOrigins.some(origin => req.url.startsWith(origin));
  
  if (isOwnOrigin) {
    const token = inject(AuthService).getToken();
    if (token) {
      req = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` }
      });
    }
  }
  
  return next.handle(req);
}
```

## Guardrail 2: Origin Validation (CRITICAL)

### Rule: All Outbound Requests Go Through HttpClient

Do not use `fetch()`, `XMLHttpRequest`, or `new Image()` to make requests. These bypass interceptors. If you bypass the interceptor, you may accidentally attach tokens to third-party origins.

### Allowed Pattern (Interceptor Controlled)

```typescript
// ✅ CORRECT: HttpClient, intercepted, origin checked

constructor(private http: HttpClient) {}

getOrders() {
  // HttpClient will go through authInterceptor
  // Interceptor checks origin, attaches token only to own APIs
  return this.http.get<Order[]>('/api/v1/orders');
}

// Call a third-party API (no token attached)
getExternalData() {
  // Interceptor sees URL is not in allowedOrigins, does NOT attach token
  return this.http.get('https://third-party-data.com/data');
}
```

### Forbidden Patterns

```typescript
// ❌ WRONG: fetch bypasses interceptor, may attach token via header

async getOrders() {
  const token = this.authService.getToken();
  // If you forgot to check the origin, token goes to third party!
  const response = await fetch('https://analytics.thirdparty.com/logs', {
    headers: { Authorization: `Bearer ${token}` }
  });
}

// ❌ WRONG: XMLHttpRequest, not intercepted

const xhr = new XMLHttpRequest();
xhr.setRequestHeader('Authorization', `Bearer ${this.token}`);
xhr.open('GET', someUrl);
xhr.send(); // Which origin is someUrl? Did you check?

// ❌ WRONG: Image for tracking pixel, token leaks in referer

new Image().src = `https://tracker.com?token=${this.token}`;
```

### Third-Party API Example (Correct)

If you call Fauxnance API or any third party:

```typescript
// ✅ CORRECT: No token attachment, because not on allowedOrigins

getFauxnanceQuote(symbol: string): Observable<Quote> {
  // Interceptor will NOT attach token (https://api.fauxnance.com not in allowedOrigins)
  return this.http.get<Quote>(`https://api.fauxnance.com/quote/${symbol}`);
}
```

## Guardrail 3: Open Redirect Prevention (CRITICAL)

### Rule: Return URL Must Be on This Origin

When a user logs in, they get redirected to `/sign-in?returnUrl=/dashboard`. An attacker can send them to `/sign-in?returnUrl=https://evil.com` and after login, the user is redirected to a phishing site. **You cannot let that happen.**

### Implementation Pattern

```typescript
// ✅ CORRECT: Validate return URL is a path on this origin

export const authGuard: CanActivateFn = (route, state): boolean | UrlTree => {
  const authService = inject(AuthService);
  const router = inject(Router);
  
  if (authService.isAuthenticated()) {
    return true;
  }
  
  // Extract return URL from state.url
  const url = new URL(state.url, window.location.origin);
  const returnUrl = url.pathname + url.search;
  
  // Validate: must start with "/" and not be "//"
  if (returnUrl.startsWith('/') && !returnUrl.startsWith('//')) {
    return router.createUrlTree(['/sign-in'], { 
      queryParams: { returnUrl } 
    });
  }
  
  // Reject, redirect to sign-in without return URL
  return router.createUrlTree(['/sign-in']);
};

// In sign-in component, after successful login:
onLoginSuccess(response: LoginResponse) {
  const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
  
  if (returnUrl && returnUrl.startsWith('/') && !returnUrl.startsWith('//')) {
    this.router.navigateByUrl(returnUrl);
  } else {
    this.router.navigate(['/dashboard']);
  }
}
```

### Forbidden Pattern

```typescript
// ❌ WRONG: No validation, user redirected to attacker's site

const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
this.router.navigateByUrl(returnUrl); // What if returnUrl is 'https://evil.com'?

// ❌ WRONG: Deny-list of bad origins (always fails open)

if (!returnUrl.includes('evil.com')) {
  this.router.navigateByUrl(returnUrl); // What about the 1000 other evil sites?
}
```

## Guardrail 4: Error Handling & Logging (CRITICAL)

### Rule: Never Log Tokens or Sensitive Data

Logs appear in browser DevTools, server logs, error trackers, and log aggregation systems. Any token in a log is a leak.

### Implementation Pattern

```typescript
// ✅ CORRECT: Error message without token

private handleApiError(error: HttpErrorResponse): Observable<never> {
  let userMessage = 'An error occurred';
  
  if (error.status === 401) {
    userMessage = 'Your session has expired. Please sign in again.';
    this.authService.logout();
  } else if (error.status === 403) {
    userMessage = 'You do not have permission to perform this action.';
  } else if (error.status === 400) {
    userMessage = error.error?.message || 'Invalid request.';
  }
  
  // Log error details for debugging, NOT the token or customer data
  console.error('[API Error]', error.status, error.statusText);
  
  return throwError(() => new Error(userMessage));
}

// In interceptor, log safely

export function authInterceptor(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
  const allowedOrigins = environment.apiOrigins;
  const isOwnOrigin = allowedOrigins.some(origin => req.url.startsWith(origin));
  
  if (isOwnOrigin) {
    const token = inject(AuthService).getToken();
    if (token) {
      req = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` }
      });
      // Log that we attached a token, NOT the token itself
      console.debug(`[Auth] Token attached to ${req.url}`);
    }
  }
  
  return next.handle(req);
}
```

### Forbidden Pattern

```typescript
// ❌ WRONG: Token logged in console

console.log('Token:', authService.getToken());
console.log('Request headers:', req.headers); // Headers include Authorization!

// ❌ WRONG: Token in error message

throw new Error(`Failed to get orders with token ${token}: ${error}`);

// ❌ WRONG: Sensitive data in query params

this.http.get(`/api/v1/customer?id=${customerId}&token=${token}`);

// ❌ WRONG: Customer data rendered unredacted

<div>Order placed for {{ order.customerId }} at {{ order.timestamp }}</div>
```

## Guardrail 5: Form Validation (Client-Side Mitigation Only)

### Rule: Validation is UX, Not Security

The server is the authority on what data is valid. Client-side validation exists only to catch obvious user mistakes before the wire. **Business rules live in the API. Do not re-implement them in the UI expecting the UI to enforce them.**

### Implementation Pattern

```typescript
// ✅ CORRECT: Validate before submit, but rely on server for enforcement

orderForm = this.fb.group({
  symbol: ['', [Validators.required, Validators.pattern(/^[A-Z]{1,10}$/)]],
  quantity: ['', [Validators.required, Validators.min(1), Validators.pattern(/^\d+$/)]],
  price: ['', [Validators.required, Validators.min(0.01)]],
  account: [{ value: '', disabled: true }, Validators.required],
  side: ['BUY', Validators.required]
});

onSubmit() {
  if (this.orderForm.invalid) {
    // Show error to user, don't submit
    return;
  }
  
  // Submit to API
  this.tradeClient.placeOrder(this.orderForm.value).subscribe({
    next: () => this.showSuccess('Order submitted'),
    error: (err) => this.showError(err.message) // API's error message
  });
}

// API response contains business rule violations
// Handle them gracefully, don't assume validation passed
```

### Forbidden Pattern

```typescript
// ❌ WRONG: Assuming account field is secure because it's read-only in UI

<input formControlName="account" [value]="currentAccount()" disabled />

// WRONG: An attacker can set any account_id in the request body by modifying the request.
// The API MUST re-verify that the authenticated user owns the account.
```

## Guardrail 6: Environment Configuration (CRITICAL)

### Rule: All Infrastructure URLs Come From Configuration

Hardcoded URLs are impossible to change between dev/staging/production. Hardcoded credentials are leaked to source control. Use environment files.

### Implementation Pattern

```typescript
// ✅ CORRECT: environment.ts for dev, environment.prod.ts for production

// src/environments/environment.ts
export const environment = {
  production: false,
  apiOrigins: ['http://localhost:3000', 'http://localhost:3001'],
  authServiceUrl: 'http://localhost:3001',
  tradeApiUrl: 'http://localhost:3000'
};

// src/environments/environment.prod.ts
export const environment = {
  production: true,
  apiOrigins: ['https://api.platform.com', 'https://auth.platform.com'],
  authServiceUrl: 'https://auth.platform.com',
  tradeApiUrl: 'https://api.platform.com'
};

// .env.example (template, committed, no real secrets)
ANGULAR_AUTH_URL=http://localhost:3001
ANGULAR_TRADE_API_URL=http://localhost:3000

// .env (secrets, never committed, copied from .env.example)
ANGULAR_AUTH_URL=https://auth.production.com
ANGULAR_TRADE_API_URL=https://api.production.com
```

### Forbidden Pattern

```typescript
// ❌ WRONG: Hardcoded URLs

const AUTH_URL = 'https://auth.production.com'; // Leaked to source control
const API_KEY = 'sk-1234567890'; // Now everyone has your API key

// ❌ WRONG: URLs in code changes between dev and prod

if (environment.production) {
  apiUrl = 'https://api.production.com';
} else {
  apiUrl = 'http://localhost:3000';
}
// This pattern works but environment.prod.ts is cleaner
```

## Guardrail 7: Token Refresh & Expiry (CRITICAL)

### Rule: Refresh Before Expiry, Handle Expiry Gracefully

JWT tokens expire. Refresh tokens prevent user disruption. If a token expires mid-request, handle it gracefully (logout, show message, don't render empty screen).

### Implementation Pattern

```typescript
// ✅ CORRECT: Refresh before expiry, handle expiry on failed request

@Injectable({ providedIn: 'root' })
export class AuthService {
  private tokenSignal = signal<string | null>(null);
  private refreshTokenSignal = signal<string | null>(null);
  private expiresAt = signal<number | null>(null);
  
  setToken(token: string, refreshToken: string, expiresIn: number): void {
    this.tokenSignal.set(token);
    this.refreshTokenSignal.set(refreshToken);
    // Set expiry time (now + expiresIn), refresh 5 minutes before
    this.expiresAt.set(Date.now() + (expiresIn * 1000) - (5 * 60 * 1000));
  }
  
  silentRefresh(): Observable<void> {
    const refreshToken = this.refreshTokenSignal();
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token'));
    }
    
    return this.http.post<AuthResponse>('/auth/refresh', { refreshToken })
      .pipe(
        tap(response => this.setToken(response.token, response.refreshToken, response.expiresIn)),
        catchError(() => {
          this.logout();
          return throwError(() => new Error('Session expired'));
        })
      );
  }
  
  ensureTokenValid(): Observable<void> {
    const expiresAt = this.expiresAt();
    if (expiresAt && Date.now() >= expiresAt) {
      return this.silentRefresh();
    }
    return of(void 0);
  }
  
  logout(): void {
    this.tokenSignal.set(null);
    this.refreshTokenSignal.set(null);
    this.expiresAt.set(null);
  }
}

// In guard or startup
export const appInitializer = (authService: AuthService): () => Promise<void> => {
  return () => authService.ensureTokenValid().toPromise().then(() => {});
};

// In bootstrapApplication
bootstrapApplication(AppComponent, {
  providers: [
    APP_INITIALIZER: {
      useFactory: appInitializer,
      deps: [AuthService],
      multi: true
    }
  ]
});
```

### Forbidden Pattern

```typescript
// ❌ WRONG: Token stored in localStorage without expiry tracking

localStorage.setItem('token', token);

// ❌ WRONG: Token expiry ignored, user gets 401 mid-request, page goes blank

getOrders() {
  return this.http.get<Order[]>('/api/v1/orders');
  // If token is expired, API returns 401
  // If error is not handled, user sees empty page
}

// ✅ CORRECT: Handle 401, trigger logout

getOrders() {
  return this.http.get<Order[]>('/api/v1/orders')
    .pipe(
      catchError(err => {
        if (err.status === 401) {
          this.authService.logout();
          this.router.navigate(['/sign-in']);
          return throwError(() => new Error('Session expired'));
        }
        return throwError(() => err);
      })
    );
}
```

## Guardrail 8: Interceptor Registration (CRITICAL)

### Rule: Interceptor Registered Once, Never in Multiple Places

If you set Authorization headers in multiple services, you **will forget one**. The day you forget is the day a call goes out unauthenticated or with the wrong header.

### Implementation Pattern

```typescript
// ✅ CORRECT: Interceptor in one place

// src/app/auth.interceptor.ts
export function authInterceptor(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
  const allowedOrigins = environment.apiOrigins;
  const isOwnOrigin = allowedOrigins.some(origin => req.url.startsWith(origin));
  
  if (isOwnOrigin) {
    const token = inject(AuthService).getToken();
    if (token) {
      req = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` }
      });
    }
  }
  
  return next.handle(req);
}

// src/main.ts
bootstrapApplication(AppComponent, {
  providers: [
    provideHttpClient(withInterceptors([authInterceptor]))
  ]
});

// Every service uses HttpClient, interceptor is applied to every request
@Injectable()
export class OrderService {
  constructor(private http: HttpClient) {}
  
  getOrders() {
    // Interceptor automatically attaches token (if own origin)
    return this.http.get<Order[]>('/api/v1/orders');
  }
}
```

### Forbidden Pattern

```typescript
// ❌ WRONG: Interceptor-like logic in multiple services

@Injectable()
export class OrderService {
  constructor(private http: HttpClient, private auth: AuthService) {}
  
  getOrders() {
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${this.auth.getToken()}`
    });
    return this.http.get<Order[]>('/api/v1/orders', { headers });
  }
}

@Injectable()
export class PositionService {
  constructor(private http: HttpClient, private auth: AuthService) {}
  
  getPositions() {
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${this.auth.getToken()}`
    });
    return this.http.get<Position[]>('/api/v1/positions', { headers });
  }
}

// NEW: Dashboard service, developer forgets header
@Injectable()
export class DashboardService {
  constructor(private http: HttpClient) {}
  
  getDashboard() {
    // MISTAKE: No Authorization header, call goes out unauthenticated
    return this.http.get('/api/v1/dashboard');
  }
}
```

## Security Review Checklist

Before code review, verify:

- [ ] Tokens stored in memory only (not localStorage)
- [ ] Bearer token attached only by single interceptor
- [ ] Interceptor uses allow-list for origin checking (not deny-list)
- [ ] No token in console logs, error messages, or URL parameters
- [ ] All routes except `/sign-in` have `canActivate: [authGuard]`
- [ ] Return URL validated: must start with `/`, not `//`
- [ ] Form validation present (UX only, API is authority)
- [ ] All infrastructure URLs from `environment.ts`
- [ ] `.env` in `.gitignore`, `.env.example` committed
- [ ] Interceptor tests: one for attach, one for do-not-attach
- [ ] No `fetch()` or `XMLHttpRequest` used (HttpClient only)
- [ ] 401 errors handled: logout, show message, don't render blank page
- [ ] Token refresh logic in place before expiry
- [ ] Generated clients committed and regenerated on contract changes
- [ ] No hand-written HTTP clients

