# Streaming with Flink & Spark

A repo to explore different streaming mechanisms: Kafka, Flink, and Spark.
The main goal is to understand the core components of Flink and Spark in streaming pipelines and how each serves a different purpose.

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

## How it works

```
┌──────────────┐
│   Producer   │ → Generates realistic clickstream events
└──────┬───────┘
       │
       ▼
┌──────────────┐
│    Kafka     │ → Message broker
└──────┬───────┘
       │
       ├─► Flink  → Real-time stream processing
       │
       └─► Spark  → Batch processing & analytics
             │
             ▼
       ┌──────────┐
       │  MinIO   │ → Data lake storage
       └──────────┘
```

## Project Structure

```
streaming-with-flink-spark/
├── config/                      # Configuration files
│   ├── flink-conf.yaml
│   └── spark-defaults.conf
├── data/
│   └── schemas/                 # Avro schemas
│       ├── clickstream-event.avsc
│       └── clickstream-event-readable.avsc
├── docker-compose.yml           # Service definitions
├── flink/                       # Flink jobs
│   ├── pom.xml
│   └── src/
├── kafka/                       # Data producer
│   ├── produce-test-events.py  # Main producer script
│   └── README.md               # Producer documentation
├── shared/
│   ├── data-generator/         # Onboarding & utilities
│   │   ├── start-producer.sh   # 🌟 New user script
│   │   ├── README.md
│   │   └── QUICK_REFERENCE.md
│   ├── schemas/
│   └── tests/
└── spark/                      # Spark jobs
    ├── requirements.txt
    └── src/
```

## What data gets generated

The producer simulates e-commerce user behavior:

- **Events**: VIEW, ADD_TO_CART, REMOVE_FROM_CART, CHECKOUT, PURCHASE
- **Rate**: 10 events/sec (you can change this)
- **Users**: 100 fake users with shopping sessions
- **Products**: 50 products in 5 categories
- **Late data**: 5% of events arrive 10-120 seconds late (good for testing watermarks)
- **Out-of-order**: Events don't always arrive in order (realistic scenario)

## Common commands

### Services

```bash
docker compose up -d              # Start everything
docker compose down               # Stop everything
docker compose ps                 # Check what's running
docker compose logs -f producer   # View logs
```

### Producer

```bash
# Start producer (see live output, Ctrl+C to stop)
docker exec -it clickstream-producer python3 produce-test-events.py

# Start in background
docker exec -d clickstream-producer python3 produce-test-events.py

# Stop producer
docker exec clickstream-producer pkill -f produce-test-events.py
```

### Kafka

```bash
# List topics
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Read messages from topic
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic clickstream \
  --from-beginning
```

### Jump into containers

```bash
docker exec -it clickstream-producer bash  # Producer
docker exec -it kafka bash                 # Kafka
```

## Web UIs

- Kafka UI: http://localhost:8080 - browse topics and messages
- MinIO Console: http://localhost:9001 - view stored files (login: admin/password123)
- Schema Registry: http://localhost:8081/subjects - view registered schemas

## More docs

- Producer details: `kafka/README.md`
- Data generator guide: `shared/data-generator/README.md`
- Command cheatsheet: `shared/data-generator/QUICK_REFERENCE.md`

## Common issues

**Services won't start**
```bash
docker ps                          # Is Docker running?
docker compose logs kafka          # Check for errors
docker compose restart kafka       # Restart if needed
```

**Producer timeout error**
```bash
# Check if Kafka is healthy (takes 30-60 sec to start)
docker inspect -f '{{.State.Health.Status}}' kafka

# Should show "healthy". If not, wait or restart:
docker compose restart kafka
```

**Topic doesn't exist**
```bash
# Create topics
docker compose up kafka-setup

# Check if they exist
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

## What to learn next

1. Start services ✅
2. Generate test data ✅
3. Build Flink streaming jobs (real-time processing)
4. Build Spark batch jobs (historical analysis)
5. Add monitoring (Prometheus/Grafana)
