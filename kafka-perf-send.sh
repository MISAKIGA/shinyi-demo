#!/bin/bash
#
# High-Performance Kafka Producer Test Script
# Sends raw strings efficiently using kafka-console-producer with optimized settings
#

set -e

TOPIC="${1:-test-raw}"
COUNT="${2:-100000}"
BOOTSTRAP_SERVER="localhost:9092"

echo "=== Kafka High-Performance Producer Test ==="
echo "Topic: $TOPIC"
echo "Messages: $COUNT"
echo "Bootstrap: $BOOTSTRAP_SERVER"
echo ""

# Generate messages efficiently using printf (faster than echo in loop)
start_time=$(date +%s%3N)

# Use multiple parallel producers for higher throughput
BATCH_SIZE=50000
PIDS=()

for ((offset=0; offset<COUNT; offset+=BATCH_SIZE)); do
    end=$((offset + BATCH_SIZE))
    if ((end > COUNT)); then end=$COUNT; fi

    # Generate batch and send in background
    (
        for ((i=offset; i<end; i++)); do
            printf "raw-message-%d\\n" "$i"
        done | docker exec -i kafka kafka-console-producer \
            --bootstrap-server "$BOOTSTRAP_SERVER" \
            --topic "$TOPIC" \
            --producer-property batch.size=16384 \
            --producer-property linger.ms=20 \
            --producer-property buffer.memory=67108864 \
            2>/dev/null
    ) &
    PIDS+=($!)
done

# Wait for all batches
for pid in "${PIDS[@]}"; do
    wait $pid 2>/dev/null || true
done

end_time=$(date +%s%3N)
duration=$((end_time - start_time))

# Calculate throughput
if (( duration > 0 )); then
    throughput=$((COUNT * 1000 / duration))
else
    throughput=$COUNT
fi

echo ""
echo "=== Results ==="
echo "Sent: $COUNT messages"
echo "Duration: ${duration}ms"
echo "Throughput: $throughput msg/s"
