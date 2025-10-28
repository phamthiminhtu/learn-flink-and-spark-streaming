# Learning Flink & Spark

A repo to explore different streaming mechanisms: Kafka, Flink, and Spark.

The main goal is to understand the core components of Flink and Spark in streaming pipelines and how each serves a different purpose.

Bonus point: got my elementary Java revised lol.

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

## Learning points

1. Generate and publish simulated event data to Kafka ✅
2. Build Spark micro batch jobs (30s latency): event -> kafka topic -> spark streaming -> S3 (MinIO) ✅
```
# Set up
       docker compose up -d

# Simulate events:
       docker exec -it clickstream-producer python3 produce-test-events.py

# Submit Spark job
       ./spark/docker-run.sh submit <job name>
       
       # example
       ./spark/docker-run.sh submit jobs/sliding_window_exercise.py

# Check if data is streamed into MinIO at: http://localhost:9001/browser/lakehouse
```
3. Build Flink streaming jobs (real-time processing): event -> Kafka topic -> Flink -> Postgres ✅
```
# Set up: 
       docker compose up -d

# Simulate events: 

       docker exec -it clickstream-producer python3 produce-test-events.py
# Submit Flink job
       cd flink
       ./docker-run.sh submit # jobs in folder `flink/jobs/active` will be run

# Check if data is streamed into Postgres db:

       docker exec -it postgres psql -U flink -d streaming
```
- Check streamed data in Postgres
```sql

       SELECT COUNT(*) FROM clickstream_events;
       SELECT * FROM clickstream_events ORDER BY event_timestamp DESC LIMIT 10;
```
- Refer to [Flink quick start](https://github.com/phamthiminhtu/learn-flink-and-spark-streaming/tree/master/flink#overview) for more details.


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
├── doc/                         # Documentation & learning materials
│   ├── windowing-syllabus.md           # Comprehensive windowing course
│   ├── windowing-quick-reference.md    # Quick reference guide
│   └── quick_start.md
├── docker-compose.yml           # Service definitions
├── exercises/                   # Hands-on windowing exercises
│   ├── README.md
│   ├── flink/                   # Flink implementations
│   │   └── solution/
│   ├── spark/                   # Spark implementations
│   │   └── solution/
│   └── sql/                     # Database schemas
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

- Getting started: [set up and generate mock data](doc/quick_start.md#quick-start)
- [Common commands](doc/quick_start.md#common-commands)
- [Common issues](doc/quick_start.md#common-issues)
