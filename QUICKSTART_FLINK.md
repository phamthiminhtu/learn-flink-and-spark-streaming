# Flink Quick Start Guide

Get the Flink clickstream processor running in 5 minutes!

## What You'll Run

```
Kafka Producer → Kafka → Flink Cluster → MinIO (S3)
                           ↓
                    Web UI (localhost:8082)
```

## Prerequisites

- Docker & Docker Compose
- ~~Java 11+ and Maven~~ **Not needed!** (We use Docker to build)

## Step-by-Step

### 1. Start All Services (2 minutes)

```bash
# From project root
docker-compose up -d

# Wait for services to be healthy (~30 seconds)
docker-compose ps
```

**What's starting:**
- ✅ Kafka (message broker)
- ✅ MinIO (S3-compatible storage)
- ✅ Flink JobManager (master node)
- ✅ Flink TaskManager x2 (worker nodes)
- ✅ Producer (clickstream generator)

### 2. Start Producing Events (30 seconds)

```bash
# Enter producer container
docker exec -it clickstream-producer bash

# Generate clickstream events
python produce-test-events.py
```

You should see:
```
✅ Producing to topic: clickstream
📊 Event rate: ~100 events/sec
🔄 Ctrl+C to stop
```

**Keep this running** (open a new terminal for next steps)

### 3. Build & Submit Flink Job (2-3 minutes)

```bash
# In a new terminal
cd flink/

# Build JAR and submit to cluster
# (No Maven required - uses Docker to build!)
./docker-run.sh submit
```

Output:
```
========================================
Building Flink Job
========================================
⚠️  Maven not found locally - using Docker to build
ℹ️  This may take a few minutes on first run...

[INFO] Downloading dependencies...
[INFO] Building jar: /app/target/flink-clickstream-1.0-SNAPSHOT.jar
✅ Build completed

========================================
Submitting Job to Flink Cluster
========================================
ℹ️  JobManager UI: http://localhost:8082
Job has been submitted with JobID: a1b2c3d4e5f6...
✅ Job submitted successfully!
```

**Note:** First build downloads dependencies (~200MB) and may take 2-3 minutes. Subsequent builds are much faster (~30 seconds)!

### 4. Verify It's Working

#### Option A: Web UI (Visual)

Open in browser:
```
http://localhost:8082
```

You should see:
- **Running Jobs**: 1 job called "Flink Clickstream Processor"
- **TaskManagers**: 2 workers available
- **Completed Checkpoints**: Increments every 30 seconds

#### Option B: Command Line

```bash
# List running jobs
./docker-run.sh list

# Check cluster status
./docker-run.sh status
```

### 5. Check Output Data

```bash
# View files in MinIO
docker exec -it minio mc ls myminio/lakehouse/clickstream-events-flink/

# Sample output:
# [2024-10-22 18:30:00 PST] 125KiB part-0-0.json
# [2024-10-22 18:35:00 PST] 118KiB part-0-1.json

# View file contents (first 10 lines)
docker exec -it minio mc cat myminio/lakehouse/clickstream-events-flink/part-0-0 | head -10
```

**Expected output:**
```json
{"event_id":"evt_123","user_id":"user_456","event_type":"page_view","timestamp":1729630800000,"event_timestamp":"2024-10-22T18:00:00Z","event_timestamp_start":"2024-10-22T18:00:00Z","event_timestamp_end":"2024-10-22T18:05:00Z",...}
{"event_id":"evt_124","user_id":"user_457","event_type":"click","timestamp":1729630801000,...}
```

## You're Done! 🎉

Your Flink streaming pipeline is now:
- ✅ Reading events from Kafka
- ✅ Processing in 5-minute windows
- ✅ Writing enriched data to MinIO
- ✅ Checkpointing every 30 seconds for fault tolerance

## What's Happening?

```
1. Producer generates clickstream events
   ↓
2. Events sent to Kafka topic "clickstream"
   ↓
3. Flink reads from Kafka
   ↓
4. Flink assigns event time & watermarks
   ↓
5. Flink creates 5-minute tumbling windows
   ↓
6. Flink enriches events with window metadata
   ↓
7. Flink writes to MinIO in JSONL format
   ↓
8. Checkpoints saved to MinIO every 30s
```

## Monitoring

