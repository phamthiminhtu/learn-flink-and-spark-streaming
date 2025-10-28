# Stream Processing Window Exercises

This directory contains hands-on exercises for learning windowing concepts in Apache Flink and Apache Spark Structured Streaming.

## Directory Structure

```
exercises/
├── README.md                    # This file
├── flink/
│   ├── starter/                 # Starter code with TODOs
│   └── solution/                # Complete solutions
│       ├── SessionAnalyticsProcessor.java
│       ├── TumblingWindowExercise.java
│       └── LateDataHandlingExercise.java
├── spark/
│   ├── starter/                 # Starter code with TODOs
│   └── solution/                # Complete solutions
│       ├── session_analytics.py
│       ├── tumbling_window_exercise.py
│       └── sliding_window_exercise.py
└── sql/
    └── setup_exercise_tables.sql  # Database schema setup
```

## Prerequisites

1. Complete the main project setup (see main README.md)
2. Have Docker services running:
   ```bash
   docker compose up -d
   ```
3. Database tables created:
   ```bash
   # Run from project root directory
   docker exec -i postgres psql -U flink -d streaming < sql/setup_exercise_tables.sql
   ```

## Learning Path

Follow the syllabus in `/doc/windowing-syllabus.md` for structured learning.

### Quick Start Path

1. **Module 1**: Understand time concepts (no code)
2. **Module 2**: Tumbling windows → `TumblingWindowExercise`
3. **Module 3**: Sliding windows → `sliding_window_exercise.py`
4. **Module 4**: Session windows → `SessionAnalyticsProcessor` (DELIVERABLE)
5. **Module 5**: Late data → `LateDataHandlingExercise`

## Running Exercises

### Flink Exercises

