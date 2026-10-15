# GitHub Folder: Agentic AI & Customization

This folder contains VS Code agent customization and prompt engineering files for the Trading Platform capstone project. These files guide Copilot to generate better, more secure, and more consistent code across the team.

## Quick Start

1. **You're in Sprint 9 (Trading UI)?** → Files in this folder automatically apply
2. **Open a file** in `sprint-09-trading-ui/src/` → Copilot loads the agent & instructions
3. **Start coding** → Copilot suggests secure, tested patterns automatically
4. **Type `/` in chat** → See available prompts like `/generate-client`

## Folder Structure

```
.github/
├── README.md                           # This file
├── AGENTS.md                          # Overview of all agents & how to use them
├── agents/
│   └── sprint-09-trading-ui.agent.md  # Primary Sprint 9 agent (all architecture rules)
├── instructions/
│   ├── angular-trading-ui.instructions.md      # Angular 21 patterns
│   └── security-guardrails.instructions.md     # Token, origin, redirect, logging security
├── hooks/
│   └── sprint-09-security.json                # Automated security checks on file edit
└── prompts/
    └── generate-client.prompt.md       # Generate typed API clients from contracts
```

## Files at a Glance

### Agent (Primary Authority)

| File | Scope | Purpose | When Used |
|------|-------|---------|-----------|
| `agents/sprint-09-trading-ui.agent.md` | `sprint-09-trading-ui/**` | Architecture, security, testing | On any Sprint 9 file |

## Files at a Glance

### Agent (Primary Authority)

| File | Scope | Purpose | When Used |
|------|-------|---------|-----------|
| `agents/sprint-09-trading-ui.agent.md` | `sprint-09-trading-ui/**` | Architecture, security, testing, visual design | On any Sprint 9 file |

### Instructions (Applied Automatically)

| File | Scope | Purpose | When Used |
|------|-------|---------|-----------|
| `instructions/angular-trading-ui.instructions.md` | `sprint-09-trading-ui/src/**/*.ts` | Component, service, guard, interceptor, form, test patterns | On any Angular file |
| `instructions/security-guardrails.instructions.md` | `sprint-09-trading-ui/**` | Token security, origin validation, redirect prevention, logging | On security-sensitive code |
| `instructions/sprint-10-ui-styling.instructions.md` | `sprint-09-trading-ui/src/**/*.scss\|*.css` | CSS patterns, theme tokens, component styling, spacing, accessibility | On CSS/SCSS files |

### Hooks (Run on Edit)

| File | Trigger | Purpose | Action |
|------|---------|---------|--------|
| `hooks/sprint-09-security.json` | PostToolUse on file edit | Detect token leaks, header inconsistencies, missing guards | Warn or error |

### Prompts (Slash Commands)

| Command | Purpose | When Used |
|---------|---------|-----------|
| `/generate-client` | Generate typed API clients from OpenAPI contracts | Building API client from new contract |

### Design System

| File | Purpose |
|------|---------|
| `design/theme-sage-forest.md` | Complete visual design system: tokens, typography, layout, screens, status badges, accessibility |
| `design/DESIGN.md` | Quick reference and overview for design system |

## Key Security Patterns Enforced

### 1. Bearer Token Security (CRITICAL)
- ✅ Tokens stored in memory only (signal)
- ✅ Attached by single interceptor
- ✅ Allow-list origin checking (never deny-list)
- ❌ Never localStorage, never console.log, never third-party

### 2. Interceptor Centralization
- ✅ One interceptor, registered once
- ✅ Explicit `withInterceptors([authInterceptor])`
- ❌ No Authorization headers in services
- ❌ No fetch/XMLHttpRequest bypassing interceptor

### 3. Route Guards Everywhere
- ✅ Guards on all routes except `/sign-in`
- ✅ Return URL validated (must start with `/`, not `//`)
- ❌ No empty screens on auth failure (redirect + message)

### 4. Generated Clients
- ✅ Committed to git (not gitignored)
- ✅ Regenerated on every contract change
- ❌ Never hand-edited (wrap in services instead)

### 5. Form Validation
- ✅ Client-side validation for UX (prevent obvious mistakes)
- ✅ Server-side business rules (API is authority)
- ❌ Never assume client validation is enforcement

