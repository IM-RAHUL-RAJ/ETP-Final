#!/bin/bash

# The broker address comes from KAFKA_BOOTSTRAP (KAFKA_HOST:KAFKA_PORT in .env, passed in by docker-compose).
BOOTSTRAP="${KAFKA_BOOTSTRAP:?KAFKA_BOOTSTRAP is not set}"

echo "Waiting for Kafka to become available..."

until /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP" \
  --list > /dev/null 2>&1
do
  sleep 2
done

echo "Kafka is ready."
echo "Creating topics..."

# Create orders topic
/opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP" \
  --create \
  --if-not-exists \
  --topic orders \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000

# Create trade-events topic
/opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP" \
  --create \
  --if-not-exists \
  --topic trade-events \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=2592000000

# Create market-data topic
/opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP" \
  --create \
  --if-not-exists \
  --topic market-data \
  --partitions 6 \
  --replication-factor 1 \
  --config retention.ms=86400000

# Create orders dead-letter topic
/opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP" \
  --create \
  --if-not-exists \
  --topic orders.DLT \
  --partitions 1 \
  --replication-factor 1 \
  --config retention.ms=604800000

# Create trade-events dead-letter topic
/opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP" \
  --create \
  --if-not-exists \
  --topic trade-events.DLT \
  --partitions 1 \
  --replication-factor 1 \
  --config retention.ms=2592000000

# Create market-data dead-letter topic
/opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP" \
  --create \
  --if-not-exists \
  --topic market-data.DLT \
  --partitions 1 \
  --replication-factor 1 \
  --config retention.ms=86400000

echo ""
echo "Kafka topics created successfully."
echo ""
echo "Available topics:"

/opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP" \
  --list



