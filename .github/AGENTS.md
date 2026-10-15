# Sprint 9 Agentic AI Agents

This folder contains VS Code agent customization files that guide Copilot when working on the Trading UI codebase. The agents enforce security, architecture, and testing practices specific to Sprint 9.

## Files & Purpose

### Agents

- **[`.github/agents/sprint-09-trading-ui.agent.md`](./agents/sprint-09-trading-ui.agent.md)**
  - Primary agent for Sprint 9 development
  - Scope: `sprint-09-trading-ui/**`
  - Enforces: Contract-driven development, security-first token handling, agentic guardrails
  - Use when: Building Angular components, services, guards, interceptors, or tests

### Instructions

Instructions are applied automatically to files matching the `applyTo` pattern.

- **[`.github/instructions/angular-trading-ui.instructions.md`](./instructions/angular-trading-ui.instructions.md)**
  - Angular 21 patterns: standalone components, signals, services, interceptors, guards, forms, testing
  - Scope: `sprint-09-trading-ui/src/**/*.ts`
  - Use when: Writing any Angular TypeScript file in Sprint 9

- **[`.github/instructions/security-guardrails.instructions.md`](./instructions/security-guardrails.instructions.md)**
  - Bearer token security, origin validation, open redirect prevention, error handling, environment config
  - Scope: `sprint-09-trading-ui/**` (all files)
  - Use when: Implementing authentication, authorization, API calls, or any security-sensitive feature

- **[`.github/instructions/sprint-10-ui-styling.instructions.md`](./instructions/sprint-10-ui-styling.instructions.md)**
  - CSS patterns, theme tokens, component styling, spacing, radius, typography, accessibility
  - Scope: `sprint-09-trading-ui/src/**/*.scss|*.css`
  - Use when: Styling components or working with CSS/SCSS

### Design System

- **[`.github/design/theme-sage-forest.md`](./design/theme-sage-forest.md)**
  - Complete visual design system: tokens, typography, layout, screens, status badges, accessibility
  - Design principles, spacing scale, radius scale, component specifications
  - Use when: Planning or building any UI screen

- **[`.github/design/DESIGN.md`](./design/DESIGN.md)**
  - Quick reference guide for the design system
  - Token table, spacing scale, radius scale, status badges, rules
  - Use when: Quick lookup on design decisions

### Hooks

- **[`.github/hooks/sprint-09-security.json`](./hooks/sprint-09-security.json)**
  - Automated security checks that run during code editing
  - Detects: Token storage issues, header attachment errors, missing guards, logging violations, .gitignore problems
  - Triggers: After file reads or replacements in Sprint 9 files
  - Action: Warns or errors on security issues, reminds of test coverage

### Prompts

- **[`.github/prompts/generate-client.prompt.md`](./prompts/generate-client.prompt.md)**
  - Generate typed API clients from OpenAPI contracts
  - Use: Type `/generate-client` in chat to invoke
  - Input: Contract path, output directory, language

## How to Use These Files

### For Developers (Daily Use)

1. **When you start working on Sprint 9 files**, Copilot automatically loads the agent and instructions
2. **Type code in the editor** → Instructions provide patterns automatically
3. **Use `/generate-client` prompt** to generate API clients from contracts
4. **Hooks run silently** to detect security issues as you edit

**Example**: You're writing an interceptor:
- Open `src/auth.interceptor.ts`
- Start typing `export function authInterceptor...`
- Copilot reminds you: "Use allow-list, not deny-list. Never attach to third-party origins."
- Hook detects any Authorization header outside the interceptor, warns immediately

### For Code Review

1. **Security guardrails ensure consistency** across the team
2. **Hooks highlight potential issues** before review
3. **Tests are reminded by name** (attach case, do-not-attach case)
4. **No hand-written clients slip through** (generated clients checked)

### For Debugging

If Copilot isn't suggesting the right patterns:
1. Check which file you're in (must match `applyTo`)
2. Check the agent description (must match your task)
3. Read the relevant instruction file manually
4. Ask Copilot: "Show me the security guardrails for token attachment"

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