### 6. Error Handling
- ✅ No tokens or sensitive data in logs
- ✅ User-friendly error messages
- ✅ 401 logout & redirect on auth failure

### 7. Environment Config
- ✅ All URLs from `environment.ts`
- ✅ `.env.example` committed (template)

## Key Design Patterns Enforced

### Visual Design (Sage and Forest Theme)
- ✅ All colors defined as CSS custom properties on `:root`
- ✅ Spacing: multiples of 2 or 4 (16px page, 14px card, 10px gap, 8px input)
- ✅ Radius: 12px frames, 10px cards, 8px inputs/buttons/banners, 99px badges
- ✅ Status badges: word + shape marker (not color alone)
- ✅ Typography: 13px dense, 15px headings, 20px figures, tabular-nums on prices
- ✅ Accessibility: visible focus ring, labels, aria attributes, prefers-reduced-motion
- ❌ No hard-coded hex values in components
- ❌ No gradients, no shadows heavier than 1px border
- ❌ No use of color alone to convey status

## Key Security Patterns Enforced

### 1. Bearer Token Security (CRITICAL)
- ✅ Tokens stored in memory only (signal)
- ✅ Attached by single interceptor
- ✅ Allow-list origin checking (never deny-list)
- ❌ Never localStorage, never console.log, never third-party

### 2. Interceptor Centralization
- ✅ One interceptor, registered once
- ✅ Explicit `withInterceptors([authInterceptor])`
- ❌ No Authorization headers in services
- ❌ No fetch/XMLHttpRequest bypassing interceptor

### 3. Route Guards Everywhere
- ✅ Guards on all routes except `/sign-in`
- ✅ Return URL validated (must start with `/`, not `//`)
- ❌ No empty screens on auth failure (redirect + message)

### 4. Generated Clients
- ✅ Committed to git (not gitignored)
- ✅ Regenerated on every contract change
- ❌ Never hand-edited (wrap in services instead)

### 5. Form Validation
- ✅ Client-side validation for UX (prevent obvious mistakes)
- ✅ Server-side business rules (API is authority)
- ❌ Never assume client validation is enforcement

### 6. Error Handling
- ✅ No tokens or sensitive data in logs
- ✅ User-friendly error messages
- ✅ 401 logout & redirect on auth failure

### 7. Environment Config
- ✅ All URLs from `environment.ts`
- ✅ `.env.example` committed (template)
- ✅ `.env` in `.gitignore` (not committed)

### 8. Test Coverage
- ✅ Interceptor: "attach bearer token" & "do-not-attach" tests by name
- ✅ Guards: Redirect & return URL validation
- ✅ Forms: Required fields & validation logic
- ✅ E2E: Playwright journeys for sign-in & order flow
- ✅ `.env` in `.gitignore` (not committed)

### 8. Test Coverage
- ✅ Interceptor: "attach bearer token" & "do-not-attach" tests by name
- ✅ Guards: Redirect & return URL validation
- ✅ Forms: Required fields & validation logic
- ✅ E2E: Playwright journeys for sign-in & order flow

## How Copilot Uses These Files

### Scenario 1: Writing an Interceptor
```
You: Create auth.interceptor.ts
↓
Copilot loads: angular-trading-ui.instructions.md (pattern examples)
↓
Copilot warns: "Use allow-list, not deny-list. Never attach to third-party origins."
↓
Hook detects Authorization header, reminds: "Is this the only place setting headers?"
↓
Result: Correct interceptor following pattern
```

### Scenario 2: Generating an API Client
```
You: Type /generate-client in chat
↓
Copilot loads: generate-client.prompt.md
↓
Copilot asks: "Which contract? contracts/trade-api.yaml?"
↓
Result: Client generated, committed to git, never edited by hand
```

### Scenario 3: Reviewing Code
```
Reviewer: Check sprint-09-trading-ui/src/order-ticket.component.ts
↓
Hooks run: Check for token attachment, return URL validation, route guards
↓
Hooks warn: "Missing guard on this route" or "Token logged in error message"
↓
Result: Issues caught before review, fixed before commit
```

## Common Questions

### Q: Where do I configure the agent for my team?

