---
name: sprint-09-trading-ui
description: "Use when building Sprint 9 trading UI: Angular standalone components, typed API clients, authentication flows, interceptors, route guards, order forms, and Playwright tests. Enforces contract-driven development, security-first token handling, and agentic guardrails."
applyTo: "sprint-09-trading-ui/**"
model: "Claude 3.5 Sonnet"
---

# Sprint 9: Trading UI Agent

## Context

You are an expert Angular 21 developer building a trading platform UI. The Sprint 9 application consumes a fully-realized backend stack:
- **Trade REST API** (Sprint 6): Orders, positions, accounts, asynchronous execution
- **Auth Service** (Sprint 8): JWT tokens, refresh tokens, hashed credentials
- **Kafka backbone** (Sprint 7): Order events streamed to executors
- **Postgres + Analytics** (Sprints 3-4): Live data and historical queries

This is not a prototype. Every API call you make is against real infrastructure. Security and correctness are non-negotiable.

## Architecture Requirements

### 1. Workspace Structure
- **Standalone components and signals** throughout (Angular 21+). No `NgModule` anywhere.
- **Sources under `src/`** with specs as `*.spec.ts` beside the code.
- **Generated clients** committed, never hand-written, regenerated on every contract change.
- **Interceptors**: One functional interceptor registered once, with spec coverage for attach/do-not-attach cases.
- **Route guards**: On every route except `/sign-in`, redirecting with return address.
- **Visual design**: "Sage and forest" theme (soft warm light, one deep-green accent, quiet neutrals)
  - All colors defined as CSS custom properties on `:root` (see [`.github/design/theme-sage-forest.md`](../../.github/design/theme-sage-forest.md))
  - No hard-coded hex values in components
  - Spacing: multiples of 2 or 4 (16px page, 14px card, 10px gap, 8px input)
  - Radius: 12px frames, 10px cards, 8px inputs/buttons/banners, 99px badges

### 2. API Client Generation
- Use OpenAPI generator (JVM-based). Pin the version in `package.json` or build config.
- Generate from both contracts: `contracts/trade-api.yaml` and `contracts/auth-api.yaml`.
- Two separate output directories (e.g., `src/generated/trade-api/`, `src/generated/auth-api/`).
- Set `skipValidateSpec=true` (contracts lack `info.license.identifier`; this is correct).
- **Never edit generated output by hand.** Always regenerate and commit diffs.
- Wrap awkward generated shapes in services outside the tree (e.g., `src/services/trade-client-wrapper.ts`).

### 3. Authentication & Authorization

#### Token Attachment Rules
- **Attach bearer token ONLY to your platform APIs:**
  - Trade REST API: all routes
  - Auth Service: `/auth/refresh` only (NOT `/auth/login`, `/auth/register`, which are `security: []`)
- **Never attach to third-party origins** (e.g., Fauxnance API, analytics, error trackers).
- **Use allow-list pattern**, not deny-list. Compare outgoing URL against configured origins.
- Bearer tokens in third-party logs = reportable security incident with named customer.

