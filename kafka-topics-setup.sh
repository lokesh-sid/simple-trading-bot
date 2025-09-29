#!/bin/bash

# Kafka Topics Setup Script for Trading Bot
# This script creates the necessary Kafka topics with appropriate configurations

set -e

KAFKA_HOME=${KAFKA_HOME:-/opt/kafka}
BOOTSTRAP_SERVERS=${BOOTSTRAP_SERVERS:-localhost:9092}
REPLICATION_FACTOR=${REPLICATION_FACTOR:-1}

echo "Creating Kafka topics for Trading Bot..."

# Trade Signals Topic - Medium throughput, bot-based partitioning
$KAFKA_HOME/bin/kafka-topics.sh --create \
  --bootstrap-server $BOOTSTRAP_SERVERS \
  --topic trading.signals \
  --partitions 6 \
  --replication-factor $REPLICATION_FACTOR \
  --config retention.ms=604800000 \
  --config cleanup.policy=delete \
  --if-not-exists

# Trade Executions Topic - Medium throughput, bot-based partitioning
$KAFKA_HOME/bin/kafka-topics.sh --create \
  --bootstrap-server $BOOTSTRAP_SERVERS \
  --topic trading.executions \
  --partitions 6 \
  --replication-factor $REPLICATION_FACTOR \
  --config retention.ms=2592000000 \
  --config cleanup.policy=delete \
  --if-not-exists

# Risk Events Topic - Low throughput but critical, bot-based partitioning
$KAFKA_HOME/bin/kafka-topics.sh --create \
  --bootstrap-server $BOOTSTRAP_SERVERS \
  --topic trading.risk \
  --partitions 3 \
  --replication-factor $REPLICATION_FACTOR \
  --config retention.ms=2592000000 \
  --config cleanup.policy=delete \
  --if-not-exists

# Market Data Topic - High throughput, symbol-based partitioning
$KAFKA_HOME/bin/kafka-topics.sh --create \
  --bootstrap-server $BOOTSTRAP_SERVERS \
  --topic trading.market-data \
  --partitions 12 \
  --replication-factor $REPLICATION_FACTOR \
  --config retention.ms=86400000 \
  --config cleanup.policy=delete \
  --if-not-exists

# Bot Status Topic - Low throughput, bot-based partitioning
$KAFKA_HOME/bin/kafka-topics.sh --create \
  --bootstrap-server $BOOTSTRAP_SERVERS \
  --topic trading.bot-status \
  --partitions 3 \
  --replication-factor $REPLICATION_FACTOR \
  --config retention.ms=604800000 \
  --config cleanup.policy=delete \
  --if-not-exists

echo "All Kafka topics created successfully!"

# List created topics
echo "Listing created topics:"
$KAFKA_HOME/bin/kafka-topics.sh --list --bootstrap-server $BOOTSTRAP_SERVERS | grep "trading\."

echo "Topic configurations:"
for topic in trading.signals trading.executions trading.risk trading.market-data trading.bot-status; do
  echo "--- $topic ---"
  $KAFKA_HOME/bin/kafka-topics.sh --describe --bootstrap-server $BOOTSTRAP_SERVERS --topic $topic
done