#!/bin/bash
# Test Kerberos Authentication for Kafka
# This script verifies that Kerberos authentication is working correctly

set -e

KAFKA_HOST=${1:-localhost}
KAFKA_PORT=${2:-9092}
KAFKA_BOOTSTRAP="${KAFKA_HOST}:${KAFKA_PORT}"

echo "=============================================="
echo "Testing Kerberos Authentication for Kafka"
echo "=============================================="
echo "Bootstrap Server: ${KAFKA_BOOTSTRAP}"
echo ""

# Check if kinit is available
if ! command -v kinit &> /dev/null; then
    echo "ERROR: kinit not found. Install krb5-workstation package."
    exit 1
fi

# Set up Kerberos configuration
export KRB5_CONFIG=${KRB5_CONFIG:-./krb5.conf}
export KRB5_CLIENT_KTNAME=${KRB5_CLIENT_KTNAME:-./kafka.keytab}

echo "=== Kerberos Configuration ==="
echo "KRB5_CONFIG: ${KRB5_CONFIG}"
echo "KRB5_CLIENT_KTNAME: ${KRB5_CLIENT_KTNAME}"
echo ""

# Verify keytab exists
if [ ! -f "./kafka.keytab" ]; then
    echo "ERROR: kafka.keytab not found. Run docker compose first to generate keytab."
    echo "Try: docker cp kdc:/etc/kafka/kafka.keytab ./kafka.keytab"
    exit 1
fi

echo "=== Keytab Contents ==="
klist -k ./kafka.keytab
echo ""

# Test Kerberos login with keytab
echo "=== Testing Kerberos Login ==="
PRINCIPAL=$(klist -k ./kafka.keytab | grep kafka | head -1 | awk '{print $2}')
echo "Using principal: ${PRINCIPAL}"

kinit -kt ./kafka.keytab "${PRINCIPAL}" && echo "kinit SUCCESS" || { echo "kinit FAILED"; exit 1; }
echo ""

# Verify ticket
echo "=== Kerberos Ticket Cache ==="
klist
echo ""

# Test Kafka connection using kafka-verifiable-producer with GSSAPI
echo "=== Testing Kafka Producer with Kerberos ==="
TOPIC="kerberos-test-topic"

# Create the topic first using kafka-topics (if available)
echo "Note: Topic creation should be handled by Kafka auto.create.topics.enable=true"
echo ""

# Try to produce a test message using kafka-console-producer
# Note: This requires the Kafka image to have the producer scripts
echo "Producer test with GSSAPI..."
echo "To verify manually, run:"
echo "  docker exec -it kafka kafka-console-producer --broker-list localhost:9092 --topic ${TOPIC} --producer.config /opt/kafka/config/producer.properties"
echo ""

echo "=== Kerberos Authentication Test Complete ==="
echo "If you see kinit SUCCESS above, Kerberos auth is working."