A: Edit the agent files in this `.github/` folder:
- `agents/sprint-09-trading-ui.agent.md` — Main architecture & security rules
- `instructions/` — Specific patterns for Angular, security, etc.
- `hooks/` — Automated checks
- `prompts/` — Available slash commands

Commit changes to git, they apply immediately to all team members.

### Q: How do I see what agent is loaded?

A: In VS Code, open a file in `sprint-09-trading-ui/src/`, then:
1. Open Copilot chat
2. Look at the top: you'll see which agent is active
3. Read the agent description — it tells you the constraints

### Q: What if the agent suggests something insecure?

A: That means the agent file needs updating. Edit `.github/instructions/security-guardrails.instructions.md` to add a stronger rule.

### Q: How do I add a new prompt?

A: Create a new file in `.github/prompts/`:
```markdown
---
name: my-prompt
description: "Use when ..."
---

# My Custom Prompt

## Parameters
- **param1**: Description

## Example
```

Commit, and it appears as `/my-prompt` in chat.

### Q: Can I override the agent on one file?

A: Yes! Create a file-specific instruction at `.github/instructions/my-file.instructions.md` with a more specific `applyTo` pattern (e.g., `src/my-component.component.ts`). File instructions override agent instructions.

## Building Generation Quality

These files improve generation quality by:

1. **Explicit Architecture** — Agent knows the workspace structure (standalone components, generated clients, single interceptor)
2. **Security Rules** — No guessing about token attachment, origin validation, redirect checks
3. **Testing Patterns** — Agent knows which tests are named and assessed
4. **Consistency** — All team members follow the same patterns
5. **Fast Feedback** — Hooks catch issues on edit, not in review
6. **Reusable Patterns** — Prompts automate repetitive tasks (client generation)

## Security Guardrails Summary

### Tokens (CRITICAL)
- Memory only (signal), never localStorage
- One interceptor, allow-list origins
- Never in logs, console, URLs, or headers of fetch/XHR

### Routes (CRITICAL)
- Guards on all routes except `/sign-in`
- Return URL validated: `/` OK, `//` forbidden
- Render error, don't hide with blank screen

### Clients (CRITICAL)
- Generated from contracts, committed to git
- Regenerated on contract change, never hand-edited
- Wrapped in services for domain logic

### Config (CRITICAL)
- URLs from environment.ts, not hardcoded
- .env in .gitignore, .env.example committed
- No secrets in code

### Tests (CRITICAL)
- Interceptor: attach & do-not-attach by name
- Guards: redirect & return URL
- E2E: sign-in & order journeys
- All run with `npm test` locally

## Related Documentation

- [Sprint 9 Trading UI README](../sprint-09-trading-ui/README.md)
- [Contracts README](../contracts/README.md)
- [Agent Customization Skill](c:\Program Files\Microsoft VS Code\a5b5009513\resources\app\extensions\copilot\assets\prompts\skills\agent-customization\SKILL.md)
- [Angular 21 Docs](https://angular.io/docs)
- [OpenAPI Generator](https://openapi-generator.tech/)

## Contributing

To update these files:
1. Edit the relevant `.agent.md`, `.instructions.md`, `.json`, or `.prompt.md`
2. Test locally (open a matching file, verify Copilot suggestions)
3. Commit to `main`
4. Changes apply immediately to all team members

### Commit Message Example
```
feat: Add open redirect validation guardrail
docs: Update security-guardrails.instructions.md

Reminder: Return URL must start with "/" and not "//"
Hook warns if returnUrl used without validation.
```

---

**Last Updated**: 2026-09-29  
**Sprint**: 9 (Trading UI)  
**Status**: Active

## Sprint 10 additions

| File | Applies to | Covers |
|---|---|---|
| `instructions/sprint-10-ui-styling.instructions.md` | `frontend/src/**/*.css` | Design tokens (light/dark), spacing, motion, mascot styling (replaces the Sprint 9 palette) |
| `instructions/price-alerts-ui.instructions.md` | alerts screens and API | Sentence dialog, list, switch, trigger history, operator wording |
| `instructions/portfolio-redesign.instructions.md` | portfolio, watchlist, downloads | Layout, freshness dots, candle chart, CSV/Excel/PDF export |
| `agents/sprint-10-ui-design.agent.md` | `frontend/**` | Design philosophy, constraints, patterns, test and build commands |
