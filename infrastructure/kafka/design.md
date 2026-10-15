# Kafka Design Decisions

## 1. Topic Keys

| Topic          | Key         | Reason                                                   |
| -------------- | ----------- | -------------------------------------------------------- |
| `orders`       | `accountId` | Preserve order execution sequence for each account       |
| `trade-events` | `accountId` | Preserve order lifecycle event ordering for each account |
| `market-data`  | `symbol`    | Preserve quote ordering for each instrument              |

Kafka uses the message key to determine the partition. Messages with the same key are routed to the same partition, which allows Kafka to preserve their order within that partition.

For `orders` and `trade-events`, the key is `accountId` because the order of events for an individual account matters.

For `market-data`, the key is `symbol` because quotes for the same instrument must remain ordered.

---

## 2. Key, Partition, and Ordering

Kafka guarantees ordering **within a partition**. It does not guarantee ordering across different partitions.

The message key determines which partition receives the message. Therefore, using the same key for related messages ensures that those messages are placed in the same partition and can be processed in order.

For example, if account `101` places two orders:

```text
Order A → accountId=101
Order B → accountId=101
```

both messages use `101` as their Kafka key. They are therefore routed to the same partition, where Kafka preserves their order:

```text
Order A → Order B
```

Orders belonging to different accounts can be placed in different partitions and processed in parallel.

The same principle applies to `trade-events`. Lifecycle events for an account remain ordered relative to each other.

For `market-data`, the key is the instrument symbol. This keeps quotes for the same symbol ordered so that an older quote does not appear after a newer quote for that symbol.

---

## 3. Why We Do Not Key Orders by `orderId`

Orders are **not** keyed by `orderId`.

If every order used its own `orderId` as the Kafka key, different orders belonging to the same account could be assigned to different partitions.

For example:

```text
Order A
accountId = 101
orderId   = A

Order B
accountId = 101
orderId   = B
```

If the keys are the order IDs:

```text
A → Partition 0
B → Partition 2
```

Kafka only guarantees ordering inside each partition. Therefore, Kafka could not guarantee that Order A is processed before Order B.

Using `accountId` as the key instead gives:

```text
accountId = 101
        ↓
same Kafka partition
        ↓
ordered processing
```

This is why `accountId` is the correct key for both `orders` and `trade-events`.

---

## 4. Partition Counts

The partition counts are based on the Kafka topic catalogue.

| Topic          | Partitions | Reason                                                                                      |
| -------------- | ---------: | ------------------------------------------------------------------------------------------- |
| `orders`       |          3 | Allows three executor instances in one consumer group and demonstrates consumer rebalancing |
| `trade-events` |          3 | Matches the account-based ordering requirement while allowing parallel processing           |
| `market-data`  |          6 | Market data has a higher message rate and benefits from additional parallelism              |

The `orders` topic is a work queue with one logical consumer group. Three partitions allow up to three executor instances in that consumer group to process work in parallel.

The `trade-events` topic also has three partitions. Since it is keyed by `accountId`, events for the same account remain ordered while events for different accounts can be processed concurrently.

`market-data` has six partitions because market data is expected to have a higher message rate than orders and trade events.

Partitions can be increased later, but they cannot be decreased.

Increasing the number of partitions also changes how keys are assigned to partitions. As a result, an account's messages can be assigned to a different partition after the increase. This means an account's history can be split across partitions from that point onward.

Therefore, partition counts should be chosen deliberately.

---

## 5. Topic Creation

Kafka topic auto-creation is disabled.

Topics are created explicitly by:

```text
infra/kafka/create-topics.sh
```

The script creates the three main topics:

```text
orders
trade-events
market-data
```

and their corresponding dead-letter topics:

```text
orders.DLT
trade-events.DLT
market-data.DLT
```

The topic creation script can be rerun because it uses `--if-not-exists`.

The local Kafka broker is started using Docker Compose:

```text
docker-compose.yml
```

The creation process is therefore:

```text
Start Kafka
    ↓
docker-compose up -d
    ↓
Run topic creation script
    ↓
infra/kafka/create-topics.sh
    ↓
Topics are created explicitly
```

This avoids relying on automatic topic creation and makes the Kafka setup reproducible.

---

## 6. Dead-Letter Topics

Each main topic has a corresponding dead-letter topic:

| Main Topic     | Dead-Letter Topic  |
| -------------- | ------------------ |
| `orders`       | `orders.DLT`       |
| `trade-events` | `trade-events.DLT` |
| `market-data`  | `market-data.DLT`  |

The dead-letter topics are explicitly created rather than relying on Kafka's automatic topic creation.

Malformed, validation, or deserialization failures can be sent to the appropriate DLT.

