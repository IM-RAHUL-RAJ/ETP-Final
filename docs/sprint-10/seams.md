# The two seams

Story 804. Both are Java interfaces called inside the Trade REST API process. Neither is an HTTP route: publishing
either as a route would let a customer's token resolve someone else's channel or send itself an arbitrary message.

```
preferences ──ChannelResolver──▶ notifications ◀──AlertDelivery── alerts
 (publishes)                      (calls the first,                (calls the second,
                                   publishes the second)            never asks for a channel)
```

## Seam 1: notifications resolves a channel through preferences

`com.capstone.etp.preferences.ChannelResolver`

```java
public interface ChannelResolver {
    Optional<ResolvedChannel> resolve(long accountId);
}

public record ResolvedChannel(AlertChannel channel, String destination, ChannelSource source) { }
public enum AlertChannel { EMAIL, SMS, PUSH }
public enum ChannelSource { STORED, DEFAULT }
public class PreferencesUnavailableException extends RuntimeException { }
```

Called by `NotificationDispatcher` **on every send**: nothing is cached or copied at start-up.

| The account | `resolve` answers | Notifications does |
|---|---|---|
| has a stored preference the profile can serve | `STORED` channel and its destination | sends on it |
| has none stored | `DEFAULT`: EMAIL to the profile address, else PUSH (in-app) | sends on it, records `channelSource = DEFAULT` |
| chose a channel the profile cannot serve (SMS, no phone) | `Optional.empty()` | HELD, reason `NO_CONTACT`, retried every 5 min, **never another channel** |
| does not exist | `Optional.empty()` | same |
| (the store cannot be read) | throws `PreferencesUnavailableException` | stays QUEUED, reason `PREFERENCES_UNAVAILABLE`, retried with backoff |

It never returns an error for "nothing stored". It does not return a destination for display: the settings API
returns it masked, and `ResolvedChannel.toString()` never prints it.

**Only caller:** `NotificationDispatcher`. **Only implementer:** `PreferencesService`.

## Seam 2: alerts hands a fired alert to notifications

`com.capstone.etp.notifications.AlertDelivery`

```java
public interface AlertDelivery {
    DeliveryReceipt deliver(AlertDeliveryRequest request) throws AlertDeliveryException;
}

public record AlertDeliveryRequest(long accountId, long alertId, String symbol, Direction direction,
                                   BigDecimal threshold, BigDecimal price, Instant observedAt) { }
public record DeliveryReceipt(long notificationId, boolean duplicate) { }
public class AlertDeliveryException extends RuntimeException { }
```

There is **no** channel, address or URL on the request: alerts never decides how a customer is reached.

| Call | Notifications answers | Alerts does |
|---|---|---|
| first call for an alert | records a `PRICE_ALERT` notification as QUEUED, receipt `duplicate = false` | marks the alert `HANDED_OVER` |
| the same `alertId` again (a retry, a restart between "fired" and "handed over") | the original receipt, `duplicate = true`, nothing new recorded | marks `HANDED_OVER` |
| cannot record it (database down, content refused) | throws `AlertDeliveryException` | marks `HANDOVER_FAILED`, keeps the alert `TRIGGERED`, `AlertHandoverRetrier` calls again (up to 5), the page says it was not yet handed over |

"Returns" means durably recorded for delivery, not accepted by a provider. After that the notification's own
status (QUEUED, SENT, HELD, FAILED) says whether it reached the customer.

**Only caller:** `AlertHandover`. **Only implementer:** `NotificationAlertDelivery`.

## A third, smaller seam

`com.capstone.etp.kafka.consumer.QuoteObserver` is how anything reacts to a `market-data` quote without opening a
second consumer. The `watchlist-service` listener passes every quote to every observer; the alert evaluator is the
only one. An observer must not throw; one that does is logged and the others still run.

## Consumer groups

| Group | Topic | Module | Starts from | Why |
|---|---|---|---|---|
| `watchlist-service` | `market-data` | alerts (and the quote cache) | latest | a stale quote is worthless |
| `notification-service` | `trade-events` | notifications | latest | an empty group must not re-send 30 days of old fills |
| `portfolio-service` | `trade-events` | portfolio | earliest | it only books, idempotently, so reading the retained history is safe and useful |

Groups are set explicitly on each listener and shared with nothing. Environment overrides exist for tests only
(`KAFKA_WATCHLIST_GROUP`, `KAFKA_NOTIFICATIONS_GROUP`, `KAFKA_PORTFOLIO_GROUP`). Failed messages go to `<topic>.DLT`.