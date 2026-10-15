# notifications

Turns order outcomes (and fired price alerts) into messages a customer receives on the channel they chose, and keeps the
history. Package `com.capstone.etp.notifications`, inside the Trade REST API.

| | |
|---|---|
| Contract | `contracts/notifications-api.yaml` |
| Routes | `GET /api/v1/accounts/{id}/notifications[?unreadOnly&limit]`, `GET .../unread-count`, `POST .../{nid}/read` |
| Table | `notifications` (unique `event_id`) |
| Kafka | consumes `trade-events`, group **`notification-service`** (starts from `latest`); failures go to `trade-events.DLT` |
| Calls | `preferences.ChannelResolver`, on every send |
| Publishes | `AlertDelivery`, `AlertDeliveryRequest`, `DeliveryReceipt`, `AlertDeliveryException` |
| Called by | `alerts` (`AlertHandover`) |

## How a message flows

```
trade-events ──▶ TradeEventListener ──▶ notifications (QUEUED)  ◀── offset committed here
                                              │
                          NotificationDispatcher (every 2 s, claims due rows with SKIP LOCKED)
                                              │  ChannelResolver.resolve(account)   ← every send
                                              ▼
                             ChannelSender (EMAIL | SMS | PUSH)  ──▶  SENT / retry / FAILED / HELD
```

* **Consumption and delivery are separate.** The offset says a message was read; `status` says whether it reached the customer (decision 0006).
* **Idempotent on `eventId`.** A replayed event records nothing the second time and sends nothing twice.
* **The channel is resolved, not assumed**, and recorded on the notification (`resolved_channel`, `channel_source`). The destination is never stored.
* **Handles** `ORDER_FILLED`, `ORDER_REJECTED` and `ORDER_CANCELLED` (a rejection is news too). Note: the Trade REST API's own cancel route does not publish `ORDER_CANCELLED` today, so a customer-initiated cancel is notified only if some producer publishes it.
* **Nothing sensitive in a message.** Text is built from a fixed list of fields; `ContentGuard` refuses an address, a card-sized number, a credential word or a control character.
* **No caller-supplied destinations.** One sender per channel, fixed in code; the address comes from the customer's profile.
* **E-mail** is sent over SMTP when `SMTP_HOST` is set. Otherwise (and for SMS and push, which have no provider here) the channel logs a masked line and the notification is recorded `SENT` with reason `STUB_DELIVERY`. The routing decision is real either way.

## Statuses

`QUEUED` waiting (or retrying) · `HELD` no usable contact for the chosen channel (reason `NO_CONTACT`) · `SENT` · `FAILED` after 5 attempts.

## Access control

History routes call `ensureOwnAccount`. A notification id of another account is `404 NTF-404`. There is **no route that creates or sends** a message.
The listener has no caller and no token; it accepts only `trade-executor` / `trade-api` as the producer (decision 0009).

## Tests

`TradeEventListenerTest`, `NotificationDispatcherTest`, `NotificationServiceTest`, `NotificationAlertDeliveryTest`, `ContentGuardTest`;
`ExtensionsChainIT` (database, HTTP) and `KafkaChainIT` (embedded Kafka: replay, offsets, dead letter).
