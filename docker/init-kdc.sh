#!/bin/bash
# Kerberos KDC Initialization Script
# This script initializes the KDC database and creates the kafka keytab

set -e

echo "=== Initializing Kerberos KDC ==="

# Wait for KDC to be ready
echo "Waiting for KDC to be ready..."
sleep 5

# Initialize KDC database (only if not exists)
if [ ! -f "/var/lib/kafka/krb5kdc/principal" ]; then
    echo "Creating KDC database..."
    kdb5_util create -r EXAMPLE.COM -P kdc-admin-secret -s
fi

# Create kafka principal if not exists
echo "Creating kafka principals..."
kadmin.local -q "addprinc -randkey kafka/kafka.example.com@EXAMPLE.COM" 2>/dev/null || true
kadmin.local -q "addprinc -randkey kafka/kafka.docker@EXAMPLE.COM" 2>/dev/null || true

# Create client principal for testing
kadmin.local -q "addprinc -pw kafka-client-secret testclient@EXAMPLE.COM" 2>/dev/null || true

# Generate keytab
echo "Generating kafka.keytab..."
kadmin.local -q "ktadd -k /etc/kafka/kafka.keytab kafka/kafka.example.com@EXAMPLE.COM" 2>/dev/null || \
kadmin.local -q "ktadd -k /etc/kafka/kafka.keytab kafka/kafka.docker@EXAMPLE.COM"

# Set permissions
chmod 644 /etc/kafka/kafka.keytab

echo "=== Keytab created successfully ==="
ls -la /etc/kafka/kafka.keytab

# Verify keytab
echo "Verifying keytab..."
klist -k /etc/kafka/kafka.keytab

echo "=== KDC Initialization Complete ==="
