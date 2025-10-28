# Flink Clickstream Processor

A production-ready Apache Flink streaming application that processes clickstream events from Kafka and writes enriched data to S3/MinIO.

## Overview

This Flink job demonstrates core streaming concepts and best practices:

**Quick start:**
```bash
# From project root
docker-compose up -d

# Submit job
cd flink/
./docker-run.sh submit

# View UI at http://localhost:8082
```

### Key Flink Concepts Implemented

1. **DataStream API**
   - Primary abstraction for stream processing
   - Type-safe transformations and operations
   - Flexible composition of processing logic

2. **Event Time Processing**
   - Uses event timestamp (not processing time)
   - Handles out-of-order events correctly
   - Watermarks for progress tracking

3. **Watermark Strategy**
   - Bounded out-of-orderness: 10 minutes
   - Ensures window completeness
   - Handles late data gracefully

4. **Time Windows**
   - Tumbling windows: 5-minute intervals
   - Event-time based (matching Spark implementation)
   - Window metadata enrichment (start/end times)

5. **Operators**
   - `filter`: Remove null/invalid events
   - `keyBy`: Partition stream by user_id
   - `window`: Apply time-based windowing
   - `process`: Custom window processing logic

6. **Connectors**
   - **Kafka Source**: Read from Kafka with exactly-once semantics
   - **File Sink**: Write to S3/MinIO in JSONL format
   - Custom serialization schemas

7. **Fault Tolerance**
   - Checkpointing every 30 seconds
   - Exactly-once processing guarantees
   - Automatic restart on failure (3 attempts)

## Architecture

```
┌─────────────┐
│   Kafka     │ clickstream topic
│  (Source)   │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────┐
│  Deserialize JSON           │ Custom schema
│  Extract Timestamp          │ Event time
│  Assign Watermarks          │ +10 min late
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│  Key by user_id             │ Partitioning
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│  Tumbling Window (5 min)    │ Event time
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│  Enrich with Window Data    │ ProcessFunction
│  - event_timestamp_start    │
│  - event_timestamp_end      │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│  Write to MinIO/S3          │ JSONL format
│  + Checkpointing            │
└─────────────────────────────┘
```

## Project Structure

```
flink/
├── pom.xml                                 # Maven dependencies
├── README.md                               # This file
└── src/main/java/com/streaming/flink/
    ├── ClickstreamProcessor.java          # Main application
    ├── model/
    │   ├── ClickstreamEvent.java          # Input POJO
    │   └── EnrichedClickstreamEvent.java  # Output POJO with window data
    └── serialization/
        ├── ClickstreamEventDeserializationSchema.java  # Kafka → POJO
        └── EnrichedEventEncoder.java                    # POJO → JSON

```

## Dependencies

Key Maven dependencies (see [pom.xml](pom.xml)):
- **Flink Streaming**: 1.18.0
- **Kafka Connector**: 3.0.1-1.18
- **S3 Filesystem**: Hadoop-based S3A
- **Jackson**: JSON serialization
- **Log4j2**: Logging

## Build & Run

You can run this Flink job in two ways:
1. **Docker Compose** (Recommended) - Full Flink cluster in containers
2. **Local** - Embedded Flink mini cluster

### Docker Compose (Recommended)

See [DOCKER.md](DOCKER.md) for complete Docker deployment guide.

### Local Development

#### Prerequisites

1. Java 11 or higher
2. Maven 3.6+
3. Running services (via docker-compose):
   - Kafka (localhost:9092)
   - MinIO (localhost:9000)

#### Build

```bash
cd flink
mvn clean package
```

This creates a fat JAR: `target/flink-clickstream-1.0-SNAPSHOT.jar`

### Run Locally

```bash
# Run with Flink mini cluster (embedded mode)
mvn exec:java -Dexec.mainClass="com.streaming.flink.ClickstreamProcessor"

# Or run the packaged JAR
java -jar target/flink-clickstream-1.0-SNAPSHOT.jar
```

### Submit to Flink Cluster

```bash
# Start local Flink cluster
$FLINK_HOME/bin/start-cluster.sh

# Submit job
$FLINK_HOME/bin/flink run \
  --class com.streaming.flink.ClickstreamProcessor \
  target/flink-clickstream-1.0-SNAPSHOT.jar

# Check job status
$FLINK_HOME/bin/flink list

# Cancel job
$FLINK_HOME/bin/flink cancel <job-id>
```

## Configuration

### Kafka Settings

Located in `ClickstreamProcessor.java`:

```java
KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
KAFKA_TOPIC = "clickstream"
```

### S3/MinIO Settings

```java
OUTPUT_PATH = "s3a://lakehouse/clickstream-events-flink/"
CHECKPOINT_PATH = "s3a://checkpoints/clickstream-events-flink/"

// Access credentials
s3.endpoint = "http://localhost:9000"
s3.access-key = "admin"
s3.secret-key = "password123"
```

### Time Characteristics

