# Automated Trading

> **Automation executes configured rules through the existing Trade REST API and must remain subject to the platform's authentication, authorization, suitability and risk controls.**

## Purpose

A customer sets up a rule in the app (**Automation** page), switches it on, and from then on it places orders for them when the rule triggers. Each order is a `POST /api/v1/orders` on the Trade REST API, exactly as a browser would send it. Automation never writes to the orders table, never calls `OrderService` directly and never publishes to the execution topic itself, so order validation, authorisation, Kafka publishing and the executor stay the single source of truth.

## The flow

```
Automation page ──> /api/v1/automation/strategies  (create: OFF)
                    /api/v1/automation/strategies/{id}/enabled  (switch ON: needs acknowledged=true)
                          │ stored in automation_strategies, then AutomationService.reload()
                          ▼
market-data topic ──> MarketDataConsumer ──> QuoteObserver = AutomationService
                                                  │  PriceHistory (bounded, per symbol)
                                                  ▼
                                   TradingStrategy (MA crossover | Bollinger)
                                                  │  BUY | SELL | NONE
                                                  ▼
                                 order request ──> RestTradeApiClient ──> POST /api/v1/orders (existing)
                                                  ▼
                                    existing validation ──> Kafka ──> Executor
```

## Using it

1. **Run the migration** `databases/postgreSQL/migrations/005_automation.sql` once.
2. Open **Automation**, press **New strategy**, pick the rule, instrument, quantity and parameters. It is created **off**.
3. Press the switch. A confirmation says the strategy will place **real orders**; the **Switch on** button stays disabled until the box is ticked.
4. The strategy collects live prices first ("Collecting prices: 12 of 51"). It cannot act until it has them all. History starts empty and fills from live quotes, so a long average takes a while; quotes must be arriving on `market-data`.
5. The page shows what it did: Triggered, Order sent, Rejected, Failed.
6. Switch it off, or remove it, at any time. An order already sent cannot be taken back.

## API (all under `/api/v1/automation`, customer tokens only)

| Request | What |
|---|---|
| `GET /` | The caller's strategies (with price-collection progress) and recent results |
| `POST /strategies` | Create, always **off**. Validates account, instrument, quantity, periods. Cap `app.automation.max-per-account` (10) |
| `PUT /strategies/{id}/enabled` | `{ "enabled": true, "acknowledged": true }`. Switching on without `acknowledged` is 422 `AUT-422` |
| `DELETE /strategies/{id}` | Remove |

Only the caller's own accounts are visible or changeable (another customer's strategy is 404 `AUT-404`; an account that is not yours is 403). An administrator cannot manage strategies.

## Seeing it work

See `docs/automation-demo.md` for the walkthrough, including the **demo button** (`app.automation.simulation-enabled`, env `AUTOMATION_SIMULATION`, off by default) that makes a running strategy trigger now from a scripted price path. The order it places is real; the path is never added to the shared price history, so it cannot trigger another customer's strategy. The page also charts the prices a strategy has collected with its rule lines.

## Strategies

Each is stateless: a trigger is a **transition between the last two observations**.

### `MA_CROSSOVER`

| Trigger | Condition |
|---|---|
| BUY | previous short MA ≤ previous long MA **and** current short MA > current long MA |
| SELL | previous short MA ≥ previous long MA **and** current short MA < current long MA |

"Short MA above long MA" alone triggers nothing. Needs `longPeriod + 1` observations.

### `BOLLINGER_BANDS`

`middle` = moving average over `period`; `upper` = middle + `multiplier` × σ; `lower` = middle − `multiplier` × σ (population σ).

| Trigger | Condition |
|---|---|
| BUY | price crosses **below** the lower band: previous price ≥ previous lower band and price < lower band |
| SELL | price crosses **above** the upper band: previous price ≤ previous upper band and price > upper band |

While the price stays outside the band, later ticks trigger nothing. Needs `period + 1` observations.

To add a strategy: implement `TradingStrategy`, add a `StrategyType` and a case in `StrategyFactory`.

## Configuration (`app.automation.*`)

```
app.automation.enabled=true            # AUTOMATION_ENABLED. The platform KILL SWITCH: false and nothing trades,
                                       #   whatever customers have switched on, and nothing can be switched on.
app.automation.trade-api-base-url=...  # AUTOMATION_TRADE_API_URL, defaults to this service
app.automation.connect-timeout-ms=2000
app.automation.read-timeout-ms=4000
app.automation.history-size=500        # prices kept per symbol
app.automation.max-per-account=10
app.automation.reload-ms=30000         # how often running strategies are re-read from the database
```

## Authentication

There is no login token to paste. `AutomationTokenIssuer` signs a **one-minute** token for the strategy's own account (role `CUSTOMER`, same secret and issuer the Trade API verifies), and only for an account whose owner has a strategy switched on. The Trade API checks it like any other, including account ownership and the rule that admins cannot place orders. Anyone who can read the JWT secret can already mint any token; this adds no new secret.

## Safety

- A strategy is created off and only trades after an explicit, acknowledged switch-on. A bad or invalid strategy cannot be switched on and is skipped.
- **One attempt per trigger.** A failure is logged and recorded, never retried, so an unknown outcome cannot become two orders.
- The idempotency key is derived from `strategy + symbol + side + quote time`, so the same trigger can never be accepted twice by the platform.
- A quote that is not newer than the last one stored for the symbol is not a new observation, so a quote delivered twice is one tick.
- Malformed events (no symbol, no or non-positive price) are ignored with a warning.
- If the database cannot be read on a reload, the strategies already running keep running.
- History is bounded and kept only for symbols that have a running strategy.

## Outcomes

| Outcome | Meaning |
|---|---|
| `STRATEGY_TRIGGERED` | the rule fired; no trade yet |
| `ORDER_SUBMITTED` | the Trade API accepted the order (the executor still decides if it fills) |
| `ORDER_REJECTED` | the Trade API said no (4xx) |
| `ORDER_SUBMISSION_FAILED` | unreachable, timed out or 5xx: the outcome is unknown |

Logs: `[AUTOMATION] event=... strategy=... symbol=... accountId=... trigger=... orderId=... at=...` for every trigger, submission attempt, success and failure (evaluations at debug).

## Assumptions and limits

- The order `price` is the quote price, sent as the limit price; the Trade API converts it for the instrument. Use accounts whose currency matches the instrument.
- Recent results are kept in memory (last 200) and are lost on restart. Orders themselves are in the normal order history.
- Strategies run in the trade-api process; orders are submitted on one dedicated thread.
- Automation is per customer account; there is no approval workflow or per-day loss limit yet. Suitability and risk limits are whatever the order validator enforces.

## Run and test

```
mvn spring-boot:run
mvn test -Dtest='Automation*Test,RestTradeApiClientTest,StrategiesTest'
```
