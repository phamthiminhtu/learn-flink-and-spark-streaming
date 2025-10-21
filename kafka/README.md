# Clickstream Event Producer

Generates realistic e-commerce clickstream events and sends them to Kafka.

## Quick Start

### Option 1: Docker (Recommended)

```bash
# Start the producer service
docker compose up -d producer

# View logs
docker compose logs -f producer

# Stop producer
docker compose stop producer
```

### Option 2: Run Locally

```bash
# Install dependencies
pip3 install kafka-python lz4

# Run producer
python3 kafka/produce-test-events.py
```

## Configuration

Environment variables (set in docker-compose.yml or export locally):

- `KAFKA_BOOTSTRAP_SERVERS` - Kafka broker addresses (default: `localhost:9092`)

Script parameters (edit in produce-test-events.py):

- `TOPIC_NAME` - Target topic (default: `clickstream`)
- `EVENTS_PER_SECOND` - Event generation rate (default: 10)
- `NUM_USERS` - Number of simulated users (default: 100)
- `NUM_PRODUCTS` - Number of products in catalog (default: 50)
- `LATE_DATA_PERCENTAGE` - Percentage of late events (default: 5%)
- `MAX_LATE_SECONDS` - Maximum delay for late events (default: 120s)

## Event Schema

Each clickstream event contains:

```json
{
  "event_id": "uuid",
  "user_id": "USER_0001",
  "session_id": "session-uuid",
  "event_type": "VIEW|ADD_TO_CART|REMOVE_FROM_CART|CHECKOUT|PURCHASE",
  "product_id": "PROD_001",
  "price": 99.99,
  "event_time": 1729545123000,
  "processing_time": 1729545125000
}
```

## Event Types

- **VIEW** - User views a product
- **ADD_TO_CART** - User adds product to cart
- **REMOVE_FROM_CART** - User removes product from cart
- **CHECKOUT** - User initiates checkout
- **PURCHASE** - Purchase completed (auto-generated after CHECKOUT)

## Features

- ✅ Realistic user behavior simulation with sessions
- ✅ Shopping cart state management
- ✅ Late-arriving events (5% arrive 10-120 seconds late)
- ✅ Out-of-order events for testing watermark strategies
- ✅ Partitioning by user_id for consistent ordering per user
- ✅ Product catalog with varied pricing
- ✅ Session timeout (30 minutes)

## Monitoring

```bash
# Check events are being produced
docker compose logs producer | grep "Events:"

# Verify in Kafka UI
open http://localhost:8080

# Check topic
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic clickstream \
  --from-beginning \
  --max-messages 5
```

## Troubleshooting

### Producer won't start

```bash
# Check if Kafka is healthy
docker compose ps

# Check producer logs
docker compose logs producer

# Restart producer
docker compose restart producer
```

### No events in Kafka

```bash
# Check if topic exists
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Check producer can connect
docker compose logs producer | grep "Starting"
```

### Wrong event rate

Edit `EVENTS_PER_SECOND` in `produce-test-events.py` and restart:

```bash
docker compose restart producer
```