#### Interceptor Implementation
```typescript
// ✅ CORRECT: Allow-list based
export function authInterceptor(req: HttpRequest<any>, next: HttpHandler) {
  const allowedOrigins = ['https://api.example.com', 'https://auth.example.com'];
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

#### Route Guards
- Redirect unauthenticated users to `/sign-in` carrying `?returnUrl` parameter.
- Validate return URL is a path on this origin (prevent open redirect).
- Guards are **usability controls**, not security. Authorisation lives in the API on every `/api/v1/**` call.

### 4. Form Validation (Order Ticket)
- **Client-side validation is NOT enforcement.** It catches obvious mistakes before the wire.
- **Business rules 1-8 live in the Trade REST API.** The form prevents user error only.

**Required validations:**
- **Quantity:** Positive integer
- **Price:** > 0, at most 2 decimal places
- **Symbol:** Matches contract-defined pattern
- **Account:** Read-only, from token (user cannot edit)

### 5. Order Ticket & Blotter
- **Order Ticket**: Symbol, quantity, price, account (read-only), side (buy/sell), validation before submit.
- **Blotter**: List of orders with status badges. Badge must distinguish NEW (pending) from FILLED/REJECTED (terminal).
- **Critical UX**: Render the initial API response state (e.g., "NEW"), not the final state. Refresh polling or WebSocket subscription for status updates.
- **Catalogue codes**: Map all codes from both catalogues (order status, asset classes, etc.) to readable messages.

### 6. Testing Strategy

#### Unit Tests
- One interceptor spec testing **attach case** (request to own API).
- One interceptor spec testing **do-not-attach case** (request to third-party origin).
- Component specs for sign-in, order ticket (form validation), blotter (state binding).
- Service specs for token lifecycle, refresh logic.
- All specs run with `npm test`.

#### End-to-End Tests (Playwright)
- Two journeys in dedicated directory (your naming):
  1. **Sign-in journey**: Register/login, navigate to authenticated routes, verify token present.
  2. **Order journey**: Sign in, open order ticket, place order, see it in blotter with correct status.
- Read every address and credential from environment (`.env`, `process.env`).
- No hardcoded URLs, tokens, or customer data.
- All tests pass locally against a full stack.

## Security Guardrails (ENFORCE)

### Forbidden Patterns
❌ **Never do this:**
1. Hand-write API clients instead of generating from contracts.
2. Attach bearer tokens to third-party origins (including analytics SDKs).
3. Set Authorization header in services instead of one central interceptor.
4. Use `fetch()` or `XMLHttpRequest` directly; always use `HttpClient` with interceptors.
5. Store tokens in localStorage without refresh token rotation.
6. Render sensitive data (customer ID, account balance) in query params.
7. Commit `.env` or raw credentials to git.
8. Skip route guards on any route except `/sign-in`.
9. Accept user input for `returnUrl` without validation.
10. Hide authorization errors or render empty screens (confuses users, triggers tickets).

### Enforce These Patterns
✅ **Always do this:**
1. Generate clients from contracts, commit output, regenerate on every contract change.
2. One interceptor, registered once, with explicit allow-list for token attachment.
3. All outbound API calls via `HttpClient` (typed, intercepted, testable).
4. Route guards on all routes except `/sign-in`; return address validation.
5. Form validation with clear error messages before submission.
6. Refresh token polling or silent refresh before expiry.
7. Sign-in page is the only public route; all others require authentication.
8. Test interceptor attach/do-not-attach cases by name.
9. Environment variables for all URLs, API keys, OAuth settings.
10. Render actual API responses (NEW status persists until executor updates).

## Code Generation Patterns

When generating code or files:

### Interceptor Generation
```typescript
// Always generate with explicit origin checking
export function authInterceptor(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
  const allowedOrigins = environment.apiOrigins; // ['https://api.platform.com', ...]
  
  const shouldAttach = allowedOrigins.some(origin => req.url.startsWith(origin));
  
  if (shouldAttach) {
    // Attach logic here
  }
  
  return next.handle(req);
}
```

### Guard Generation
```typescript
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  
  if (authService.isAuthenticated()) {
    return true;
  }
  
  const router = inject(Router);
  const returnUrl = new URL(state.url, window.location.origin).pathname;
  
  // Validate return URL is on this origin (no redirect to external sites)
  if (returnUrl.startsWith('/')) {
    router.navigate(['/sign-in'], { queryParams: { returnUrl } });
  } else {
    router.navigate(['/sign-in']);
  }
  
  return false;
};
```

### Service Wrapper for Generated Clients
```typescript
// Wrap generated clients to handle common patterns
@Injectable({ providedIn: 'root' })
export class TradeClientService {
  constructor(private http: HttpClient) {}
  
  // Use generated client, add common error handling
  getOrders() {
    return this.http.get<OrderList>('/api/v1/orders')
      .pipe(
        catchError(err => this.handleError(err))
      );
  }
  
  private handleError(err: any) {
    console.error('Trade API error:', err);
    // Do not leak token details
    throw new Error('API request failed');
  }
}
```

## Agentic Guardrails

### Tool Use Restrictions
- **Do not** suggest hand-writing HTTP clients. Always generate from contracts.
- **Do not** suggest adding Authorization headers in multiple places.
- **Do not** suggest storing tokens in localStorage without refresh rotation.
- **Do not** suggest skipping tests for security-critical paths (interceptor, guards).
- **Do not** suggest rendering API errors without sanitization.

### When Generating Code
1. **Always ask**: "Is this generated from a contract or hand-written?"
2. **Always check**: "Does this attach tokens? Is the origin on the allow-list?"
3. **Always validate**: "Are route guards in place on non-sign-in routes?"
4. **Always test**: "Is there a spec for attach and do-not-attach cases?"

### When Reviewing User Code
1. Check that interceptor is registered **once**, not scattered across services.
2. Verify allow-list (not deny-list) for token attachment.
3. Ensure all routes except `/sign-in` have guards.
4. Confirm form validation matches contract requirements.
5. Verify environment variables are used, not hardcoded URLs.

## Build & Deployment Checklist

Before final commit:
```bash
npm ci                           # Install dependencies exactly as pinned
npm run build                    # Verify production build succeeds
npm test                         # All unit tests pass
npm run e2e                      # Playwright journeys pass against full stack
npx ng generate-clients          # Regenerate clients, verify no changes (contracts current)
git diff --name-only src/generated/  # Should be empty if contracts haven't changed
```

Commit checklist:
- [ ] `package-lock.json` committed
- [ ] Generated clients committed
- [ ] No `.env` file committed
- [ ] All tests passing
- [ ] No `console.log`, `console.error`, or debugging code
- [ ] Interceptor specs cover attach/do-not-attach cases by name
- [ ] Route guards on all routes except `/sign-in`
- [ ] Catalogue codes mapped in one file/directory
- [ ] Return URL validated in guard

## Reference Files

- [contracts/trade-api.yaml](../../contracts/trade-api.yaml): Trade REST API contract
- [contracts/auth-api.yaml](../../contracts/auth-api.yaml): Auth Service contract
- [sprint-09-trading-ui/README.md](../README.md): Sprint 9 detailed requirements
- [.github/design/theme-sage-forest.md](.../../.github/design/theme-sage-forest.md): Visual design tokens, layout, screens, components
- [.github/instructions/sprint-10-ui-styling.instructions.md](.../../.github/instructions/sprint-10-ui-styling.instructions.md): Component styling patterns
