## Quick Start

### 1. Start services

```bash
docker compose up -d
```

What starts:
- Kafka (port 9092) - message broker
- Schema Registry (port 8081) - manages Avro schemas
- MinIO (ports 9000, 9001) - S3-compatible storage
- Kafka UI (port 8080) - web UI to browse topics/messages
- Producer container - has Python + dependencies installed

### 2. Generate test data

**Easy way** - run the onboarding script:

```bash
./shared/data-generator/start-producer.sh
```

It checks prerequisites, starts services, creates topics, and lets you pick how to run the producer.

**Manual way** - run the producer directly:

```bash
docker exec -it clickstream-producer python3 produce-test-events.py
```

### 3. Check if data is flowing

Open Kafka UI: http://localhost:8080

Or use command line:
```bash
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic clickstream \
  --from-beginning \
  --max-messages 10
```

## What data gets generated

The producer simulates e-commerce user behavior:

- **Events**: VIEW, ADD_TO_CART, REMOVE_FROM_CART, CHECKOUT, PURCHASE
- **Rate**: 10 events/sec (you can change this)
- **Users**: 100 fake users with shopping sessions
- **Products**: 50 products in 5 categories
- **Late data**: 5% of events arrive 10-120 seconds late (good for testing watermarks)
- **Out-of-order**: Events don't always arrive in order (realistic scenario)