# Streaming with Flink & Spark

A repo to explore different streaming mechanisms: Kafka, Flink, and Spark.
The main goal is to understand the core components of Flink and Spark in streaming pipelines and how each serves a different purpose.

## What to learn next

1. Start services ✅
2. Generate test data ✅
3. Build Flink streaming jobs (real-time processing)
4. Build Spark batch jobs (historical analysis)
5. Add monitoring (Prometheus/Grafana)

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
│   └── tests/
└── spark/                      # Spark jobs
    ├── requirements.txt
    └── src/
```

## Quick Start

- Getting started: [set up and generate mock data](https://github.com/phamthiminhtu/streaming-with-flink-spark/blob/master/doc/quick_start.md#quick-start)
- [Common commands](https://github.com/phamthiminhtu/streaming-with-flink-spark/blob/master/doc/quick_start.md#common-commands)
- [Common issues](https://github.com/phamthiminhtu/streaming-with-flink-spark/blob/master/doc/quick_start.md#common-issues)
