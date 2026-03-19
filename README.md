# Shinyi Demo

Multi-module demo project for the [Shinyi EventBus](https://github.com/MISAKIGA/shinyi-eventbus) framework.

## Project Structure

```
shinyi-demo/
├── kafka-demo/                 # Kafka demo application module
│   ├── README.md               # Kafka Demo documentation
│   └── src/                    # Source code
└── docker/                     # Docker Compose configurations
    ├── kafka-plaintext.yml     # Kafka without authentication
    ├── kafka-sasl.yml          # Kafka with SASL/PLAIN authentication
    ├── kafka-kerberos.yml      # Kafka with Kerberos authentication
    ├── kdc/                    # Kerberos KDC Docker image
    │   ├── Dockerfile
    │   ├── krb5.kdc.conf
    │   └── kafka-jaas.conf
    ├── env-*.sh                # Environment configuration scripts
    └── setup-kerberos.sh       # Kerberos setup helper script
```

## Modules

### [Kafka Demo](kafka-demo/README.md)

Event-driven architecture demo with Kafka integration, supporting:
- PLAINTEXT, SASL/PLAIN, SASL/SCRAM, and Kerberos authentication
- Producer and consumer performance testing
- Spring Boot integration

## Quick Start

```bash
# Build and install all modules
mvn clean install -DskipTests

# Start Kafka (choose one)
docker-compose -f docker/kafka-plaintext.yml up -d

# Run the demo
cd kafka-demo
mvn spring-boot:run
```

## Kerberos Authentication

For production environments using Kerberos:

```bash
# Step 1: Setup Kerberos (build KDC image and extract keytab)
./docker/setup-kerberos.sh

# Step 2: Start Kafka with Kerberos
docker-compose -f docker/kafka-kerberos.yml up -d

# Step 3: Run Kerberos authentication test
cd kafka-demo
mvn spring-boot:run -Dspring-boot.run.arguments='--kafka.test.mode=kerberos-test'
```

### Kerberos Test Modes

```bash
# Test only Kerberos authentication (no performance test)
--kafka.test.mode=kerberos-test

# Test with Kerberos and performance
--kafka.test.mode=both
```

### Environment Variables for Kerberos

```bash
source docker/env-kerberos.sh
```

## License

Apache License 2.0 - See [shinyi-eventbus](https://github.com/MISAKIGA/shinyi-eventbus)