## Key Design Patterns Enforced (Sage and Forest Theme)

### Visual Design
- ✅ All colors from CSS custom properties on `:root`
- ✅ Spacing: multiples of 2 or 4
- ✅ Radius: 12px frames, 10px cards, 8px inputs/buttons, 99px badges
- ✅ Status badges: word + shape marker (not color alone)
- ✅ Typography: 13px dense, 15px headings, 20px figures, tabular-nums on prices
- ✅ Accessibility: focus ring, labels, aria attributes, prefers-reduced-motion
- ❌ No hard-coded hex values
- ❌ No gradients or heavy shadows
- ❌ No color-only status indicators

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

## Configuration & Maintenance

### To Update Agent Behavior

Edit the relevant `.agent.md` or `.instructions.md` file:
1. Open the file in VS Code
2. Update the description, context, or patterns
3. Save and commit
4. Changes apply immediately on next file interaction

### To Add New Guardrails

Edit `.github/hooks/sprint-09-security.json`:
1. Add a new object to the array
2. Set trigger, toolPattern, condition, action, message
3. Commit and it runs on next file edit

### To Create New Prompts

Create a new file in `.github/prompts/`:
1. Follow the frontmatter pattern (name, description, applyTo)
2. Add parameters and examples
3. Commit and it appears as a slash command (`/my-prompt-name`)

## Example: Using the Agent During Development

**Scenario**: You're building the order ticket component and need to generate an API client.

```bash
# Step 1: Check the agent is loaded
# → You're in sprint-09-trading-ui/src/components/order-ticket.component.ts
# → Copilot shows security reminders automatically

# Step 2: Generate the client
# Type in chat: /generate-client

# Step 3: Agent responds with prompt for contract path and output
# You enter:
#   Contract: contracts/trade-api.yaml
#   Output: src/generated/trade-api
#   Language: typescript

# Step 4: Client is generated and committed
git commit -m "chore: generate trade-api client from contract"

# Step 5: Write the interceptor
# Create src/auth.interceptor.ts
# → Hooks detect Authorization header setup
# → Hook reminds: "Is this in the authInterceptor only?"
# → You verify: yes, it's the single interceptor

# Step 6: Write tests
# Create src/auth.interceptor.spec.ts
# → Instructions remind: "Test attach case and do-not-attach case"
# → You write both named tests
# → Review verifies both tests pass by name
```

## Testing the Agent Locally

Before committing agent changes:

```bash
# Read the agent file
cat .github/agents/sprint-09-trading-ui.agent.md

# Verify frontmatter (--- markers, valid YAML)
# Verify applyTo pattern is correct
# Verify description matches your use case

# Test: Create a dummy file matching applyTo
touch sprint-09-trading-ui/src/test-component.ts

# Open it in VS Code
# Check that Copilot shows the agent suggestions

# Clean up
rm sprint-09-trading-ui/src/test-component.ts
```

## Reference

- **VS Code Agent Customization Skill**: [agent-customization SKILL.md](c:\Program Files\Microsoft VS Code\a5b5009513\resources\app\extensions\copilot\assets\prompts\skills\agent-customization\SKILL.md)
- **Sprint 9 Requirements**: [sprint-09-trading-ui/README.md](../sprint-09-trading-ui/README.md)
- **Angular 21 Docs**: https://angular.io/docs
- **OpenAPI Generator**: https://openapi-generator.tech/

## Sprint 10

- **[`agents/sprint-10-ui-design.agent.md`](./agents/sprint-10-ui-design.agent.md)**: the UI redesign agent (shell, mascot, alerts, portfolio, downloads, e-mail)
- **[`instructions/sprint-10-ui-styling.instructions.md`](./instructions/sprint-10-ui-styling.instructions.md)**: tokens, motion, mascot styling
- **[`instructions/price-alerts-ui.instructions.md`](./instructions/price-alerts-ui.instructions.md)**
- **[`instructions/portfolio-redesign.instructions.md`](./instructions/portfolio-redesign.instructions.md)**
