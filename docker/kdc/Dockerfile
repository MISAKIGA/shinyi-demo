FROM centos:7

ENV KRB5_REALM=EXAMPLE.COM
ENV KRB5_PRINCIPAL=kafka/kafka.example.com@EXAMPLE.COM
ENV KRB5_PASSWORD=kafka-secret

# Install Kerberos packages
RUN yum install -y \
    krb5-server \
    krb5-workstation \
    krb5-libs \
    && yum clean all

# Create Kafka principal directory
RUN mkdir -p /etc/kafka

# Copy krb5.conf
COPY krb5.kdc.conf /etc/krb5-kdc.conf

# Initialize KDC on first run
CMD echo "=== Starting KDC ===" && \
    echo "Initializing KDC database..." && \
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
    klist -k /etc/kafka/kafka.keytab && \
    echo "=== Starting KDC Server ===" && \
    krb5kdc
