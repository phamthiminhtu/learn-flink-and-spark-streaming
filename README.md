# Streaming with Flink & Spark

A complete streaming data platform for processing clickstream events using Apache Flink and Apache Spark.

## Quick Start

### 1. Start All Services

```bash
docker compose up -d
```

This starts:
- **Kafka** - Message broker (port 9092)
- **Schema Registry** - Avro schema management (port 8081)
- **MinIO** - S3-compatible object storage (ports 9000, 9001)
- **Kafka UI** - Web interface (port 8080)
- **Producer Container** - Python environment with dependencies

### 2. Start Producing Data (New User Onboarding)

Use the onboarding script for a guided setup:

```bash
./shared/data-generator/start-producer.sh
```

The script will:
- ✅ Check prerequisites (Docker, Docker Compose)
- ✅ Verify services are running
- ✅ Create Kafka topics if needed
- ✅ Start the producer with your preferred mode

**Alternative**: Manual start

```bash
docker exec -it clickstream-producer python3 produce-test-events.py
```

### 3. Verify Data is Flowing

**Web UI** (easiest):
```bash
open http://localhost:8080
```

**Command line**:
```bash
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic clickstream \
  --from-beginning \
  --max-messages 10
```

## Architecture

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

## Producer Features

The clickstream producer generates realistic e-commerce user behavior:

- **Event Types**: VIEW, ADD_TO_CART, REMOVE_FROM_CART, CHECKOUT, PURCHASE
- **Rate**: 10 events/second (configurable)
- **Users**: 100 simulated users with session tracking
- **Products**: 50 products across 5 categories
- **Late Data**: 5% of events arrive 10-120 seconds late (for watermark testing)
- **Out-of-Order Events**: Realistic event timing scenarios

## Frequently Used Commands

### Service Management

```bash
# Start all services
docker compose up -d

# Stop all services
docker compose down

# View service status
docker compose ps

# View logs
docker compose logs -f [service_name]
```

### Producer Control

```bash
# Interactive mode (see live output)
docker exec -it clickstream-producer python3 produce-test-events.py

# Background mode
docker exec -d clickstream-producer python3 produce-test-events.py

# Stop producer
docker exec clickstream-producer pkill -f produce-test-events.py

# View producer logs
docker compose logs -f producer
```

### Kafka Operations

```bash
# List topics
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Describe topic
docker exec kafka kafka-topics --describe --topic clickstream --bootstrap-server localhost:9092

# Consume messages
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic clickstream \
  --from-beginning
```

### Container Access

```bash
# Enter producer container
docker exec -it clickstream-producer bash

# Enter Kafka container
docker exec -it kafka bash
```

## Web Interfaces

- **Kafka UI**: http://localhost:8080 - Browse topics, messages, consumer groups
- **MinIO Console**: http://localhost:9001 - Object storage (admin/password123)
- **Schema Registry**: http://localhost:8081 - API endpoint

## Documentation

- **Producer Setup**: `kafka/README.md`
- **Data Generator**: `shared/data-generator/README.md`
- **Quick Reference**: `shared/data-generator/QUICK_REFERENCE.md`

## Troubleshooting

### Services won't start

```bash
# Check Docker is running
docker ps

# Check logs for errors
docker compose logs [service_name]

# Restart services
docker compose restart
```

### Producer connection timeout

```bash
# Check Kafka health (should be "healthy")
docker inspect -f '{{.State.Health.Status}}' kafka

# Wait 30-60 seconds for Kafka to start
# Or restart Kafka
docker compose restart kafka
```

### Topics missing

```bash
# Create topics
docker compose up kafka-setup

# Verify topics exist
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

## Next Steps

1. ✅ Start services
2. ✅ Generate data
3. 🔄 Build Flink streaming jobs
4. 🔄 Create Spark batch jobs
5. 🔄 Set up monitoring with Prometheus/Grafana

Happy streaming! 🚀
