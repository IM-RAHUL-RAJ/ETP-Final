---
name: price-alerts-ui
description: "Use when working on the price alerts screens or API client: the sentence-style create/edit dialog, the alerts table with switch/edit/delete/history, the trigger history side panel, filter tabs, and the operator wording in notifications."
applyTo: "frontend/src/app/components/users/alerts/**|frontend/src/app/core/**/alert*|services/trade-api/src/main/java/com/capstone/etp/alerts/**"
---

# Price Alerts UI and API

## Model

- A condition is `condition` + `price`: `>`, `>=`, `<`, `<=`, `==`. The API speaks the symbols; the database
  stores `GT GTE LT LTE EQ`; the old `direction` column is kept and derived (`ABOVE` for `>`, `>=`, `==`).
- An alert is **armed** when `status = ACTIVE` and `enabled`. Only armed alerts are in the evaluator's index.
- Each firing writes one `price_alert_triggers` row (history) and one `PRICE_ALERT` notification. The
  notification's event id is derived from `(alertId, triggerId)`, so a retried hand-over is one message and a
  second firing is a second.
- Switching a fired alert back on, or editing it, **re-arms** it (status back to `ACTIVE`). Its history stays.
- Delete removes the alert and, by cascade, its history. It is refused (409) while a fired alert is still being
  handed over for delivery.
- A level the price already satisfies is refused with 422 and the reason, except `==` (a level to watch for).
- Equality compares at the four decimals the level is stored at.

## API

| Call | Notes |
|---|---|
| `GET /api/v1/accounts/{id}/alerts` | `condition`, `price`, `enabled`, `status`, `deliveryState`, `triggerCount` |
| `POST .../alerts` | `{symbol, condition, price, enabled?}` -> 201 |
| `PUT .../alerts/{alertId}` | any of `symbol, condition, price, enabled`; absent fields stay |
| `PUT .../alerts/{alertId}/enabled` | `{enabled}` -> `{enabled}` |
| `GET .../alerts/{alertId}/triggers` | newest first, with `notificationStatus` |
| `DELETE .../alerts/{alertId}` | removes alert and history |

Every call authorises the token's own account (403 otherwise); another account's alert is 404, never 403, so
ids cannot be probed. Unknown JSON fields are refused with 422.

## Create / edit dialog (`AlertDialogComponent`)

- A sentence: **If price of [instrument] is [operator] than [price] [CCY]**. Title "Create Alert" / "Edit Alert";
  buttons "Create" / "Update" and "Cancel".
- The instrument box is a searchable combobox (`role="combobox"`, listbox of `symbol name`). Typing again
  un-chooses the instrument until one is picked.
- Operator labels: "Greater than (>)", "Greater or equal (>=)", "Less than (<)", "Less or equal (<=)", "Equals (==)".
- Price: text input, `^\d+(\.\d{1,4})?$` and above zero. Errors sit under the sentence in `--reject`.
- The currency pill takes its colour from `data-ccy` and shows the chosen instrument's currency.
- The server's 422/409 message is shown as it is (it explains "already reached" and "too many alerts").

## List (`AlertsComponent`)

- Header: "Total: N alerts · X enabled, Y disabled · Z of 25 left", and the **Create Alert** primary button.
- Tabs: All, Enabled, Disabled, Triggered, each with a count. "Triggered" = fired at least once.
- Columns: Symbol, Condition (`≥ 230.50` + currency pill), Status (switch + word), Actions (edit, history, delete).
  Symbol, Condition and Status sort. Rows tint down when switched off.
- The switch flips at once and rolls back with the server's message if refused; it then re-reads the list,
  because re-arming changes the status.
- Delete asks first: "Delete this alert?" with "Keep it" / "Delete".
- Empty: the companion, "No alerts yet", and "Create your first alert".
- A fired alert whose hand-over is unresolved has edit, delete and the switch disabled.

## Trigger history panel (`TriggerHistoryPanelComponent`)

- Side panel from the right, title `Alert: AAPL ≥ 230.50`. Only triggers, never creation.
- Each card: status dot (Sent green, Queued/Held amber, Failed red, Not sent/Not recorded grey), the price with
  its currency pill, relative time (`timeAgo`) with the exact time in `title`, and the delivery word.
- Escape, the scrim, the close button and **Back** all close it.

## Wording (backend `NotificationAlertDelivery`)

| Operator | Subject / body |
|---|---|
| `>` | `AAPL is above 230.50` |
| `>=` | `AAPL is at or above 230.50` |
| `<` | `AAPL is below 230.50` |
| `<=` | `AAPL is at or below 230.50` |
| `==` | `AAPL equals 230.50` |

Body adds `The latest price is 231.20.` Never put an operator code (`GTE`) or `ORDER_FILLED` in text a customer reads.

## Tests that must stay green

`alerts.component.spec.ts`, `alert-dialog.component.spec.ts`, `alert.service.spec.ts`;
`PriceAlertControllerTest`, `PriceAlertServiceTest`, `PriceAlertNotificationTest`; and the real-database
`ExtensionsChainIT` (needs `TEST_DB_URL`, see `docs/sprint-10/demo.md`).
