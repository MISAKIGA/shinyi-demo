#!/bin/bash
# Raw Kafka Producer/Consumer Benchmark Script
# Tests 100K messages using native Kafka tools

set -e

BOOTSTRAP_SERVER="localhost:9092"
TOPIC="benchmark-raw-test"
MESSAGE_COUNT=100000
MESSAGE_SIZE=1024

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_header() {
    echo -e "\n${YELLOW}========================================${NC}"
    echo -e "${YELLOW}$1${NC}"
    echo -e "${YELLOW}========================================${NC}\n"
}

# Generate random message payload
generate_message() {
    local size=$1
    cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w "$size" | head -c "$size"
}

# Create topic if not exists
create_topic() {
    log_info "Creating topic: $TOPIC"
    docker exec kafka kafka-topics --create \
        --if-not-exists \
        --topic "$TOPIC" \
        --partitions 1 \
        --replication-factor 1 \
        --bootstrap-server localhost:9092 2>/dev/null || true
}

# Clean up topic
cleanup_topic() {
    log_info "Cleaning up topic: $TOPIC"
    docker exec kafka kafka-topics --delete --topic "$TOPIC" --bootstrap-server localhost:9092 2>/dev/null || true
}

# Run raw Kafka producer benchmark
run_producer_benchmark() {
    log_header "RAW KAFKA PRODUCER BENCHMARK"

    # Create topic
    create_topic

    log_info "Starting producer benchmark: $MESSAGE_COUNT messages, $MESSAGE_SIZE bytes each"

    # Generate a file with messages
    local msg_file="/tmp/kafka_msgs_$$.txt"
    for i in $(seq 1 $MESSAGE_COUNT); do
        echo "key-$i:$(generate_message $MESSAGE_SIZE)"
    done > "$msg_file"

    # Run producer and measure time
    local start_time=$(date +%s.%N)

    docker exec -i kafka kafka-console-producer \
        --broker-list localhost:9092 \
        --topic "$TOPIC" \
        --producer-property acks=1 \
        --producer-property batch.size=65536 \
        --producer-property linger.ms=10 \
        --producer-property compression.type=snappy \
        < "$msg_file" > /dev/null 2>&1

    local end_time=$(date +%s.%N)
    rm -f "$msg_file"

    local duration=$(echo "$end_time - $start_time" | bc)
    local throughput=$(echo "scale=2; $MESSAGE_COUNT / $duration" | bc)
    local bandwidth=$(echo "scale=2; ($MESSAGE_COUNT * $MESSAGE_SIZE) / $duration / 1024 / 1024" | bc)

    log_success "Producer Results:"
    echo "  Messages: $MESSAGE_COUNT"
    echo "  Duration: ${duration}s"
    echo "  Throughput: ${throughput} msg/s"
    echo "  Bandwidth: ${bandwidth} MB/s"

    # Store results
    echo "RAW_PRODUCER_DURATION=$duration"
    echo "RAW_PRODUCER_THROUGHPUT=$throughput"
    echo "RAW_PRODUCER_BANDWIDTH=$bandwidth"
}

# Run raw Kafka consumer benchmark
run_consumer_benchmark() {
    log_header "RAW KAFKA CONSUMER BENCHMARK"

    log_info "Starting consumer benchmark: $MESSAGE_COUNT messages"

    # Ensure messages are produced first
    sleep 2

    # Run consumer and measure time
    local start_time=$(date +%s.%N)

    local msg_count=$(docker exec kafka kafka-console-consumer \
        --bootstrap-server localhost:9092 \
        --topic "$TOPIC" \
        --from-beginning \
        --max-messages $MESSAGE_COUNT \
        --consumer-property fetch.max.bytes=10485760 \
        --consumer-property max.poll.records=10000 \
        2>/dev/null | wc -l)

    local end_time=$(date +%s.%N)

    local duration=$(echo "$end_time - $start_time" | bc)
    local throughput=$(echo "scale=2; $MESSAGE_COUNT / $duration" | bc)
    local bandwidth=$(echo "scale=2; ($MESSAGE_COUNT * $MESSAGE_SIZE) / $duration / 1024 / 1024" | bc)

    log_success "Consumer Results:"
    echo "  Messages: $MESSAGE_COUNT"
    echo "  Duration: ${duration}s"
    echo "  Throughput: ${throughput} msg/s"
    echo "  Bandwidth: ${bandwidth} MB/s"

    # Store results
    echo "RAW_CONSUMER_DURATION=$duration"
    echo "RAW_CONSUMER_THROUGHPUT=$throughput"
    echo "RAW_CONSUMER_BANDWIDTH=$bandwidth"
}

# Main
main() {
    log_header "RAW KAFKA BENCHMARK (100K Messages)"

    run_producer_benchmark
    run_consumer_benchmark

    log_header "BENCHMARK COMPLETE"
    cleanup_topic
}

main "$@"
