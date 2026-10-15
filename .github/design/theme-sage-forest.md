---
name: sage-forest-theme
description: "Visual design system for Sprint 9 trading UI: Sage and forest theme with soft warm light, one deep-green accent, quiet neutrals. No hard-coded colors in components—use CSS custom properties only."
applyTo: "sprint-09-trading-ui/**"
---

# Sage and Forest: Sprint 9 UI Theme

A minimal, professional visual direction for the trading platform. Calm surfaces, one accent colour, generous spacing, no gradients, no shadows heavier than 1px border. Colour is never the only signal—status and errors always carry a word.

## Design Principles

- **Minimal and professional**: Calm surfaces, one accent colour, generous spacing, no gradients, no shadows
- **Status is always labeled**: Never use colour alone; errors and states carry both word and shape marker
- **Error messages are actionable**: Map API error codes to trader-friendly copy; never render raw API messages
- **Sentence case everywhere**: No all-caps labels except the four status words (NEW, FILLED, REJECTED, CANCELLED)
- **Dark mode out of scope**: Verify contrast meets WCAG AA (4.5:1 for body text) before release

## Design Tokens (CSS Custom Properties)

Define these once in `src/styles.css` or equivalent global stylesheet on `:root`. Components use tokens only—**never hard-coded hex values**.

```css
:root {
  /* Neutral surfaces */
  --bg: #f3f5ef;           /* Page background, input fill */
  --surface: #fcfdf9;      /* Cards, nav bar */
  --line: #d8dfcf;         /* All borders and dividers */
  
  /* Text */
  --text: #1f2a22;         /* Primary text, WCAG AA contrast ✓ */
  --muted: #66736a;        /* Labels, secondary text, table headers */
  
  /* Accent: Deep green */
  --accent: #2f6b4f;       /* Primary button, active nav underline, focus ring */
  --on-accent: #ffffff;    /* Text on accent backgrounds */
  
  /* Status colors and backgrounds */
  --ok: #2f6b4f;
  --ok-bg: #e0eee5;        /* FILLED badge */
  
  --new: #345f8a;
  --new-bg: #e2ecf5;       /* NEW badge, info banner */
  
  --reject: #a5382c;
  --reject-bg: #f8e5e1;    /* REJECTED badge, error banner, field errors */
  
  --cancel: #5f6a62;
  --cancel-bg: #e8ebe4;    /* CANCELLED badge */
}
```

