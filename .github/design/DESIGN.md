# Design System for Sprint 9 Trading UI

This folder contains design and styling guidance for the Trading Platform UI (Sprint 9).

## Files

- **[`theme-sage-forest.md`](./theme-sage-forest.md)** — Complete visual design system
  - Design principles, tokens, typography, layout, screens, components
  - Status badges, messages, accessibility, implementation checklist
  - Reference for colors, spacing, radius, and all visual specifications

- **[`../instructions/sprint-10-ui-styling.instructions.md`](../instructions/sprint-10-ui-styling.instructions.md)** — Styling patterns for components
  - Global stylesheet setup (CSS variables, buttons, labels, inputs, tables)
  - Component patterns (nav bar, status badge, error banner, form field)
  - Color, spacing, radius, typography rules
  - Accessibility checklist, test checklist

## Quick Reference

### Design Tokens

| Token | Color | Use |
|-------|-------|-----|
| `--bg` | #f3f5ef | Page background, input fill |
| `--surface` | #fcfdf9 | Cards, nav bar |
| `--line` | #d8dfcf | Borders and dividers |
| `--text` | #1f2a22 | Primary text |
| `--muted` | #66736a | Labels, secondary text |
| `--accent` | #2f6b4f | Primary button, focus ring |
| `--ok` / `--ok-bg` | #2f6b4f / #e0eee5 | FILLED badge |
| `--new` / `--new-bg` | #345f8a / #e2ecf5 | NEW badge |
| `--reject` / `--reject-bg` | #a5382c / #f8e5e1 | REJECTED badge |
| `--cancel` / `--cancel-bg` | #5f6a62 / #e8ebe4 | CANCELLED badge |

### Spacing Scale

- Page padding: **16px**
- Card padding: **14px**
- Gap between fields: **10px**
- Gap within cards: **8px**
- All spacing: multiples of 2 or 4

### Radius Scale

- Page frames: **12px**
- Cards: **10px**
- Inputs, buttons, banners: **8px**
- Badges: **99px** (pill)

### Status Badges

| Status | Marker | Color | Background |
|--------|--------|-------|------------|
| **NEW** | ● (pulse) | --new | --new-bg |
| **FILLED** | ◇ | --ok | --ok-bg |
| **REJECTED** | — | --reject | --reject-bg |
| **CANCELLED** | ○ | --cancel | --cancel-bg |

### Typography

- Font: "Segoe UI", Helvetica, Arial, sans-serif
- Base: 13px (dense), 15px (headings), 20px (figures)
- Use `font-variant-numeric: tabular-nums` on all prices/quantities/amounts

## How to Use

### 1. Setting Up Styles

Start in `src/styles.css`:
```css
:root {
  --bg: #f3f5ef;
  --surface: #fcfdf9;
  /* ... define all tokens ... */
}
```

Then use tokens in all component styles:
```typescript
styles: [`
  .component {
    background-color: var(--surface);
    color: var(--text);
    border: 1px solid var(--line);
    border-radius: 10px;
    padding: 14px;
  }
`]
```

### 2. Building Components

Follow the component patterns in `sprint-10-ui-styling.instructions.md`:
- Status badge (with pulsing NEW state)
- Error banner (role="alert")
- Form field with validation hints
- Navigation bar with active link indicator

### 3. Checking Your Work

**Visual checklist:**
- [ ] All colors from CSS tokens (no hex values in components)
- [ ] Spacing: multiples of 2 or 4
- [ ] Radius: 12/10/8/99px only
- [ ] Status badges: all four states present + distinct markers

**Accessibility checklist:**
- [ ] Focus ring visible (2px accent outline)
- [ ] Labels associated with inputs
- [ ] Error messages linked with `aria-describedby`
- [ ] Banners have `role="alert"` or `role="status"`
- [ ] Motion respects `prefers-reduced-motion`
- [ ] WCAG AA contrast (4.5:1 for body text)

## Screens

### 1. Sign In
Centred card, ~270px, no nav bar. Username, password, sign-in button, error banner (if AUTH-401), return URL note.

### 2. Dashboard
Nav bar, account heading, three stat cards (cash, positions, working orders), recent-orders table with status badges.

### 3. Order Ticket
Nav bar, one card: account (read-only dashed), symbol, side (segmented), quantity, price, validation hints, error banner, place-order button.

### 4. Blotter
Nav bar, heading, refresh button, orders table (newest first), info banner (polling status).

## Rules (Do / Don't)

### Do
✅ Use CSS custom properties for all colors
✅ Spacing: multiples of 2 or 4
✅ Radius: 12/10/8/99px
✅ Status: word + shape marker (not color alone)
✅ Accessibility: labels, focus ring, aria attributes
✅ Motion: respect prefers-reduced-motion

### Don't
❌ Hard-code hex values in components
❌ Arbitrary spacing (11px, 9px, etc.)
❌ Random radius values
❌ Use color alone to convey status
❌ Remove focus indicators
❌ Make account field editable
❌ Render raw API messages (map error codes)

## Reference

- Full theme spec: [`theme-sage-forest.md`](./theme-sage-forest.md)
- Styling patterns: [`../instructions/sprint-10-ui-styling.instructions.md`](../instructions/sprint-10-ui-styling.instructions.md)
- Figma mockup: See `.github/design/` for visual reference
- Angular instructions: [`../instructions/angular-trading-ui.instructions.md`](../instructions/angular-trading-ui.instructions.md)

## Maintenance

If you need to:
- **Add a new color**: Add it to `theme-sage-forest.md`, define in `:root`, update this file
- **Change spacing scale**: Update all references in `theme-sage-forest.md` and `sprint-10-ui-styling.instructions.md`
- **Add a component pattern**: Document in `sprint-10-ui-styling.instructions.md` with ✅ CORRECT and ❌ WRONG examples

## Status

- **Created**: 2026-09-30
- **Sprint**: 9 (Trading UI)
- **Theme**: Sage and Forest (soft warm light, one deep-green accent, quiet neutrals)