Transient processing failures are retried with backoff. After the configured bounded number of retries, the message can be sent to the DLT.

The original message value and failure reason are included in the message headers when a message is sent to a DLT.

---

## 7. Message Envelope

Every Kafka message uses a common five-field envelope plus a `payload`.

The envelope contains:

| Field           | Type        | Purpose                                                                 |
| --------------- | ----------- | ----------------------------------------------------------------------- |
| `eventId`       | string UUID | Unique identifier for the message and consumer idempotency              |
| `eventType`     | string      | Identifies the type of event                                            |
| `eventTime`     | string      | RFC 3339 UTC timestamp representing when the producer created the event |
| `source`        | string      | Identifies the component that produced the event                        |
| `schemaVersion` | integer     | Identifies the version of the message schema                            |
| `payload`       | object      | Contains the event-specific data                                        |

A message therefore has this general structure:

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "ORDER_PLACED",
  "eventTime": "2026-09-15T12:00:00Z",
  "source": "trade-api",
  "schemaVersion": 1,
  "payload": {}
}
```

Messages are encoded as JSON using UTF-8.

Consumers must ignore fields that they do not recognize.

Adding optional fields is considered a non-breaking change and does not require a schema version increase.

Removing, renaming, or changing the type of an existing field is a breaking change and requires the `schemaVersion` to be increased.

---

## 8. `orders` Message

The `orders` topic contains accepted orders awaiting execution.

Its key is:

```text
accountId
```

The event type is:

```text
ORDER_PLACED
```

The payload contains:

```text
orderId
accountId
symbol
side
quantity
price
idempotencyKey
createdOn
```

Orders are published only after the database transaction has committed.

Publishing before the database transaction commits could produce an event for an order that is later rolled back.

Publishing after the commit means that a committed order could temporarily exist without its Kafka event. This is recoverable by replaying the order table.

---

## 9. `trade-events` Message

The `trade-events` topic contains order lifecycle outcomes.

Possible event types are:

```text
ORDER_FILLED
ORDER_REJECTED
ORDER_CANCELLED
```

Its key is:

```text
accountId
```

The payload contains:

```text
orderId
accountId
symbol
side
quantity
price
executedPrice
status
reason
cashDelta
positionQuantityAfter
averageCostAfter
executedOn
```

Rejected orders are also published as events.

Including the resulting account and position information in the event allows downstream consumers to build projections without having to query PostgreSQL for every event.

---

## 10. `market-data` Message

The `market-data` topic contains quotes produced by the market-data polling process.

Its key is:

```text
symbol
```

The event type is:

```text
QUOTE
```

The payload contains:

```text
symbol
price
bid
ask
currency
change
changePercent
previousClose
marketState
stale
quoteAsOf
```

One Kafka message is produced per symbol rather than one message for an entire batch.

`eventTime` represents the time the quote was published to Kafka.

`quoteAsOf` represents the time associated with the market observation itself.

---

## 11. Delivery Semantics and Idempotency

The platform uses **at-least-once delivery**.

This means messages can occasionally be delivered more than once.

Producers use retries and idempotent producer settings.

Consumers process the message before committing its Kafka offset.

Therefore, consumers must be able to safely handle duplicate messages.

The Trade Executor uses a guarded state transition:

```text
UPDATE orders
SET ...
WHERE id = ?
  AND status = 'NEW'
```

If the update affects zero rows, the order has already been handled or is no longer in the expected state.

This prevents duplicate Kafka delivery from executing the same order more than once.

The platform does not rely on Kafka exactly-once transactions for database side effects.

---

## 12. Producer Settings

Kafka producers should use:

```text
acks=all
enable.idempotence=true
```

A high retry count should be configured.

The number of in-flight requests per connection must not exceed:

```text
5
```

These settings support reliable at-least-once message delivery while preserving the required ordering behavior.

---

## 13. Consumer Processing

Consumers disable automatic offset commits.

The processing flow is:

```text
Receive message
      ↓
Process message
      ↓
Apply required side effects
      ↓
Commit Kafka offset
```

The offset is committed only after successful processing.

If processing fails before the offset is committed, Kafka can deliver the message again.

This is why consumers must use idempotent processing.

---

## 14. Ordering Guarantee

The Kafka ordering guarantee is limited to individual partitions.

The platform relies on this to provide:

```text
orders
    accountId
        ↓
    same partition
        ↓
    ordered processing
```

and:

```text
trade-events
    accountId
        ↓
    same partition
        ↓
    ordered lifecycle events
```

and:

```text
market-data
    symbol
        ↓
    same partition
        ↓
    ordered quotes
```

There is no ordering guarantee between different partitions.

There is also no global ordering guarantee across an entire Kafka topic.

---

