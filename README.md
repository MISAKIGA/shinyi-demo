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
    └── env-*.sh                # Environment configuration scripts
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

## License

Apache License 2.0 - See [shinyi-eventbus](https://github.com/MISAKIGA/shinyi-eventbus)
