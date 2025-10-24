# Streaming with Flink & Spark

A repo to explore different streaming mechanisms: Kafka, Flink, and Spark.
The main goal is to understand the core components of Flink and Spark in streaming pipelines and how each serves a different purpose.

## Learning points

1. Start services ✅
2. Generate test data ✅
3. Build Spark micro batch jobs (30s latency): event -> kafka topic -> spark streaming -> S3 (MinIO) ✅
```
# Set up
       docker compose up -d

# Simulate events:
       docker exec -it clickstream-producer python3 produce-test-events.py

# Submit Spark job
       ./spark/docker-run.sh submit spark_clickstream_events.py

# Check if data is streamed into MinIO at: http://localhost:9001/browser/lakehouse:
```
4. Build Flink streaming jobs (real-time processing): event -> kafka topic -> flink -> postgres ✅
```
# Set up: 
docker compose up -d

# Simulate events: 

       docker exec -it clickstream-producer python3 produce-test-events.py
# Submit Flink job

       ./flink/docker-run.sh submit

# Check if data is streamed into Postgres db:

       docker exec -it postgres psql -U flink -d streaming
```
- Check streamed data in Postgres
```sql

       SELECT COUNT(*) FROM clickstream_events;
       SELECT * FROM clickstream_events ORDER BY event_timestamp DESC LIMIT 10;
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
       ├─► Flink
       │
       └─► Spark
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
│   │   ├── start-producer.sh   
│   │   ├── README.md
│   │   └── QUICK_REFERENCE.md
│   └── tests/
└── spark/                      # Spark jobs
    ├── requirements.txt
    └── src/
```

## Quick Start

- Getting started: [set up and generate mock data](https://github.com/phamthiminhtu/streaming-with-flink-spark/blob/master/doc/quick_start.md#quick-start)
- [Common commands](https://github.com/phamthiminhtu/streaming-with-flink-spark/blob/master/doc/quick_start.md#common-commands)
- [Common issues](https://github.com/phamthiminhtu/streaming-with-flink-spark/blob/master/doc/quick_start.md#common-issues)
