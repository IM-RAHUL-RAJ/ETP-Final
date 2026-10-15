# Demonstration script

For story 809 and 812. Everything here runs against the live stack: real quotes from Fauxnance via the executor's
poller, real messages on Kafka, real rows in Postgres. Nothing below is a fixture.

## Before you start

1. `docker compose --profile platform up -d --build` (set `JWT_SECRET`, `FAUXNANCE_API_KEY`, and `SMTP_*` if you want a real e-mail; without `SMTP_HOST` e-mail records `STUB_DELIVERY`).
2. A database that already has data needs the extension tables once:
   `psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f databases/postgreSQL/migrations/003_extensions.sql`
   (a fresh volume gets it automatically).
   Then `psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f databases/postgreSQL/migrations/004_sprint10_ui_enhancements.sql` (alert operators, trigger history, mascot, funds, value history; safe to run twice).
3. Create the topics if they do not exist (`infrastructure/kafka/scripts/create-topics.sh` runs as `kafka-init`).
4. `cd frontend && npm start`, sign in as a customer.

## 1. The chain, end to end

1. **Settings.** Choose a channel (say SMS or E-mail). It is stored per customer; the page shows only a masked contact detail.
2. **Place an order** on the order ticket (a real limit order). The executor fills or rejects it and publishes to `trade-events`.
3. **Inbox.** A notification appears, with the order outcome and *the channel it was resolved to* (the record, not a claim).
   Check it in the database too:
   `SELECT id, kind, status, resolved_channel, channel_source, attempts FROM notifications ORDER BY id DESC LIMIT 5;`
4. **Change the channel** in Settings, place another order: the new notification shows the new channel.
5. **A rejection is news.** Place an order the account cannot afford: the inbox says it was rejected and why.

## 2. A price alert, delivered through notifications

1. Watchlist, "Set a price alert" on a card (or the Alerts page). Pick a level a little beyond the current price.
2. When the poller publishes a quote across the level, the alert goes `TRIGGERED` and `HANDED_OVER` and a `PRICE_ALERT`
   message appears in the Inbox on the channel from step 1.
3. To force it without waiting for the market, publish a quote the same way the poller does (see "Replaying a message").
4. It fires once: keep publishing quotes past the level and no second message appears.

## 3. A replayed event produces one message, not two

```
services/trade-api/scripts/replay-trade-event.sh 1
```

It sends the **same** `ORDER_FILLED` event (same `eventId`) three times to `trade-events`. Then:

```sql
SELECT count(*) FROM notifications WHERE kind = 'ORDER_FILLED' AND event_id = '<the id the script printed>';  -- 1
SELECT attempts, status, resolved_channel FROM notifications WHERE event_id = '<id>';                         -- attempts = 1
SELECT count(*) FROM portfolio_realised_pnl WHERE event_id = '<id>';                                          -- 1 for a SELL
```

Open Kafbat UI (`http://localhost:8086`) to show the three records on the topic and the consumer group's offset past them.
(For an automated version run `KafkaChainIT`, see the README.)

## 4. A portfolio figure traced to a live quote

1. Portfolio page: each holding shows its last price and *when that price was observed*, with a Live or Delayed marker.
2. `GET /api/v1/portfolio/{id}/positions`: `lastPrice`, `priceAsOf`, `stale` per position.
3. The same symbol's price in the executor's `market-data` records (Kafbat UI) or in Fauxnance directly: they agree.
4. Realised P&L: sell some of a holding, and the Profit and loss panel shows the sale booked once; refresh the page and the realised figure does not move with the price.
5. **Degraded paths** (stop the executor or revoke the key to show them): some symbols unpriced gives a partial banner; none priced gives the "Prices are not available right now" page with holdings at cost, and order placement still works.

## Replaying a message by hand

```
# a quote, as the poller publishes it (key = symbol)
echo 'AAPL:{"eventId":"'$(uuidgen)'","eventType":"QUOTE","eventTime":"'$(date -u +%FT%TZ)'","source":"market-poller","schemaVersion":1,"payload":{"symbol":"AAPL","price":999.00,"bid":998.9,"ask":999.1,"currency":"USD","stale":false,"quoteAsOf":"'$(date -u +%FT%TZ)'"}}' \
 | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:29092 --topic market-data --property parse.key=true --property key.separator=:
```

(Publishing a made-up quote is a demonstration aid for triggering an alert on demand; say so when you do it. It is not
a substitute for the live run in section 2.)

## What to be ready to explain

* Why the offset is committed when the notification is *recorded*, not when a provider answers (decision 0006).
* What notifications does when no preference has been stored (0004), and when the chosen channel has no contact (0013).
* Why an alert fires once (0007) and what the customer sees if its hand-over fails (0008).
* Where the authorisation boundary sits for a consumer (0009) and why the two seams are not routes (0001).
* Why realised P&L is booked from sale events and not from today's price (0010), and the currency trap in the event.


## UI enhancements walkthrough

1. **Shell.** Collapse and expand the sidebar with the chevron (no label) (it remembers); hover an icon for its name; resize under 800px for the drawer. The gear opens Settings; the avatar opens Profile and Sign out.
2. **Settings.** Pick Dark, switch a notification kind off, pick the rabbit: each saves with a toast. Reload: it is all still there.
3. **Alerts.** Create "If price of AAPL is >= than 235". Switch it off, edit it, delete it. After it fires, open its history (price, time, delivery state), switch it back on: it fires again and the history has two rows.
4. **Inbox.** Use the Price Alerts / Orders / Funds tabs and their counts. An alert on `==` reads "AAPL equals 123.45".
5. **Profile.** Add 250 and withdraw 100: the balance moves, two Funds messages arrive, the history has two rows. Try withdrawing more than the balance.
6. **Watchlist.** Click an instrument: candles with 1D/4H/1H/15M. Disconnect the provider: an error with a retry, not a made-up chart.
7. **Portfolio.** Filter to Crypto, sort by Gain/Loss, hover a price dot for its time. Download the PDF with the charts: the file says which filter applied.
8. **E-mail.** With `SMTP_HOST` set, an order fill arrives as HTML with the chosen companion. `PUBLIC_BASE_URL` must be the address the web app is served from, because the mascot images are loaded from it.
