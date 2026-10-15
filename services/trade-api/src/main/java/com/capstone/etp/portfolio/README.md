# portfolio (portfolio and P&L)

Priced holdings, cost basis and realised and unrealised profit and loss. Package `com.capstone.etp.portfolio`, inside the
Trade REST API. Independent of the other three modules.

| | |
|---|---|
| Contract | `contracts/portfolio-api.yaml` (binding: routes, field names, error codes) |
| Routes | `GET /api/v1/portfolio/{id}`, `.../positions[?symbol]`, `.../pnl[?from&to&bySymbol]`, and public `GET /health` |
| Table | `portfolio_realised_pnl` (the only table it writes) |
| Kafka | consumes `trade-events`, group **`portfolio-service`** (starts from `earliest`) |
| External | Fauxnance `GET /quotes?symbols=` (batch, max 25) and `GET /usage`, server side only; key from `FAUXNANCE_API_KEY` |
| Reads | positions, cash and instrument currencies **through** `AccountService` / `AccountMapper`; it never writes to the trading tables |

## Definitions (from the contract)

cost basis = quantity x average cost · market value = quantity x last price · unrealised = market value - cost basis (never stored) ·
realised = `(fillPrice - averageCostAtSale) x quantitySold`, **booked at the sale, never recomputed** · total value = cash + market value.

## Pricing

Batched (one call per 25 symbols), cached for 60 s, a 15 s cooldown after a failed call, then fall back to the last known price (expired cache entry,
then the last `market-data` quote), always marked `stale` (decision 0011). `stale` is true when the provider said so, the price is older than 15 minutes, or it was served because the
provider failed. Every priced figure carries `priceAsOf`.

* **Nothing priced** for an account that holds something: `503 MKT-503`. An account that holds nothing is not an outage.
* **Some priced:** `200`, unpriced positions have null prices and `stale: true`, and the summary has `partial: true` (unpriced holdings count toward `positionCount` and `costBasis` but not `marketValue`, `unrealisedPnl` or `totalValue`).
* **Currency:** totals are in the account's currency; each position keeps its own. A holding in a currency the platform cannot convert is left out of the totals and sets `partial`.

## Realised P&L

`TradeEventPnlListener` books each filled SELL from `trade-events`. Two facts about the executor's event that the booking depends on (decision 0010):
a sell leaves average cost unchanged, so `averageCostAfter` is the cost at the sale; and `executedPrice` is in the **account** currency while
`averageCostAfter` is in the **instrument** currency, so the cost is converted before subtracting. Idempotent on `eventId`.
Sales older than the topic's 30-day retention are not in the ledger.

## Access control

Every route calls `ensureOwnAccount`; a mismatch is `403 ACC-403` and is logged. The key never reaches the browser: the Angular app calls `/api/v1/portfolio/...` only.

## Tests

`PricingServiceTest`, `PortfolioServiceTest`, `RealisedPnlBookTest`, `TradeEventPnlListenerTest`, `FauxnanceQuoteClientTest` (a real local HTTP server),
`PortfolioControllerTest` (contract field names), `PortfolioHealthControllerTest`; `ExtensionsChainIT`, `KafkaChainIT`.
