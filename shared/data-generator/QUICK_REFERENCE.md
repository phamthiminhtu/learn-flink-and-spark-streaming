# Quick Reference - Clickstream Producer

## One-Line Start

```bash
./shared/data-generator/start-producer.sh
```

## Common Commands

### Start Producer
```bash
# Interactive (see live output)
docker exec -it clickstream-producer python3 produce-test-events.py

# Background
docker exec -d clickstream-producer python3 produce-test-events.py
```

### Stop Producer
```bash
docker exec clickstream-producer pkill -f produce-test-events.py
```

### View Logs
```bash
# Producer logs
docker compose logs -f producer

# All services
docker compose logs -f
```

### Check Status
```bash
# Services
docker compose ps

# Kafka health
docker inspect -f '{{.State.Health.Status}}' kafka

# Topics
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

### View Events
```bash
# Last 10 events
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic clickstream \
  --from-beginning \
  --max-messages 10

# Live stream (Ctrl+C to stop)
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic clickstream
```

### Service Management
```bash
# Start all services
docker compose up -d

# Stop all services
docker compose down

# Restart specific service
docker compose restart producer

# View all containers
docker compose ps
```

## Configuration

Edit `kafka/produce-test-events.py`:

```python
EVENTS_PER_SECOND = 10      # Events per second
NUM_USERS = 100             # Number of simulated users
NUM_PRODUCTS = 50           # Products in catalog
LATE_DATA_PERCENTAGE = 0.05 # 5% late events
MAX_LATE_SECONDS = 120      # Max delay (seconds)
```

## URLs

- **Kafka UI**: http://localhost:8080
- **Schema Registry**: http://localhost:8081
- **MinIO Console**: http://localhost:9001 (admin/password123)

## Event Types

| Type | Description |
|------|-------------|
| VIEW | User views product |
| ADD_TO_CART | Add to shopping cart |
| REMOVE_FROM_CART | Remove from cart |
| CHECKOUT | Initiate checkout |
| PURCHASE | Purchase complete |

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Connection timeout | Wait for Kafka: `docker inspect kafka` |
| Topics missing | Run: `docker compose up kafka-setup` |
| lz4 error | Recreate: `docker compose up -d --force-recreate producer` |
| Already running | Stop first: `pkill -f produce-test-events.py` |

## File Locations

- Script: `shared/data-generator/start-producer.sh`
- Producer: `kafka/produce-test-events.py`
- Config: `docker-compose.yml`
- Schema: `data/schemas/clickstream-event.avsc`