### Flink Web UI
```
http://localhost:8082
```
- Job status & metrics
- Task parallelism
- Checkpoint statistics
- Backpressure indicators

### Kafka UI
```
http://localhost:8080
```
- Topic messages
- Consumer lag
- Partition info

### MinIO Console
```
http://localhost:9001
Username: admin
Password: password123
```
- Browse output files
- View checkpoints
- Download data

## Common Commands

```bash
# View Flink logs
./docker-run.sh logs

# List jobs
./docker-run.sh list

# Cancel job
./docker-run.sh cancel <job-id>

# Rebuild and resubmit (after code changes)
./docker-run.sh submit

# Stop everything
docker-compose down

# Stop and remove all data
docker-compose down -v
```

## Troubleshooting

### No data in MinIO?

**Check producer is running:**
```bash
docker logs clickstream-producer
```

**Check Kafka has messages:**
```bash
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic clickstream \
  --max-messages 5
```

**Check Flink job is running:**
```bash
./docker-run.sh list
# Should show: RUNNING status
```

### Job submission fails?

**Wait for cluster to be ready:**
```bash
docker-compose ps
# All services should be "healthy" or "running"
```

**Check JobManager logs:**
```bash
docker logs flink-jobmanager
```

### Port conflicts?

If ports 8082, 9092, or 9000 are already in use:

Edit `docker-compose.yml` and change port mappings:
```yaml
ports:
  - "8083:8081"  # Change from 8082
```

## Next Steps

1. **Explore the code:**
   - [ClickstreamProcessor.java](flink/src/main/java/com/streaming/flink/ClickstreamProcessor.java) - Main application
   - [Model classes](flink/src/main/java/com/streaming/flink/model/) - POJOs

2. **Modify the pipeline:**
   - Change window size (5 minutes → 1 minute)
   - Add filtering or aggregations
   - Implement sessionization

3. **Compare with Spark:**
   - Run Spark version: `python spark/src/spark_clickstream_events.py`
   - See [SPARK_VS_FLINK.md](flink/SPARK_VS_FLINK.md) for comparison

4. **Scale up:**
   - Increase TaskManagers: `docker-compose up -d --scale flink-taskmanager=4`
   - Increase parallelism in `config/flink-conf.yaml`

5. **Advanced features:**
   - Enable savepoints for job upgrades
   - Add custom metrics
   - Implement complex event processing (CEP)

## Full Documentation

- **Docker Guide**: [flink/DOCKER.md](flink/DOCKER.md)
- **Flink README**: [flink/README.md](flink/README.md)
- **Spark Comparison**: [flink/SPARK_VS_FLINK.md](flink/SPARK_VS_FLINK.md)

## Architecture Deep Dive

### Flink Core Concepts Used

1. **DataStream API**: Type-safe stream processing
2. **Event Time**: Using event timestamps (not processing time)
3. **Watermarks**: Bounded out-of-orderness (10 minutes)
4. **Windows**: Tumbling 5-minute event-time windows
5. **Operators**: KeyBy, Window, ProcessWindowFunction
6. **Connectors**: Kafka Source, File Sink (S3)
7. **Checkpointing**: Exactly-once processing guarantees

### Why This Setup?

**Docker Compose:**
- Production-like multi-node cluster
- Easy to scale (add more TaskManagers)
- Isolated resources per service
- Realistic networking

**vs Local embedded cluster:**
- Local: Faster for development/debugging
- Docker: Better for integration testing
- Both supported! See [flink/README.md](flink/README.md)

## Performance

With default settings:
- **Throughput**: ~10,000 events/second
- **Latency**: ~30 seconds (checkpoint interval)
- **Resource usage**: ~2GB RAM total

To increase throughput:
1. Scale TaskManagers: `--scale flink-taskmanager=4`
2. Increase parallelism in config
3. Tune Kafka partitions and Flink task slots

## Clean Up

```bash
# Stop all services
docker-compose down

# Also remove volumes (deletes all data)
docker-compose down -v

# Remove built JAR
cd flink/
mvn clean
```

## Getting Help

- **Flink docs**: https://flink.apache.org/docs/stable/
- **Project issues**: Check console output and logs
- **Web UI**: http://localhost:8082 shows detailed metrics

Happy streaming! 🚀
