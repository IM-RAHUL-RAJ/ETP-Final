# 0012 The per-account alert cap is enforced inside the insert, counts every row, and finished alerts are removed by the customer

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Team 2, day one (drafted by Claude from the brief; the team to confirm) |

## Context

Every quote for every symbol passes the alert evaluator, in the process that places orders. An unbounded
creation route lets one account fill the table and turn our own consumer into the thing that slows order
placement. The cap must also hold when two requests arrive at once.

## Options considered

| Option | For | Against |
|---|---|---|
| `INSERT ... SELECT ... WHERE (SELECT COUNT(*) ...) < cap RETURNING id`, counting every alert the account still holds (any status), 25 by default | Check and insert are one statement, so concurrent requests cannot both slip under; create-then-cancel loops cannot grow the table | A customer must remove finished alerts to make room |
| Count in Java, then insert | Obvious | Two parallel requests both read 24 and both insert |
| Cap only ACTIVE alerts | A customer never feels the cap | Cancelled and fired rows accumulate without bound |
| A database trigger or constraint | Strongest | A trigger to write and test; the single statement is enough |

## Decision

First option, `app.alerts.max-per-account` (25). Removing a finished alert is allowed, and removing one whose
hand-over is unresolved is refused (see 0008). The consumer's work per quote is a hash lookup on the symbol
(the per-symbol index) plus work proportional to alerts on THAT symbol, which the cap bounds per account.

## Consequences

An account near the cap sees `409 ALR-409` with a message saying what to do. The cap is a single property and
counts rows, so changing it is configuration. Quote-path cost grows with distinct customers watching one
symbol, which is the intended scaling axis.
