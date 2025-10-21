# Data Generator - Clickstream Events

This directory contains tools for generating realistic clickstream events for testing and development.

## Quick Start

### For New Users

Simply run the onboarding script:

```bash
./shared/data-generator/start-producer.sh
```

The script will:
1. ✅ Check prerequisites (Docker, Docker Compose)
2. ✅ Start required services (Kafka, Schema Registry)
3. ✅ Verify Kafka topics exist
4. ✅ Launch the producer with your preferred mode

### What It Does

The producer generates realistic e-commerce user behavior:

- **Event Types**: VIEW, ADD_TO_CART, REMOVE_FROM_CART, CHECKOUT, PURCHASE
- **Rate**: 10 events/second (configurable)
- **Users**: 100 simulated users with session management
- **Products**: 50 products across 5 categories
- **Late Data**: 5% of events arrive 10-120 seconds late (for testing watermarks)

## Producer Modes

### 1. Interactive Mode (Recommended for Testing)

See live output with statistics:

```bash
./shared/data-generator/start-producer.sh
# Choose option 1

# You'll see:
# 🚀 Starting Clickstream Event Producer
#    Target rate: 10 events/sec
#    Topic: clickstream
# 📊 Events: 100 | Failed: 0 | Rate: 10.0/sec
```

Press `Ctrl+C` to stop.

### 2. Background Mode (For Long-Running Tests)

Runs in the background:

```bash
./shared/data-generator/start-producer.sh
# Choose option 2

# Monitor logs
docker compose logs -f producer

# Stop when done
docker exec clickstream-producer pkill -f produce-test-events.py
```

### 3. Custom Configuration

Edit `kafka/produce-test-events.py` to customize:

```python
EVENTS_PER_SECOND = 10      # Increase for load testing
NUM_USERS = 100             # More users = more variety
NUM_PRODUCTS = 50           # Catalog size
LATE_DATA_PERCENTAGE = 0.05 # Adjust late arrival simulation
MAX_LATE_SECONDS = 120      # Maximum delay
```

Then run:

```bash
docker exec -it clickstream-producer python3 produce-test-events.py
```

## Event Schema

Each event contains:

```json
{
  "event_id": "550e8400-e29b-41d4-a716-446655440000",
  "user_id": "USER_0042",
  "session_id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "event_type": "VIEW",
  "product_id": "PROD_023",
  "price": 79.99,
  "event_time": 1729545123000,
  "processing_time": 1729545125000
}
```

**Note**: `event_time` and `processing_time` differ for late-arriving events.

## Monitoring

### Web UI (Kafka UI)

Open in browser: http://localhost:8080

- View topics
- Browse messages
- Monitor consumer groups

### Command Line

**View recent messages:**

```bash
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic clickstream \
  --from-beginning \
  --max-messages 10
```

**Check topic stats:**

```bash
docker exec kafka kafka-topics \
  --describe \
  --topic clickstream \
  --bootstrap-server localhost:9092
```

**Producer logs:**

```bash
docker compose logs -f producer
```

## Troubleshooting

### Producer won't start

```bash
# Check if services are running
docker compose ps

# Restart services
docker compose restart kafka schema-registry producer

# View logs
docker compose logs producer
```

### No events in Kafka

```bash
# Verify topics exist
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Should see:
# clickstream
# clickstream-dlq

# If missing, recreate them
docker compose up kafka-setup
```

### Connection timeout errors

```bash
# Check Kafka health
docker inspect -f '{{.State.Health.Status}}' kafka

# Should show: healthy
# If not, wait a bit longer or restart

docker compose restart kafka
```

### Producer dependencies missing

```bash
# Recreate producer container
docker compose up -d --force-recreate producer

# Verify installation
docker exec clickstream-producer pip list | grep -E "(kafka|lz4)"
```

## Advanced Usage

### Manual Control

```bash
# Enter producer container
docker exec -it clickstream-producer bash

# Run with custom settings (edit file first)
python3 produce-test-events.py

# Check installed packages
pip list

# Exit
exit
```

### Load Testing

1. Edit `kafka/produce-test-events.py`:
   ```python
   EVENTS_PER_SECOND = 1000  # High load
   ```

2. Restart producer:
   ```bash
   docker exec clickstream-producer pkill -f produce-test-events.py
   docker exec -d clickstream-producer python3 produce-test-events.py
   ```

3. Monitor Kafka performance:
   ```bash
   docker stats kafka
   ```

### Multiple Producers

Run multiple instances for even higher load:

```bash
# Start first producer in background
docker exec -d clickstream-producer python3 produce-test-events.py

# Start second producer in new container
docker run -d --name producer-2 \
  --network streaming-with-flink-spark_streaming-network \
  -v $(pwd)/kafka:/app \
  -w /app \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:29092 \
  python:3.11-slim \
  bash -c "pip install kafka-python lz4 && python3 produce-test-events.py"
```

## Architecture

```
┌─────────────────┐
│   start-producer│  ← You run this
│      .sh        │
└────────┬────────┘
         │
         ├─► Check Prerequisites
         ├─► Start Services
         ├─► Verify Topics
         └─► Launch Producer
              │
              ├─► Generate Events
              ├─► Simulate Sessions
              ├─► Create Late Data
              └─► Send to Kafka
                    │
                    ▼
              ┌──────────┐
              │  Kafka   │ → Flink/Spark Processing
              └──────────┘
```

## Files

- `start-producer.sh` - Main onboarding script
- `../../kafka/produce-test-events.py` - Producer implementation
- `../../kafka/README.md` - Producer documentation

## Support

For issues or questions:

1. Check logs: `docker compose logs producer`
2. Review this README
3. Check main project documentation
4. Verify prerequisites are met

## Next Steps

After data is flowing:

1. **Flink Processing**: Process streams in real-time
2. **Spark Batch**: Analyze historical data
3. **MinIO Storage**: Query parquet files in data lake
4. **Monitoring**: Set up Grafana dashboards

Enjoy streaming! 🚀

