# 0015 The design system uses Zardui's token vocabulary; the primitives are written natively

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | Sprint 10 UI enhancement work (drafted by Claude from the sprint brief; the team to confirm) |

## Context

The brief asks for Zardui, Material theme overrides (`mat-select`, `mat-menu`, `mat-icon-button`) and Material tooltips. The app has neither Tailwind nor Angular Material, and a 1,500-line stylesheet and 20 screens that already work and pass 228 tests. Zardui is a Tailwind and CDK based, copy-in component set.

## Options considered

| Option | For | Against |
|---|---|---|
| Keep the existing token cascade, re-theme it with one block that uses Zardui's names (`--zd-*`), and write the few primitives needed (modal, switch, tabs, menu, tooltip) natively | No new build tooling, no regression risk on 20 screens, small bundle, one theme source for light and dark | Not the Zardui package itself; new primitives are ours to maintain |
| Install Tailwind and Angular Material and migrate every screen | Matches the brief's snippets literally | Two styling systems side by side during a migration, Tailwind's reset fights the existing CSS, a large bundle increase, and every screen re-tested |
| Install Zardui components only for new screens | Closer to the brief | The new and old screens would look different; still needs Tailwind |

## Decision

Re-theme through one block at the end of `frontend/src/styles.css`: neutral surfaces, one brand blue, `--zd-*` aliases, spacing and motion tokens, light and dark. Write native components: `app-modal`, `.switch`, `.tabs`, avatar menu, `data-tip` hover labels, `app-icon`. Dropdown panels are themed through the same tokens.

## Consequences

The Material snippets in the brief are implemented with native elements and the same behaviour (labels, keyboard, ARIA). If the team later adopts Tailwind, the `--zd-*` names are the seam. The native `select` popup is drawn by the browser, so only its closed state is fully themed; the instrument picker in the alert dialog is a custom listbox for that reason.
