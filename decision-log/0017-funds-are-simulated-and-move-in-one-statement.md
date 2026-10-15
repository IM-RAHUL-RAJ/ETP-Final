# 0017 Add and withdraw funds are simulated, and each moves cash in one conditional statement

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | Sprint 10 UI enhancement work (drafted by Claude from the sprint brief; the team to confirm) |

## Context

The Profile page gains Add Funds and Withdraw Funds. There is no payment provider, and the balance is also changed by the order executor under optimistic locking (`accounts.version`).

## Options considered

| Option | For | Against |
|---|---|---|
| One `UPDATE accounts SET cash_balance = cash_balance +/- amount, version = version + 1 WHERE ... AND (withdrawal: cash_balance >= amount) RETURNING cash_balance`, then a `cash_movements` row, in one transaction | Cannot overdraw even with two requests; bumps `version` so an order in flight retries instead of overwriting; the balance and its record cannot disagree | The movement table is a second place a balance can be explained from |
| Read the balance, check, write | Familiar | Two withdrawals can both pass the check |
| Integrate a payment provider | Real | Out of scope and out of budget; the platform trades simulated money |

## Decision

Simulated: the payment method (bank transfer, UPI, card) is recorded and nothing is charged; the dialog says so. A single movement is capped (`app.funds.max-per-movement`, default 1,000,000). Each movement writes an inbox message (`FUNDS`) that the customer can switch off. The message failing never undoes the movement. No Kafka event is published: nothing downstream consumes cash movements yet.

## Consequences

If a real provider is added, a withdrawal becomes a request with a state (pending, paid, failed), and the balance change moves to when it is confirmed. The `kind` and `method` columns already carry what that needs.