```java
// Event time (from event.timestamp field)
WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofMinutes(10))

// 5-minute tumbling windows
TumblingEventTimeWindows.of(Time.minutes(5))

// Checkpointing interval
env.enableCheckpointing(30000)  // 30 seconds
```

## Flink Best Practices Demonstrated

### 1. Type Safety with POJOs
- Strong typing throughout the pipeline
- Compile-time error detection
- Better IDE support and refactoring

### 2. Event Time Semantics
- Correct handling of late/out-of-order events
- Reproducible results regardless of processing speed
- Watermark-based progress tracking

### 3. Exactly-Once Processing
- Checkpointing for state consistency
- Kafka offset management
- Idempotent writes (where possible)

### 4. Scalability
- Keyed streams for parallel processing
- Configurable parallelism
- Stateful operators with state backends

### 5. Observability
- Structured logging with Log4j2
- Clear job naming and metrics
- Window metadata for debugging

### 6. Error Handling
- Graceful deserialization failures (filter nulls)
- Restart strategies for transient failures
- Checkpoint timeout configuration

## Comparison with Spark Implementation

| Aspect | Spark Structured Streaming | Flink DataStream API |
|--------|---------------------------|---------------------|
| **Latency** | Micro-batch (30s trigger) | True streaming (event-by-event) |
| **API Style** | SQL-like, declarative | Functional, imperative |
| **Time Semantics** | Event time + watermark | Event time + watermark |
| **Windowing** | Built-in window functions | Explicit window operators |
| **Fault Tolerance** | Checkpoints + WAL | Distributed snapshots |
| **Output** | Delta Lake format | JSONL (can use Iceberg) |
| **Type System** | DataFrame (semi-structured) | Strongly-typed POJOs |

## Output Format

Events are written to MinIO in **JSONL** (JSON Lines) format:

```json
{"event_id":"evt_123","user_id":"user_456","session_id":"sess_789","event_type":"page_view","page_url":"https://example.com/product/123","timestamp":1729630800000,"event_timestamp":"2024-10-22T18:00:00Z","event_timestamp_start":"2024-10-22T18:00:00Z","event_timestamp_end":"2024-10-22T18:05:00Z","ip_address":"192.168.1.1","country":"US","device_type":"mobile"}
```

Each line is a complete JSON object with:
- All original event fields
- `event_timestamp`: ISO-8601 formatted event time
- `event_timestamp_start`: Window start time
- `event_timestamp_end`: Window end time

## Monitoring & Debugging

### View Output in MinIO

```bash
# Using AWS CLI (configured for MinIO)
aws s3 ls s3://lakehouse/clickstream-events-flink/ --endpoint-url http://localhost:9000

# Download a file
aws s3 cp s3://lakehouse/clickstream-events-flink/<file> . --endpoint-url http://localhost:9000
```

### Flink Web UI

When running on a Flink cluster, access the web UI at:
- http://localhost:8081

Features:
- Job graph visualization
- Task manager metrics
- Checkpoint statistics
- Backpressure monitoring

### Logs

```bash
# View application logs
tail -f $FLINK_HOME/log/flink-*-taskexecutor-*.out

# Or when running locally
# Logs output to console
```

## Troubleshooting

### Kafka Connection Issues
- Verify Kafka is running: `docker ps`
- Check bootstrap servers match: `localhost:9092`
- Test with console consumer:
  ```bash
  kafka-console-consumer --bootstrap-server localhost:9092 --topic clickstream --from-beginning
  ```

### S3/MinIO Access Issues
- Verify MinIO is running and accessible
- Check credentials in `configureS3Access()`
- Test with AWS CLI:
  ```bash
  aws s3 ls --endpoint-url http://localhost:9000
  ```

### Checkpoint Failures
- Check MinIO bucket exists: `checkpoints`
- Verify write permissions
- Review checkpoint timeout settings

### Late Data / Missing Events
- Increase watermark delay: `Duration.ofMinutes(15)`
- Check event timestamp extraction logic
- Monitor watermark metrics in Flink UI

## Next Steps

- **Stateful Processing**: Add sessionization or user journey tracking
- **Metrics**: Implement custom metrics with `getRuntimeContext().getMetricGroup()`
- **State Backends**: Configure RocksDB for large state
- **Table API**: Implement equivalent logic with Flink SQL
- **Iceberg Sink**: Replace FileSink with Apache Iceberg for ACID transactions
- **Schema Evolution**: Add Avro Schema Registry integration
- **Testing**: Add unit tests with Flink TestHarness

## Resources

- [Apache Flink Documentation](https://flink.apache.org/docs/stable/)
- [DataStream API](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/overview/)
- [Event Time & Watermarks](https://nightlies.apache.org/flink/flink-docs-stable/docs/concepts/time/)
- [Kafka Connector](https://nightlies.apache.org/flink/flink-docs-stable/docs/connectors/datastream/kafka/)
- [Checkpointing](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/fault-tolerance/checkpointing/)
