# Running Flink with Docker Compose

This guide shows you how to run the Flink clickstream processor in a Docker-based Flink cluster.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Docker Compose Stack                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────┐     │
│  │  Kafka   │───▶│ Flink        │───▶│   MinIO      │     │
│  │  :9092   │    │ JobManager   │    │   (S3)       │     │
│  └──────────┘    │ :8082        │    │   :9000      │     │
│                  └──────┬───────┘    └──────────────┘     │
│                         │                                  │
│                  ┌──────┴───────┐                          │
│                  │ TaskManager  │                          │
│                  │ (x2 workers) │                          │
│                  └──────────────┘                          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Prerequisites

- Docker & Docker Compose installed
- At least 4GB RAM available for Docker
- ~~Java 11+ and Maven~~ **Not required!** (Docker builds the JAR for you)

## Quick Start

### 1. Start All Services

From the project root directory:

```bash
# Start the entire stack
docker-compose up -d

# Check services are running
docker-compose ps
```

**Services started:**
- Kafka (port 9092)
- Kafka UI (port 8080)
- MinIO (ports 9000, 9001)
- Schema Registry (port 8081)
- **Flink JobManager** (port 8082)
- **Flink TaskManager** (2 instances)
- Clickstream Producer

### 2. Build & Submit Flink Job

```bash
cd flink/

# Build JAR and submit to cluster
./docker-run.sh submit
```

This will:
1. Build the fat JAR with Maven
2. Submit it to the Flink cluster running in Docker
3. Start processing events from Kafka

### 3. Verify Job is Running

**Web UI:**
```
http://localhost:8082
```

**Command line:**
```bash
# List running jobs
./docker-run.sh list

# Show cluster status
./docker-run.sh status

# View logs
./docker-run.sh logs
```

### 4. Check Output Data

```bash
# View files in MinIO
docker exec -it minio mc ls myminio/lakehouse/clickstream-events-flink/

# Download a file to inspect
docker exec -it minio mc cp \
  myminio/lakehouse/clickstream-events-flink/<file-name> \
  /tmp/output.json
```

## Helper Script Usage

The `docker-run.sh` script simplifies common tasks:

```bash
# Build JAR only
./docker-run.sh build

# Build and submit job (default)
./docker-run.sh submit

# List running jobs
./docker-run.sh list

# Show cluster status
./docker-run.sh status

# Cancel a job
./docker-run.sh cancel <job-id>

# View logs
./docker-run.sh logs

# Show help
./docker-run.sh help
```

## Docker Compose Configuration

### Flink Services

The `docker-compose.yml` defines:

#### JobManager (Master)
```yaml
flink-jobmanager:
  image: flink:1.18.0-java11
  ports:
    - "8082:8081"  # Web UI
    - "6123:6123"  # RPC
  environment:
    # Configuration via FLINK_PROPERTIES
    - state.checkpoints.dir: s3://checkpoints/flink/
    - s3.endpoint: http://minio:9000
```

**Key configurations:**
- Checkpoint storage on MinIO
- S3 endpoint configured for internal Docker network
- Web UI enabled for job submission

#### TaskManager (Workers)
```yaml
flink-taskmanager:
  image: flink:1.18.0-java11
  scale: 2  # 2 parallel workers
  environment:
    - taskmanager.numberOfTaskSlots: 2
```

**Scalability:**
- 2 TaskManagers × 2 slots = 4 parallel tasks total
- Adjust `scale` in docker-compose.yml to add more workers

### Volume Mounts

```yaml
volumes:
  - ./flink/target:/opt/flink/usrlib  # JAR files
  - ./config/flink-conf.yaml:/opt/flink/conf/flink-conf.yaml
  - flink-checkpoints:/tmp/flink-checkpoints
```

## Configuration

### Environment Variables

The Flink job reads configuration from environment:

```java
// In ClickstreamProcessor.java
private static final String KAFKA_BOOTSTRAP_SERVERS =
    System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
```

**Docker network hostnames:**
- Kafka: `kafka:29092` (internal) or `localhost:9092` (external)
- MinIO: `minio:9000` (internal) or `localhost:9000` (external)

### Flink Configuration File

Location: `/config/flink-conf.yaml`

Key settings:
```yaml
# Parallelism
parallelism.default: 1
taskmanager.numberOfTaskSlots: 2

# Checkpointing
state.checkpoints.dir: s3://checkpoints/flink/
execution.checkpointing.interval: 30s

# S3/MinIO
s3.endpoint: http://minio:9000
s3.access-key: admin
s3.secret-key: password123
```

**To modify:**
1. Edit `config/flink-conf.yaml`
2. Restart Flink cluster:
   ```bash
   docker-compose restart flink-jobmanager flink-taskmanager
   ```

## Common Tasks

### Start Producer

```bash
# Enter producer container
docker exec -it clickstream-producer bash

# Start generating events
python produce-test-events.py
```

### Monitor Kafka

**Kafka UI:**
```
http://localhost:8080
```

**Command line:**
```bash
# View topics
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Consume messages
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic clickstream \
  --from-beginning \
  --max-messages 5
```

### Monitor MinIO

