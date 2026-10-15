---
name: sprint-10-ui-styling
description: "Use when styling or creating CSS for the Sprint 10 trading UI. Enforces the Zard neutral pixel design system (supersedes the blue palette below; see Phase 2): tokens only (no hard-coded colours), 8px spacing scale, light and dark parity, mascot styling, and the animation timing rules."
applyTo: "frontend/src/**/*.css|frontend/src/**/*.scss"
---

# Sprint 10 UI Styling

This replaces the Sprint 9 "Sage and forest" palette. The token **names** are unchanged (`--bg`, `--surface`,
`--line`, `--text`, `--muted`, `--accent`, ...), so every existing component re-themed with one block at the
end of `frontend/src/styles.css`. That block is last on purpose: it wins the cascade. Edit it, not the older
palettes above it.

## Principles (adopt, do not copy)

- **Groww**: spacious, bold numbers, almost no colour except gain/loss and one accent, icons over words.
- **Stripe**: whitespace, one clear primary action per screen, direct trust-building copy.
- **Vercel**: dark mode is a first-class design, not an inversion; smooth, quiet micro-interactions.
- **Zardui**: token vocabulary. `--zd-background`, `--zd-foreground`, `--zd-card`, `--zd-border`, `--zd-primary`,
  `--zd-primary-foreground`, `--zd-muted-foreground`, `--zd-destructive`, `--zd-radius` alias our tokens.
  We use the vocabulary and write the primitives natively (modal, switch, tabs, menu) rather than installing
  Tailwind and Angular Material next to a working stylesheet. See the decision log.

## Tokens

| Role | Light | Dark |
|---|---|---|
| `--bg` page | `#f5f5f7` | `#0f0f10` |
| `--surface` card | `#ffffff` | `#1a1a1c` |
| `--line` hairline | `#e5e5ea` | `#2c2c31` |
| `--text` | `#1a1a1a` | `#f5f5f7` |
| `--muted` | `#6b7078` | `#9b9ca4` |
| `--accent` brand blue | `#1f5fd6` | `#5aa2ff` |
| `--accent-2` orange (rare) | `#e8650a` | `#ff9933` |
| `--ok` gain | `#1b7a52` | `#5cc48f` |
| `--reject` loss | `#c0392b` | `#f2887a` |

Rules:
- A component never writes a hex. It uses a token, or `color-mix(in srgb, var(--text) 6%, transparent)` for tints.
- Large areas are neutral. Brand blue is for the one primary button, the active nav item, focus rings and key links.
  Orange marks "needs attention" (the temporary-password dot) and nothing else.
- Gain is `--ok` and loss is `--reject`, and a **sign or word always accompanies the colour** (`+862.00`, "Gain/Loss").
- Text on `--bg`/`--surface` stays above 4.5:1 (WCAG AA) in both themes. Check any new pair in both.
- Theme is set on `<html data-theme="light|dark">` by `ThemeService`; with no attribute the
  `prefers-color-scheme` media query decides. Every colour rule must work under all three states.

```css
/* A tinted hover that works in both themes with no extra rule */
.row:hover { background: color-mix(in srgb, var(--text) 4%, transparent); }
```

## Spacing, radius, type

- Spacing scale: `--sp-1..6` = 8, 16, 24, 32, 40, 48px. Pages 16px gutter, cards 16px, sections 24px apart.
- Radius: `--r-sm` 8px (buttons, inputs), `--r-md` 12px (cards, menus), `--r-lg` 16px (dialogs), 99px pills.
- Headings 28/20/16px bold; body 14px/1.5; prices, ids and amounts use `.mono` / `.price` (`--font-mono`, 14px, tabular).
- `--font-dot` (Doto, a dot-matrix face from Google Fonts) is for **headline figures and page titles only**
  (Portfolio title, the total value, the blotter title). Never body text or dense tables.

## Components

- **Buttons**: `primary` (brand), `secondary` (hairline), `danger`, `ghost`; disabled is 50% opacity; a press
  scales to 0.95. Hover darkens (`--accent-hover`) and adds `--glow`; it does not lift.
