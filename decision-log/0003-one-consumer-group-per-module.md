# 0003 One consumer group per module, and one listener on market-data

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Team 2, day one (drafted by Claude from the brief; the team to confirm) |

## Context

`contracts/kafka-topics.md` fixes `notification-service` and `portfolio-service` on `trade-events` and
`watchlist-service` on `market-data`. All run in one process. The Trade REST API already had a quote-cache
consumer on `market-data` in a group called `trade-api-market-data`. Kafka assigns partitions within a group:
two listeners in one group split the partitions and each sees only part of the stream.

## Options considered

| Option | For | Against |
|---|---|---|
| A group per module, and exactly one `@KafkaListener` per (topic, group); other code subscribes through a `QuoteObserver` list | Offsets are independent; a redeployed notifications consumer cannot move the alert consumer's position; no accidental partition split | One more indirection for the alert evaluator |
| A second `market-data` listener for the alerts, in the same group as the cache | No indirection | Two consumers in one group split the stream: alerts would see about half of the quotes |
| A second listener in its own new group for alerts, leaving the cache group alone | No change to existing code | Two groups on the same topic doing the same work; the alert group is not the contract's `watchlist-service` |

## Decision

Groups are `watchlist-service`, `notification-service`, `portfolio-service`, set explicitly (never
`spring.kafka.consumer.group-id`, which is deliberately left unset) and overridable by environment for tests.
The existing quote-cache listener becomes the `watchlist-service` listener and passes each quote to every
`QuoteObserver` (the alert evaluator is one). `KafkaChainIT` (it needs a database, so it is not in the default `mvn test`) fails if a fourth listener appears or two share a group.

## Consequences

Scaling the Trade REST API past three instances adds nothing for `trade-events` (three partitions) and past six
for `market-data`. A rebalance pauses a group briefly, so all three consumers are idempotent on the event id (see 0006,
0007, 0010). The group ids are listed in `services/trade-api/README.md` because another consumer adopting one of
them would silently take half its messages. `notification-service` starts from `latest` (an empty group must not
re-send 30 days of old fills to customers); `portfolio-service` starts from `earliest` (it only books, idempotently).