**MinIO Console:**
```
http://localhost:9001
Username: admin
Password: password123
```

**Command line:**
```bash
# List buckets
docker exec minio mc ls myminio/

# List files in lakehouse bucket
docker exec minio mc ls myminio/lakehouse/clickstream-events-flink/

# View file contents
docker exec minio mc cat myminio/lakehouse/clickstream-events-flink/<file>
```

### View Checkpoints

```bash
# List checkpoints
docker exec minio mc ls myminio/checkpoints/flink/

# Checkpoint structure
checkpoints/
├── flink/
│   └── <job-id>/
│       ├── chk-1/
│       ├── chk-2/
│       └── chk-3/
└── flink-savepoints/
```

## Troubleshooting

### Job Submission Fails

**Error:** "Connection refused"

```bash
# Check JobManager is running
docker ps | grep flink-jobmanager

# Check JobManager health
docker exec flink-jobmanager curl http://localhost:8081/overview

# View JobManager logs
docker logs flink-jobmanager
```

### No Data in MinIO

**Possible causes:**
1. No events in Kafka
2. Job not running
3. Checkpointing not completing

**Debug steps:**
```bash
# 1. Check Kafka has data
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic clickstream \
  --from-beginning \
  --max-messages 1

# 2. Check job is running
./docker-run.sh list

# 3. Check Flink UI for errors
# Visit http://localhost:8082
```

### Checkpoint Failures

**Error:** "Could not write checkpoint"

```bash
# Check MinIO is accessible from Flink
docker exec flink-jobmanager curl http://minio:9000

# Check S3 credentials in flink-conf.yaml
docker exec flink-jobmanager cat /opt/flink/conf/flink-conf.yaml | grep s3

# Verify checkpoints bucket exists
docker exec minio mc ls myminio/checkpoints/
```

### Out of Memory

**Error:** "Java heap space"

**Solution:** Increase memory in `docker-compose.yml`:

```yaml
flink-jobmanager:
  environment:
    - jobmanager.memory.process.size: 2048m  # Increase from 1600m

flink-taskmanager:
  environment:
    - taskmanager.memory.process.size: 2048m  # Increase from 1728m
```

Then restart:
```bash
docker-compose restart flink-jobmanager flink-taskmanager
```

## Advanced: Scaling

### Add More TaskManagers

Edit `docker-compose.yml`:
```yaml
flink-taskmanager:
  scale: 4  # Increase from 2 to 4
```

Restart:
```bash
docker-compose up -d --scale flink-taskmanager=4
```

Verify:
```bash
docker ps | grep flink-taskmanager
# Should show 4 containers
```

### Increase Parallelism

Edit `config/flink-conf.yaml`:
```yaml
parallelism.default: 4  # Increase from 1
taskmanager.numberOfTaskSlots: 4  # Increase from 2
```

Restart and resubmit job:
```bash
docker-compose restart flink-jobmanager flink-taskmanager
./docker-run.sh submit
```

## Cleanup

### Stop All Services

```bash
# Stop and remove containers
docker-compose down

# Also remove volumes (data will be lost!)
docker-compose down -v
```

### Remove Flink Job Only

```bash
# List jobs
./docker-run.sh list

# Cancel job
./docker-run.sh cancel <job-id>
```

## Comparison: Docker vs Local

| Aspect | Docker Compose | Local (run.sh) |
|--------|----------------|----------------|
| **Setup** | Multi-container cluster | Embedded mini cluster |
| **Web UI** | Full cluster UI | Limited local UI |
| **Scalability** | Multiple TaskManagers | Single JVM |
| **Resource Isolation** | Container limits | System resources |
| **Production-like** | ✅ Yes | ❌ No |
| **Development Speed** | Slower (container startup) | ✅ Faster |
| **Debugging** | Harder (remote) | ✅ Easier (local) |

**Recommendation:**
- **Development:** Use local `./run.sh` for fast iteration
- **Testing:** Use Docker Compose for integration testing
- **Production:** Use Kubernetes or standalone cluster

## Next Steps

1. **Enable Monitoring:**
   - Uncomment Prometheus/Grafana in `docker-compose.yml`
   - Configure Flink metrics reporter

2. **Add Savepoints:**
   ```bash
   # Create savepoint
   docker exec flink-jobmanager flink savepoint <job-id> s3://checkpoints/flink-savepoints/

   # Restore from savepoint
   docker exec flink-jobmanager flink run \
     --fromSavepoint s3://checkpoints/flink-savepoints/<savepoint-id> \
     /opt/flink/usrlib/flink-clickstream-1.0-SNAPSHOT.jar
   ```

3. **Configure High Availability:**
   - Add ZooKeeper to docker-compose.yml
   - Enable HA in flink-conf.yaml

4. **Switch to Iceberg:**
   - Replace FileSink with IcebergSink
   - Get ACID transactions and schema evolution

## Resources

- [Flink Docker Docs](https://nightlies.apache.org/flink/flink-docs-stable/docs/deployment/resource-providers/standalone/docker/)
- [Docker Compose Reference](https://docs.docker.com/compose/compose-file/)
- [Flink Configuration](https://nightlies.apache.org/flink/flink-docs-stable/docs/deployment/config/)