1. **Compile the exercise** (add to your Flink project's `pom.xml` first)
   ```bash
   cd flink
   mvn clean package
   ```

2. **Submit to Flink cluster**
   ```bash
   # Example: Session Analytics
   docker exec -it flink-jobmanager flink run \
     /opt/flink/exercises/SessionAnalyticsProcessor.jar
   ```

3. **Monitor in Flink UI**
   - Open http://localhost:8081
   - Check job progress, watermarks, and metrics

### Spark Exercises

1. **Submit to Spark cluster**
   ```bash
   # Example: Session Analytics
   ./spark/docker-run.sh submit exercises/solution/session_analytics.py
   ```

2. **Monitor in Spark UI**
   - Open http://localhost:4040 (while job is running)
   - Check streaming query metrics

## Verifying Results

### Check Database Tables

```bash
# Connect to Postgres
docker exec -it postgres psql -U flink -d streaming

# View session analytics
SELECT * FROM user_sessions ORDER BY start_time DESC LIMIT 10;

# View event counts
SELECT * FROM event_counts_5min ORDER BY window_start DESC LIMIT 10;

# Check late events
SELECT * FROM late_events ORDER BY lateness_ms DESC LIMIT 10;

# View summary statistics
SELECT * FROM session_summary LIMIT 24;
```

### Check Delta Lake (Spark outputs)

```python
# Read from Delta Lake
from pyspark.sql import SparkSession

spark = SparkSession.builder \
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension") \
    .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog") \
    .getOrCreate()

# Read session data
sessions = spark.read.format("delta").load("s3a://lakehouse/user-sessions-delta/")
sessions.show()
```

## Exercise Details

### Exercise 2.1: Tumbling Window Event Counts
**File**: `TumblingWindowExercise.java` / `tumbling_window_exercise.py`
**Concept**: Fixed 5-minute windows, count events by type
**Output**: `event_counts_5min` table

**Key Learning**:
- Non-overlapping windows
- Window assignment
- Basic aggregation

**Verification Query**:
```sql
SELECT window_start, event_type, count
FROM event_counts_5min
WHERE window_start >= NOW() - INTERVAL '1 hour'
ORDER BY window_start DESC, count DESC;
```

### Exercise 3.1: Sliding Window Moving Average
**File**: `sliding_window_exercise.py`
**Concept**: 10-minute window sliding every 2 minutes
**Output**: Delta Lake

**Key Learning**:
- Overlapping windows
- Same event in multiple windows
- Trade-off: latency vs smoothness

**Analysis**:
```python
# Count how many windows an event appears in
sliding = spark.read.format("delta").load("s3a://lakehouse/user-events-sliding-delta/")
event_appearances = sliding.groupBy("user_id", "event_count").count()
event_appearances.orderBy("count", ascending=False).show()
```

### Exercise 4.1: Session Analytics (DELIVERABLE)
**File**: `SessionAnalyticsProcessor.java` / `session_analytics.py`
**Concept**: 30-minute gap-based session windows
**Output**: `user_sessions` table

**Key Learning**:
- Dynamic window size
- Session merging
- Gap-based timeout

**Analysis Queries**:
```sql
-- Average session duration by device
SELECT device_type,
       COUNT(*) as sessions,
       ROUND(AVG(duration_minutes), 2) as avg_duration,
       ROUND(AVG(pages_visited), 1) as avg_pages
FROM user_sessions
GROUP BY device_type;

-- Bounce rate by hour
SELECT DATE_TRUNC('hour', start_time) as hour,
       ROUND(100.0 * COUNT(*) FILTER (WHERE event_count = 1) / COUNT(*), 2) as bounce_rate
FROM user_sessions
GROUP BY hour
ORDER BY hour DESC;

-- Conversion funnel
SELECT
  COUNT(*) as total_sessions,
  COUNT(*) FILTER (WHERE event_count > 1) as engaged,
  COUNT(*) FILTER (WHERE last_event_type = 'add_to_cart') as added_to_cart,
  COUNT(*) FILTER (WHERE last_event_type = 'purchase') as purchased
FROM user_sessions;
```

### Exercise 5.2: Late Data Handling
**File**: `LateDataHandlingExercise.java`
**Concept**: Watermark + allowed lateness + side output
**Output**: `event_counts_with_late`, `late_events` tables

**Key Learning**:
- Watermark strategy
- Window re-computation
- Late event tracking

**Analysis Queries**:
```sql
-- Late event distribution
SELECT
  CASE
    WHEN lateness_ms / 1000 < 60 THEN '<1min'
    WHEN lateness_ms / 1000 < 300 THEN '1-5min'
    WHEN lateness_ms / 1000 < 600 THEN '5-10min'
    WHEN lateness_ms / 1000 < 900 THEN '10-15min'
    ELSE '>15min'
  END as lateness_bucket,
  COUNT(*) as count
FROM late_events
GROUP BY lateness_bucket
ORDER BY lateness_bucket;

-- Events that triggered late updates
SELECT * FROM event_counts_with_late
WHERE is_late_update = true
ORDER BY last_updated DESC;
```

## Common Issues

### Issue: No events appearing in windows
**Cause**: Watermark hasn't advanced enough to close windows
**Solution**: Wait longer, or generate more events with recent timestamps

### Issue: Flink job fails with ClassNotFoundException
**Cause**: Dependencies not included in jar
**Solution**: Use `maven-shade-plugin` to create fat jar

### Issue: Spark "output mode must be append or complete"
**Cause**: Using update mode with non-windowed aggregation
**Solution**: Use `withWatermark()` for append mode, or `complete` mode for small state

### Issue: Late events not appearing in side output
**Cause**: Events are within allowed lateness, not "too late"
**Solution**: Check watermark + allowed lateness threshold vs event timestamps

## Performance Tips

### Flink
1. Use `AggregateFunction` instead of `ProcessWindowFunction` alone
2. Configure checkpoint interval based on window size
3. Set appropriate parallelism (typically 1-2x number of CPU cores)
4. Use RocksDB state backend for large state

### Spark
1. Use `append` output mode when possible (more efficient than `complete`)
2. Configure watermark delay based on actual late data patterns
3. Use `trigger(processingTime='...')` for predictable micro-batch intervals
4. Partition Delta Lake tables by time for efficient queries

## Next Steps

1. Complete all exercises in sequence
2. Experiment with different parameters:
   - Window sizes
   - Watermark delays
   - Allowed lateness
3. Try the final project (Module 9 in syllabus)
4. Adapt exercises to your own data and use cases

## Resources

- **Syllabus**: `/doc/windowing-syllabus.md`
- **Flink Windowing Docs**: https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/operators/windows/
- **Spark Structured Streaming**: https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html
- **Main Project README**: `../README.md`

## Contributing

Found an issue or want to add an exercise? Please open an issue or PR!

## Questions?

Refer to the syllabus for theoretical explanations, or check the inline comments in solution code for implementation details.
