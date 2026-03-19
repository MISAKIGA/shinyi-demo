#!/bin/bash
# Environment configuration for Kafka Kerberos authentication

export KAFKA_BOOTSTRAP_SERVERS=10.10.10.30:9092
export KAFKA_TOPIC=test-topic
export KAFKA_GROUP_ID=test-group

# Kerberos/GSSAPI authentication
export KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT
export KAFKA_SASL_MECHANISM=GSSAPI
export KAFKA_KERBEROS_SERVICE_NAME=kafka
export KAFKA_KERBEROS_PRINCIPAL=kafka/kafka.example.com@EXAMPLE.COM
export KAFKA_KERBEROS_KEYTAB=/etc/kafka/kafka.keytab
export KAFKA_KERBEROS_KRB5_LOCATION=/etc/kafka/krb5.conf

# Producer settings
export KAFKA_PRODUCER_MESSAGE_COUNT=10000
export KAFKA_PRODUCER_MESSAGE_SIZE=1024
export KAFKA_PRODUCER_ACKS=1

# Consumer settings
export KAFKA_CONSUMER_MAX_POLL_RECORDS=500
export KAFKA_CONSUMER_AUTO_OFFSET_RESET=earliest

echo "Kafka Kerberos environment configured:"
echo "  Bootstrap Servers: $KAFKA_BOOTSTRAP_SERVERS"
echo "  Security Protocol: $KAFKA_SECURITY_PROTOCOL"
echo "  SASL Mechanism: $KAFKA_SASL_MECHANISM"
echo "  Kerberos Principal: $KAFKA_KERBEROS_PRINCIPAL"
echo "  Topic: $KAFKA_TOPIC"