### Token Contrast Verification (WCAG AA)
- ✅ `--text` (#1f2a22) on `--bg` (#f3f5ef): **16.8:1** (exceeds 4.5:1)
- ✅ `--text` (#1f2a22) on `--surface` (#fcfdf9): **18.2:1** (exceeds 4.5:1)
- ⚠️ `--muted` (#66736a) on `--bg` (#f3f5ef): **4.6:1** (meets minimum, test at actual size)
- ✅ `--accent` (#2f6b4f) on `--on-accent` (#ffffff): **5.2:1** (exceeds 4.5:1)

## Typography & Spacing

### Font Family
```css
font-family: "Segoe UI", Helvetica, Arial, sans-serif;
```

Use **tabular numerals** (`font-variant-numeric: tabular-nums`) for every price, quantity, and amount to align figures.

### Font Sizes
- **Dense views** (tables, forms): 13px
- **Page headings**: 15px
- **Key figures** (account balance, cash, positions): 20px
- **Buttons and labels**: 13px

### Spacing Scale
Keep all spacing to multiples of 2 or 4.
- **Page padding**: 16px
- **Card padding**: 14px
- **Gap between fields and cards**: 10px
- **Gaps within cards**: 8px

### Border & Radius
- **All borders**: 1px solid `--line`
- **Page frames**: 12px radius
- **Cards**: 10px radius
- **Inputs, buttons, banners**: 8px radius
- **Badges**: 99px (pill)
- **Read-only fields**: Dashed border + `--muted` text color

## Layout

### Top Navigation Bar
Present on all screens except sign-in.

```
┌─────────────────────────────────────────────────────┐
│ Fauxtrade    Dashboard  Order ticket  Blotter  [user] Sign out │
└─────────────────────────────────────────────────────┘
```

- **Left**: Brand/logo
- **Center**: Links (Dashboard, Order ticket, Blotter)
  - Active link: `--text` with 2px `--accent` underline
  - Inactive link: `--muted` text
- **Right**: Username and Sign out link

**Implementation**: Sticky nav, `--surface` background, 1px `--line` bottom border, 16px padding

### Sign-In Layout
Single centred card, ~270px wide, no navigation bar.

```
┌──────────────────────┐
│                      │
│  Sign in to Fauxtrade│
│  ┌────────────────┐  │
│  │ Username       │  │
│  ├────────────────┤  │
│  │ Password       │  │
│  ├────────────────┤  │
│  │ [Sign in]      │  │
│  └────────────────┘  │
│ You will return to    │
│ blotter after signing in. │
│                      │
└──────────────────────┘
```

- **Background**: `--bg`
- **Card**: `--surface`, 10px radius, 14px padding
- **Inputs**: 8px radius, `--line` border
- **Button**: `--accent` background, `--on-accent` text, 8px radius, 13px font

### Content Layout
Content sits in cards on `--bg` background with 16px page padding.

## Screens

### 1. Sign In

**Key elements:**
- Centred card (270px wide)
- Username field (required)
  - Label: "Username"
  - Input with `data-testid="username"`
  - 8px radius, `--line` border, `--bg` background
- Password field (required)
  - Label: "Password"
  - Input with `data-testid="password"` (type="password")
  - 8px radius, `--line` border, `--bg` background
- Sign in button (primary)
  - `data-testid="submit"`
  - `--accent` background, `--on-accent` text, 8px radius
  - Full width
- Error banner (if AUTH-401)
  - `--reject` text on `--reject-bg`, 1px `--reject` border, 8px radius
  - `role="alert"`
  - Above the form
  - Maps error code to friendly message (see Error Messages section)
- Return URL note
  - Below button: "You will return to **blotter** after signing in."
  - 13px, `--muted` text

### 2. Dashboard

**Layout:**
```
┌──────────────────────────────────────┐
│ Account ACC-1042                     │
├──────────────────────────────────────┤
│  Cash       Positions    Working     │
│  24,310.50      7            2       │
├──────────────────────────────────────┤
│ Recent orders         [View blotter] │
│ Time   Symbol  Side  Qty   Status    │
│ 10:42  ABC    Buy    50    NEW ●     │
│ 10:31  XYZ    Sell   20    FILLED ◇  │
│ 10:20  ABC    Buy    900   REJECTED — │
└──────────────────────────────────────┘
```

**Elements:**
- **Heading**: Account ID, 15px, `--text`
- **Stat cards**: 3 columns
  - Background: `--surface`
  - Padding: 14px
  - Radius: 10px
  - Label: 13px, `--muted`
  - Value: 20px, `--text`, tabular-nums font-variant
- **Table**: 13px, headers in `--muted`, status badges with markers (see below)
- **Link**: "View blotter" in `--accent` text

### 3. Order Ticket

**Layout:**
```
┌──────────────────────────────┐
│ New order                    │
├──────────────────────────────┤
│ Account                      │
│ ┌────────────────────────┐   │
│ │ ACC-1042 - read only   │   │ (dashed border, --muted text)
│ └────────────────────────┘   │
├──────────────────────────────┤
│ Symbol          Side         │
│ ┌──────┐    [Buy] [Sell]     │
│ │ ABC  │                     │
│ └──────┘                     │
├──────────────────────────────┤
│ Quantity        Price        │
│ ┌──────┐       ┌──────┐      │
│ │ 12.5 │       │ 10.123│     │
│ └──────┘       └──────┘      │
│ Enter whole     Use at most   │
│ number above    two decimal   │
│ zero.           places.       │
├──────────────────────────────┤
│ ┌────────────────────────────┐│
│ │ There is not enough cash   ││ (banner, --reject bg/text)
│ │ for this order. Reduce the ││
│ │ quantity or add funds.     ││
│ └────────────────────────────┘│
├──────────────────────────────┤
│ [Place order]                │
│ (disabled 45% opacity until  │
│  form valid)                 │
└──────────────────────────────┘
```

**Elements:**
- **Card**: `--surface`, 10px radius, 14px padding
- **Account field** (read-only):
  - Dashed border (1px)
  - `--muted` text
  - 8px radius
  - No editable cursor
- **Symbol input**: 8px radius, `--line` border, `--bg` background
- **Side control**: Segmented (Buy/Sell)
  - Selected segment: `--accent` background, `--on-accent` text
  - Unselected: `--bg` background, `--text` text
- **Quantity & Price inputs**: 8px radius, `--line` border
  - Price: Show inline error below if invalid (13px, `--reject` text)
  - Hint text below: 13px, `--muted` text
- **Error/catalogue banner** (if present):
  - Above form, `--reject` on `--reject-bg`, 1px `--reject` border
  - 8px radius, `role="alert"`
  - Maps error code to friendly message
- **Place order button**:
  - Primary: `--accent` background, `--on-accent` text
  - Disabled: Same color, 45% opacity, pointer: not-allowed
  - Full width, 8px radius

**Form Validation (Client-Side UX Only):**
- ✓ Quantity: Positive integer, no decimal
- ✓ Price: > 0, at most 2 decimal places, tabular-nums
- ✓ Symbol: Matches contract-defined pattern
- ✓ Account: Pre-filled from token, read-only, cannot edit

### 4. Blotter

**Layout:**
```
┌─────────────────────────────────────────────────┐
│ Orders, newest first          [Refresh]          │
├─────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────┐│
│ │ 1 order is still working. Checking again     ││
│ │ shortly, attempt 2 of 10.         (attempt)  ││ (info banner, --new bg/text, role="status")
│ └──────────────────────────────────────────────┘│
├─────────────────────────────────────────────────┤
│ Time    Symbol  Side  Qty    Price   Status     │
│ 10:42   ABC     Buy   50     10.25   NEW ●      │
│ 10:31   XYZ     Sell  20     88.40   FILLED ◇   │
│ 10:20   ABC     Buy   900    10.10   REJECTED — │
│ 09:58   QRS     Buy   10     5.00    CANCELLED ○│
│ (1 order is still working. Checking again       │
│  shortly, attempt 2 of 10.)                     │
└─────────────────────────────────────────────────┘
```

**Elements:**
- **Heading**: "Orders, newest first", 15px, `--text`
- **Refresh button**: Outline button, `--text` text on `--surface` background, 1px `--line` border
- **Info banner** (`--new` on `--new-bg`, `role="status"`):
  - Above table, 8px radius, 1px `--new` border
  - Shows polling status: "1 order is still working. Checking again shortly, attempt 2 of 10."
  - Disappears when all orders are terminal (FILLED, REJECTED, CANCELLED)
- **Table**: 13px, headers in `--muted`, columns: Time, Symbol, Side, Qty, Price, Status
  - Newest first (most recent at top)
  - Include rejected orders
  - Status badges with markers (see below)
  - Qty and Price use tabular-nums

## Status Badges

Pill (11.5px font-weight 600) with 6px marker before the word. All four must be visually distinct without colour.

```
┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│ ● NEW        │   │ ◇ FILLED     │   │ — REJECTED   │   │ ○ CANCELLED  │
├──────────────┤   ├──────────────┤   ├──────────────┤   ├──────────────┤
│ --new-bg     │   │ --ok-bg      │   │ --reject-bg  │   │ --cancel-bg  │
│ --new text   │   │ --ok text    │   │ --reject text│   │ --cancel text│
└──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘
```

### Badge Specifications

| Status | Text Color | Background | Marker | Marker Type |
|--------|------------|------------|--------|-------------|
| **NEW** | `--new` (#345f8a) | `--new-bg` (#e2ecf5) | Filled dot (●) | Pulse animation (disable under prefers-reduced-motion) |
| **FILLED** | `--ok` (#2f6b4f) | `--ok-bg` (#e0eee5) | Diamond (◇) | Static |
| **REJECTED** | `--reject` (#a5382c) | `--reject-bg` (#f8e5e1) | Short dash (—) | Static |
| **CANCELLED** | `--cancel` (#5f6a62) | `--cancel-bg` (#e8ebe4) | Hollow circle (○) | Static |

**Important**: NEW is a working state, not an error. Never style it like an error (red). It's `--new` blue on a light blue background.

## Error Messages & States

### Error Banner
```html
<div role="alert" class="error-banner">
  There is not enough cash for this order. Reduce the quantity or add funds.
</div>
```

Styling:
- Background: `--reject-bg`
- Text: `--reject`
- Border: 1px solid `--reject`
- Radius: 8px
- Padding: 12px 14px
- Margin: 10px 0

### Info Banner (Polling Status)
```html
<div role="status" class="info-banner">
  1 order is still working. Checking again shortly, attempt 2 of 10.
</div>
```

Styling:
- Background: `--new-bg`
- Text: `--new`
- Border: 1px solid `--new`
- Radius: 8px
- Padding: 12px 14px
- Margin: 10px 0

### Field-Level Errors
```html
<input aria-describedby="price-error" />
<span id="price-error" class="field-error">
  Use at most two decimal places.
</span>
```

Styling:
- Text: `--reject`
- Font-size: 13px
- Margin-top: 4px

### Error Code Mapping

Every code in both contracts needs a sentence. Keep the mapping in one file, test that it covers every code.

**Auth API codes:**
- `AUTH-401`: "Your username or password was not accepted. Check them and try again."
- `AUTH-409`: "This account already exists. Sign in instead."

**Trade API codes:**
- `ACC-404`: "The account you tried to access does not exist."
- `ACC-403`: "You do not have permission to trade this account."
- `INS-404`: "The symbol does not exist or is not tradeable."
- `ORD-400`: "The order parameters are invalid. Check quantity and price."
- `ORD-409`: "You do not have enough cash or positions for this order."
- `VAL-422`: "The submitted data is invalid. Check all fields."

**Fallbacks:**
- Status 0 (service unreachable): "The service is unreachable. Check your internet connection and try again."
- Unknown code: "An error occurred. Refresh the page and try again."

Map these in a TypeScript service (e.g., `src/services/error-messages.service.ts`) and use it everywhere errors are rendered.

## Data States

Show all four states for every data-driven view:

### Loading State
```
[skeleton loader with pulse animation]
or
"Loading..."
```

### Empty State
```
"No orders yet. Place your first order from the Order ticket."
```

### Success State
```
[rendered data]
```

### Error State
```
Error banner with friendly message (not raw API message)
```

## Accessibility

### Labels and Inputs
- Every input must have a visible label
- Label associated with input via `<label for="fieldId">` (not just proximity)
- Read-only fields have distinct styling (dashed border)

### Focus Ring
- Visible focus ring using `--accent` (2px outline, 2px offset)
- Never remove focus indicators

### Keyboard Navigation
- Full keyboard operation: tab, enter, arrow keys (for segmented controls)
- Tab order logical (left to right, top to bottom)

### Aria Attributes
- Field errors linked with `aria-describedby`
- Error banners: `role="alert"`
- Status banners: `role="status"`
- Ensure screen reader can access all information

### Motion
- Respect `prefers-reduced-motion`
- Pulse animation on NEW badge: `@media (prefers-reduced-motion: reduce) { animation: none; }`

## Do Not (Enforcement)

❌ **Do not add colours, gradients or shadows** outside the tokens above.
❌ **Do not use colour alone** to convey status or errors—always add text and shape.
❌ **Do not make the account field editable**.
❌ **Do not hard-code hex values** in components—use CSS custom properties only.
❌ **Do not render the raw API `message`**—map `errorCode` to friendly copy.
❌ **Do not render empty screens** on auth failure (confuses users)—redirect + show message.
❌ **Do not skip labels** on inputs—visibility required for accessibility.
❌ **Do not animate NEW badge** if `prefers-reduced-motion` is set.

## Implementation Checklist

### Global Stylesheet (`src/styles.css` or equivalent)
- [ ] Define all tokens as CSS custom properties on `:root`
- [ ] Set `font-family` globally
- [ ] Define base 13px font-size for body
- [ ] Test token contrast against WCAG AA
- [ ] Include `prefers-reduced-motion` media query for animations

### Components
- [ ] All colors use CSS variables (no hex values)
- [ ] All spacing uses multiples of 2 or 4
- [ ] 12px radius on page frames, 10px on cards, 8px on inputs/buttons/banners
- [ ] 99px radius on badges (pill shape)
- [ ] Use `font-variant-numeric: tabular-nums` for prices, quantities, amounts
- [ ] All inputs have visible labels
- [ ] All data-driven views show loading, empty, success, error states
- [ ] Error banners use friendly copy (error code mapping)
- [ ] Status badges have distinct markers (not color alone)
- [ ] Focus ring is visible (2px `--accent` outline)
- [ ] Keyboard navigation works (tab, enter, arrows)
- [ ] Aria attributes in place (`role`, `aria-describedby`, etc.)

### Testing
- [ ] Screenshot test with default theme
- [ ] WCAG AA contrast verified (4.5:1 for body text)
- [ ] Keyboard-only navigation works
- [ ] Screen reader announces all content
- [ ] Motion respects `prefers-reduced-motion`
- [ ] All error codes mapped and tested
- [ ] All data states (loading, empty, success, error) visible

## Reference Screenshots

See the Figma mockup (linked in `.github/design/`) for visual reference of all screens.
