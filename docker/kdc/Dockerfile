FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV KRB5_REALM=EXAMPLE.COM
ENV KRB5_PRINCIPAL=kafka/kafka.example.com@EXAMPLE.COM
ENV KRB5_PASSWORD=kafka-secret

# Install Kerberos packages
RUN apt-get update && apt-get install -y \
    krb5-kdc \
    krb5-admin-server \
    krb5-user \
    && rm -rf /var/lib/apt/lists/*

# Create Kafka principal directory
RUN mkdir -p /etc/kafka

# Copy configs
COPY krb5.kdc.conf /etc/krb5-kdc.conf

# Initialize KDC and create principals on first run
CMD echo "=== Starting KDC Init ===" && \
    cp /etc/krb5-kdc.conf /etc/krb5.conf && \
    echo "Creating KDC database..." && \
    kdb5_util create -r ${KRB5_REALM} -P kdc-admin-secret -s && \
    echo "Creating principals..." && \
    kadmin.local -q "addprinc -randkey kafka/kafka.example.com@${KRB5_REALM}" && \
    kadmin.local -q "addprinc -randkey kafka/kafka.docker@${KRB5_REALM}" && \
    kadmin.local -q "addprinc -pw ${KRB5_PASSWORD} testclient@${KRB5_REALM}" && \
    echo "Generating keytab..." && \
    kadmin.local -q "ktadd -k /etc/kafka/kafka.keytab kafka/kafka.example.com@${KRB5_REALM}" && \
    kadmin.local -q "ktadd -k /etc/kafka/kafka.keytab kafka/kafka.docker@${KRB5_REALM}" && \
    chmod 644 /etc/kafka/kafka.keytab && \
    echo "=== KDC Init Complete ===" && \
    ls -la /etc/kafka/kafka.keytab && \
    klist -k /etc/kafka/kafka.keytab && \
    echo "=== Starting KDC Server ===" && \
    /usr/sbin/krb5kdc -n
