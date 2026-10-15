# alerts (watchlists and price alerts)

Price alerts evaluated against the `market-data` stream and delivered through notifications. Package `com.capstone.etp.alerts`,
inside the Trade REST API.

| | |
|---|---|
| Contract | `contracts/alerts-api.yaml` |
| Routes | `GET, POST /api/v1/accounts/{id}/alerts`, `DELETE .../{alertId}` |
| Watchlist | the account's existing watchlist routes (`/watchlist`, `/watchlist/quotes`); an entry is a reference to an instrument, not a position |
| Table | `price_alerts` |
| Kafka | consumes `market-data`, group **`watchlist-service`** (starts from `latest`), through the shared `MarketDataConsumer` and the `QuoteObserver` seam |
| Calls | `notifications.AlertDelivery` (never asks for a channel) |

## Behaviour

* **Crossed:** ABOVE fires on the first non-stale quote at or above the level; BELOW at or below. An alert whose level has already been crossed is refused at creation.
* **Fires once** and deactivates: `ACTIVE` to `TRIGGERED` in a guarded update (decision 0007).
* **Per-symbol index:** active alerts are held in memory by symbol, so a quote nobody cares about costs one hash lookup. It is a cache of the table, rebuilt at start and every 30 s.
* **Delivery:** a fired alert is handed to `AlertDelivery`. If that raises, the alert stays `TRIGGERED` with `HANDOVER_FAILED`, is retried every 30 s up to 5 times, and the customer sees it (decision 0008).
* **Cap:** 25 alerts per account, enforced inside the insert statement (decision 0012). Customers remove finished alerts to make room.

## Access control

Every route calls `ensureOwnAccount`; queries are keyed on the account from the path. Another account's alert id is `404 ALR-404`. The evaluator has
no token: it acts on a quote and on alerts created through an authorised route.

## Find problems

```sql
SELECT id, account_id, symbol, delivery_state, delivery_attempts FROM price_alerts
 WHERE status = 'TRIGGERED' AND delivery_state <> 'HANDED_OVER';
```

## Tests

`AlertEvaluatorTest`, `AlertServiceTest`, `AlertIndexTest`, `AlertControllerTest`; `ExtensionsChainIT`, `KafkaChainIT.aQuoteOnMarketDataTriggersAnAlert...`.
