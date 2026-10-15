#!/usr/bin/env bash
# Replays one trade event, with the SAME eventId, several times, to show that it is handled once.
#
#   scripts/replay-trade-event.sh <accountId> [BUY|SELL] [times]
#
# Publishes an ORDER_FILLED event to the trade-events topic of the local compose stack, the way the executor does
# (key = accountId, source = trade-executor). It is a demonstration aid: a real order is the real demonstration.
# Afterwards, in Postgres:
#   SELECT count(*), max(attempts) FROM notifications WHERE event_id = '<printed id>';      -- 1 row, attempts 1
#   SELECT count(*) FROM portfolio_realised_pnl WHERE event_id = '<printed id>';            -- 1 for a SELL, 0 for a BUY
set -euo pipefail

. "$(dirname "${BASH_SOURCE[0]}")/../../../scripts/load-env.sh"
require_env KAFKA_HOST KAFKA_PORT TRADE_EVENTS_TOPIC

ACCOUNT_ID="${1:?usage: replay-trade-event.sh <accountId> [BUY|SELL] [times]}"
SIDE="${2:-BUY}"
TIMES="${3:-3}"
SYMBOL="${SYMBOL:-AAPL}"

if command -v uuidgen >/dev/null 2>&1; then
  EVENT_ID="${EVENT_ID:-$(uuidgen | tr 'A-Z' 'a-z')}"
else
  EVENT_ID="${EVENT_ID:-$(cat /proc/sys/kernel/random/uuid)}"
fi
NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

EVENT=$(cat <<JSON
{"eventId":"${EVENT_ID}","eventType":"ORDER_FILLED","eventTime":"${NOW}","source":"trade-executor","schemaVersion":1,"payload":{"orderId":"$(cat /proc/sys/kernel/random/uuid 2>/dev/null || echo 6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e)","accountId":${ACCOUNT_ID},"symbol":"${SYMBOL}","side":"${SIDE}","quantity":1,"price":1000.00,"executedPrice":100.00,"status":"FILLED","reason":null,"cashDelta":100.00,"positionQuantityAfter":1,"averageCostAfter":90.00,"executedOn":"${NOW}"}}
JSON
)

echo "eventId=${EVENT_ID}  account=${ACCOUNT_ID}  ${SIDE} 1 ${SYMBOL}  sending ${TIMES} times"
for _ in $(seq 1 "${TIMES}"); do
  echo "${ACCOUNT_ID}:${EVENT}"
done | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
      --bootstrap-server "${KAFKA_HOST}:${KAFKA_PORT}" --topic "${TRADE_EVENTS_TOPIC}" \
      --property parse.key=true --property key.separator=:

echo "done. Look for exactly one notification with event_id = ${EVENT_ID}"
