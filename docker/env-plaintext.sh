#!/bin/bash
# Environment configuration for Kafka PLAINTEXT connection

export KAFKA_BOOTSTRAP_SERVERS=10.10.10.30:9092
export KAFKA_TOPIC=test-topic
export KAFKA_GROUP_ID=test-group

# No authentication
export KAFKA_SECURITY_PROTOCOL=PLAINTEXT
export KAFKA_SASL_MECHANISM=
export KAFKA_SASL_USERNAME=
export KAFKA_SASL_PASSWORD=

# Producer settings
export KAFKA_PRODUCER_MESSAGE_COUNT=10000
export KAFKA_PRODUCER_MESSAGE_SIZE=1024
export KAFKA_PRODUCER_ACKS=1

# Consumer settings
export KAFKA_CONSUMER_MAX_POLL_RECORDS=500
export KAFKA_CONSUMER_AUTO_OFFSET_RESET=earliest

echo "Kafka PLAINTEXT environment configured:"
echo "  Bootstrap Servers: $KAFKA_BOOTSTRAP_SERVERS"
echo "  Security Protocol: $KAFKA_SECURITY_PROTOCOL"
echo "  Topic: $KAFKA_TOPIC"
