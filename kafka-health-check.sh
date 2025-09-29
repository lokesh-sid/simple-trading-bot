#!/bin/bash

# Kafka Monitoring and Health Check Script
# This script provides monitoring capabilities for the Kafka cluster

set -e

KAFKA_HOME=${KAFKA_HOME:-/opt/kafka}
BOOTSTRAP_SERVERS=${BOOTSTRAP_SERVERS:-localhost:9092}

echo "=== Kafka Cluster Health Check ==="

# Check if Kafka is running
echo "1. Checking Kafka connectivity..."
if $KAFKA_HOME/bin/kafka-broker-api-versions.sh --bootstrap-server $BOOTSTRAP_SERVERS > /dev/null 2>&1; then
    echo "✅ Kafka cluster is reachable"
else
    echo "❌ Kafka cluster is not reachable"
    exit 1
fi

# List brokers
echo -e "\n2. Active brokers:"
$KAFKA_HOME/bin/kafka-broker-api-versions.sh --bootstrap-server $BOOTSTRAP_SERVERS | head -5

# Check topics
echo -e "\n3. Trading topics status:"
for topic in trading.signals trading.executions trading.risk trading.market-data trading.bot-status; do
    if $KAFKA_HOME/bin/kafka-topics.sh --list --bootstrap-server $BOOTSTRAP_SERVERS | grep -q "^$topic$"; then
        echo "✅ $topic exists"
        
        # Get topic details
        partitions=$($KAFKA_HOME/bin/kafka-topics.sh --describe --bootstrap-server $BOOTSTRAP_SERVERS --topic $topic | grep "PartitionCount" | awk '{print $4}')
        replication=$($KAFKA_HOME/bin/kafka-topics.sh --describe --bootstrap-server $BOOTSTRAP_SERVERS --topic $topic | grep "ReplicationFactor" | awk '{print $6}')
        echo "   Partitions: $partitions, Replication: $replication"
    else
        echo "❌ $topic does not exist"
    fi
done

# Check consumer groups
echo -e "\n4. Consumer groups status:"
consumer_groups=("trading-bot-signals" "trading-bot-executions" "trading-bot-risk" "trading-bot-market-data" "trading-bot-status")
for group in "${consumer_groups[@]}"; do
    if $KAFKA_HOME/bin/kafka-consumer-groups.sh --bootstrap-server $BOOTSTRAP_SERVERS --list | grep -q "^$group$"; then
        echo "✅ Consumer group: $group"
        
        # Show consumer group details
        echo "   Consumer group details:"
        $KAFKA_HOME/bin/kafka-consumer-groups.sh --bootstrap-server $BOOTSTRAP_SERVERS --describe --group $group | head -5
    else
        echo "ℹ️  Consumer group not found: $group (will be created when consumers start)"
    fi
done

# Check topic message counts (last 10 minutes)
echo -e "\n5. Recent message activity:"
for topic in trading.signals trading.executions trading.risk trading.market-data trading.bot-status; do
    if $KAFKA_HOME/bin/kafka-topics.sh --list --bootstrap-server $BOOTSTRAP_SERVERS | grep -q "^$topic$"; then
        # Get end offsets for all partitions
        offsets=$($KAFKA_HOME/bin/kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list $BOOTSTRAP_SERVERS --topic $topic --time -1 | awk -F: '{sum+=$3} END {print sum}')
        echo "📊 $topic: $offsets total messages"
    fi
done

# Performance metrics
echo -e "\n6. Performance metrics:"
echo "🔍 To see real-time metrics, run:"
echo "   $KAFKA_HOME/bin/kafka-consumer-perf-test.sh --bootstrap-server $BOOTSTRAP_SERVERS --topic trading.signals --messages 1000"
echo "   $KAFKA_HOME/bin/kafka-producer-perf-test.sh --topic trading.signals --num-records 1000 --record-size 1024 --throughput 100 --producer-props bootstrap.servers=$BOOTSTRAP_SERVERS"

echo -e "\n=== Health Check Complete ==="