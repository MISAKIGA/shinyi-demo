#!/bin/bash
# Setup Kerberos for Kafka Demo
# This script builds the KDC image and extracts the keytab for testing

set -e

echo "=============================================="
echo "Setting up Kerberos for Kafka Demo"
echo "=============================================="

# Step 1: Build KDC image
echo ""
echo "=== Step 1: Building KDC Image ==="
cd "$(dirname "$0")"
docker build -t kafka-kdc:latest -f kdc/Dockerfile kdc/

# Step 2: Start KDC container
echo ""
echo "=== Step 2: Starting KDC Container ==="
docker run -d --name kafka-kdc-temp \
  -e KRB5_REALM=EXAMPLE.COM \
  -e KRB5_PRINCIPAL=kafka/kafka.example.com@EXAMPLE.COM \
  -e KRB5_PASSWORD=kafka-secret \
  kafka-kdc:latest

# Wait for KDC to initialize
echo "Waiting for KDC to initialize..."
sleep 10

# Step 3: Copy keytab from KDC container
echo ""
echo "=== Step 3: Extracting Keytab ==="
docker cp kafka-kdc-temp:/etc/kafka/kafka.keytab ./kafka.keytab
docker cp kafka-kdc-temp:/etc/krb5-kdc.conf ./krb5.client.conf

# Verify keytab
echo "Keytab contents:"
klist -k ./kafka.keytab || echo "klist not available, continuing..."

# Step 4: Clean up temp container
echo ""
echo "=== Step 4: Cleanup ==="
docker stop kafka-kdc-temp
docker rm kafka-kdc-temp

echo ""
echo "=============================================="
echo "Kerberos setup complete!"
echo "=============================================="
echo ""
echo "To start Kafka with Kerberos:"
echo "  docker-compose -f docker/kafka-kerberos.yml up -d"
echo ""
echo "To run the Kerberos test:"
echo "  cd kafka-demo"
echo "  mvn spring-boot:run -Dspring-boot.run.arguments='--kafka.test.mode=kerberos-test'"
echo ""
echo "Or manually:"
echo "  java -jar target/kafka-demo-1.0.0.jar --kafka.test.mode=kerberos-test \\"
echo "    --kafka.demo.bootstrap-servers=localhost:9092 \\"
echo "    --kafka.demo.security-protocol=SASL_PLAINTEXT \\"
echo "    --kafka.demo.sasl-mechanism=GSSAPI \\"
echo "    --kafka.demo.kerberos-principal=kafka/kafka.example.com@EXAMPLE.COM \\"
echo "    --kafka.demo.kerberos-keytab=../docker/kafka.keytab \\"
echo "    --kafka.demo.kerberos-krb5-location=../docker/krb5.client.conf"