- **Icon buttons**: `<button class="icon-btn" aria-label="Refresh" data-tip="Refresh">`. The hover label is the
  CSS-only `data-tip` (never `title`, which does not show on touch). `aria-label` is mandatory. Use
  `data-tip-below` near the top edge and `data-tip-end` near the right edge.
- **Inputs and dropdowns**: hairline border, 3px `--ring` focus glow. `select` is restyled (chevron, themed panel).
- **Tables**: air, not lines. Uppercase 12px headers, 4% hover tint, numeric columns right-aligned and `.mono`.
- **Tabs / filters**: `.tabs` + `button.active`, count in `<span class="count">`.
- **Switch**: `<button class="switch" role="switch" [attr.aria-checked]>` with a visible label.
- **Dialog**: use `<app-modal>`; it traps focus, closes on Escape and backdrop, returns focus.
- **Currency pill**: `<span class="pill-ccy" [attr.data-ccy]="code">`.
- **Freshness dot**: `.dot.live|delayed|stale` (see portfolio-redesign instructions).

## Mascot styling

- `<app-mascot [type] [state] [size]>` is a button wrapping generated SVG. States: `idle` (breathes), `loading`
  (thinking pose in a turning ring), `happy` (two bounces), `empty` (thinking pose, slides in 300ms).
- Sizes: 140 on an empty state, 96 in the Settings picker, 36 in the top bar.
- The artwork's colours are part of the mascot, not the theme; do not recolour it with tokens. Its shadow and
  thought bubble do follow the theme.
- Never add a mascot where it would sit on top of data. It lives in empty states, the top bar, Settings, e-mail.

## Motion

| What | Time | Easing |
|---|---|---|
| Hover, colour, focus (`--fast`) | 200ms | ease-out |
| Entering content, modals, panels (`--enter`) | 300ms | `--ease` |
| Sidebar width | 300ms | ease-in-out |
| Dropdown / menu open | 200ms | scale from (0.9, 0.85) with opacity |
| List stagger (`.stagger`) | 50ms between items, capped after 6 | `--ease` |
| Bounce (`--bounce`) | for mascot and the switch knob only | `cubic-bezier(0.34, 1.56, 0.64, 1)` |
| Spinners | 1.8s per turn | linear |

- Nothing animates for longer than 400ms except a spinner.
- Do not animate rows of a long table one by one beyond the first six, and never move things the user scrolls.
- All motion stops under `prefers-reduced-motion` (a global rule does this; do not fight it).
- **Never leave a `transform` animation filling forwards on an ancestor of a `position: fixed` element.** A filling
  transform makes that ancestor the containing block and the dialog collapses into it. Page entrance uses
  `animation-fill-mode: backwards`, and overlays opt out (`.page > app-modal { animation: none }`).

## Layout

- App shell: `app-sidebar` (240px, 64px collapsed, a drawer under 800px) beside the page; `app-app-header` is the
  top bar (menu on phones, companion, Settings gear, avatar menu).
- A `.page` is a single shrinkable column (`grid-template-columns: minmax(0, 1fr)`); a wide table must scroll
  inside its own `.scroll` box and never widen the page.
- Check every new screen at 390px, 768px and 1280px, in light and dark.

## Phase 2: Zard neutral, pixel art (supersedes the blue palette above)

- Tokens come from the Zard (shadcn neutral) set in `frontend/src/styles.css`: `--bg --surface --muted-bg --line --text --muted --accent --on-accent --signal --ok --reject --c1..c6`. The accent is near-black (white in dark mode); `--signal` red marks the active nav edge and unread counts. No gradients, no coloured card borders.
- The one typeface is Doto (self-hosted via @fontsource/doto, weights 500/700/900) for all text; charts use it too. Canvas charts render at half resolution with `image-rendering: pixelated` and step lines.
- Every pop-up (`app-modal`) carries a mascot with an animation (`mascot` input); toasts are the mascot's speech bubbles (`ToastService` + `app-mascot-dock`).
- Mascots are pixel art from `core/mascot/pixel-mascot.ts`; never add an SVG or PNG mascot.
