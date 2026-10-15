---
name: sprint-10-ui-design
description: "Use when redesigning or extending the Sprint 10 trading UI: the collapsible sidebar shell, Settings, the mascot companion and empty states, price alerts, portfolio and watchlist charts, downloads, and the e-mail templates. Modern, minimal, token-driven, accessible, and fast."
applyTo: "frontend/**|services/trade-api/src/main/resources/email/**"
---

# Sprint 10: UI Design Agent

## Philosophy

Minimal, calm, pixel-art. Zard neutral surfaces (black, white, grey), one signal red, the Doto dot font, pixel mascots that move. Spacious like Groww, trustworthy
like Stripe, dark-mode-first like Vercel. Inspirations, not templates: adopt the principles, keep our identity.

- <https://groww.in/> data-forward, few colours, icons over words
- <https://stripe.com/> whitespace, clear primary action, direct copy
- <https://vercel.com/> dark mode as a design, quiet micro-interactions
- <https://zardui.com/> the token vocabulary behind `--zd-*`

Every motion has a purpose and lasts under 400ms. Every colour is a token. Every icon has a text label (hover
tip and `aria-label`). Every list that can be empty shows the companion and the next step.

## Read first

- `.github/instructions/sprint-10-ui-styling.instructions.md`: tokens, spacing, motion, mascot styling
- `.github/instructions/angular-trading-ui.instructions.md`: component, service and shell patterns
- `.github/instructions/price-alerts-ui.instructions.md`
- `.github/instructions/portfolio-redesign.instructions.md`
- `.github/instructions/security-guardrails.instructions.md`: unchanged and still binding

## Constraints

1. **Standalone components and signals**, `OnPush`, no `NgModule`. Overlays use `<app-modal>`.
2. **Tokens only.** If a colour is missing, add a token to the Sprint 10 block of `styles.css` in both themes.
3. **Both themes from the start.** Check light, dark and `auto` (no attribute) before calling a screen done.
4. **Performance.** Heavy libraries load on first use with `await import(...)`: ECharts, Lightweight Charts,
   exceljs, jsPDF. The main bundle stays small; run `ng build` and `node scripts/scan-bundle.mjs` (no keys,
   secrets, provider hosts or source maps in the build).
5. **Accessibility.** Keyboard reachable, visible focus, `aria-label` on icon-only controls, `role="switch"` with
   `aria-checked`, `aria-sort` on sortable headers, live regions for toasts, dialogs that trap and return focus,
   contrast above 4.5:1, nothing conveyed by colour alone.
6. **Security.** The bearer token goes only to our own APIs. Market data and candles come through the platform;
   a quote provider is never called from the browser. No `innerHTML` except the mascot artwork we generate
   from constants. Never log or render a contact detail unmasked.
7. **Honesty.** No invented data: value history is what was recorded, candles that cannot be fetched are an
   error with a retry, "n/a" beats a guess.

## Patterns to reuse

- **Shell**: `ShellStateService` (collapsed in `localStorage`, phone drawer), `SidebarComponent`, `AppHeaderComponent`.
- **Empty state**: `<app-empty-state title message><a class="button">Next step</a></app-empty-state>`.
- **Filter tabs**: `.tabs` with counts, state kept in a signal, optionally remembered in `localStorage`.
- **Auto-save settings**: change a signal, `persist()` serialises saves and toasts "Settings saved"; on failure toast
  the server's reason and re-read.
- **Toasts**: `ToastService.show/error`, hosted once by `<app-toast-host>`.
- **Charts**: build an ECharts option in a `computed` that reads `ThemeService.changes()` so it rebuilds on a theme change.
- **Downloads**: build an `ExportDocument` (tables, subtitle lines naming the filters, optional charts) and call
  `ExportService.export(format, document, fileBase)`.
- **E-mail**: `services/trade-api/src/main/resources/email/*.html` (layout + one body per kind), rendered by
  `EmailTemplateRenderer` (values escaped, `{{{content}}}` is our own template). Mascot PNGs come from
  `npm run mascots` into `frontend/public/mascots/email/` and are served by the web app.

## Testing requirements

- A spec beside every component and service; behaviour, not implementation.
- Alerts: dialog opens, operators, numeric validation, create/edit payloads, delete confirmation, switch, history panel, tabs.
- Notifications: tabs call the service with the type, counts, type badge, wording with the operator (and "equals").
- Portfolio: tiles, plain words, filters and sort, freshness dots, download scope, failure states.
- Backend: `PriceAlertControllerTest`, `PriceAlertServiceTest`, `PriceAlertNotificationTest`, plus the real-database
  `ExtensionsChainIT` for anything that touches SQL (a mock does not run your SQL).
- Visual check at 390 / 768 / 1280px in light and dark before handing over; mock the API with Playwright routes
  if the stack is not running.

## Commands

```bash
cd frontend
npx ng test --watch=false
npx ng test --watch=false --include='**/alert*.spec.ts' --include='**/notification*.spec.ts'
npx ng build --configuration=production
node scripts/scan-bundle.mjs
npm run mascots            # re-render the e-mail mascot PNGs

cd services/trade-api
mvn test -Dtest='PriceAlert*Test'
mvn test                   # 4 OrderValidatorTest failures are known and unrelated
TEST_DB_URL=jdbc:postgresql://localhost:5432/<scratch> mvn test -Dtest=ExtensionsChainIT
```

## Do not

- Do not restyle with hard-coded colours, or add a second palette beside the token block.
- Do not animate long lists item by item, or anything the user scrolls.
- Do not put a mascot over data, or recolour its artwork.
- Do not read a chart's data from a third party in the browser, or send the token to one.
- Do not claim a feature works because it compiles: run it, or say it was not run.
