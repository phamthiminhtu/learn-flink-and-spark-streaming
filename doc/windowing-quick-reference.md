# Windowing Quick Reference

## Window Types Comparison

| Window Type | Size | Overlap | Use Case | Example |
|------------|------|---------|----------|---------|
| **Tumbling** | Fixed | None | Hourly reports, batch-like | Every 5 minutes |
| **Sliding** | Fixed | Yes | Moving averages, trends | 10 min window, 2 min slide |
| **Session** | Dynamic | No | User sessions, activity bursts | 30 min gap timeout |
| **Global** | Infinite | N/A | Custom triggers | Count-based |

## Flink Window Syntax

### Tumbling Window
```java
events
    .keyBy(Event::getUserId)
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .aggregate(new MyAggregator());
```

### Sliding Window
```java
events
    .keyBy(Event::getUserId)
    .window(SlidingEventTimeWindows.of(
        Time.minutes(10),  // window size
        Time.minutes(2)    // slide interval
    ))
    .aggregate(new MyAggregator());
```

### Session Window
```java
events
    .keyBy(Event::getUserId)
    .window(EventTimeSessionWindows.withGap(Time.minutes(30)))
    .process(new SessionProcessor());
```

### Watermark Strategy
```java
WatermarkStrategy
    .<Event>forBoundedOutOfOrderness(Duration.ofMinutes(10))
    .withTimestampAssigner((event, ts) -> event.getTimestamp())
```

### Allowed Lateness
```java
events
    .keyBy(Event::getKey)
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .allowedLateness(Time.minutes(5))      // Accept late data
    .sideOutputLateData(lateOutputTag)      // Capture too-late events
    .aggregate(new MyAggregator());
```

## Spark Window Syntax

### Tumbling Window
```python
events.groupBy(
    window(col("event_timestamp"), "5 minutes"),
    col("user_id")
).count()
```

### Sliding Window
```python
events.groupBy(
    window(col("event_timestamp"), "10 minutes", "2 minutes"),
    col("user_id")
).count()
```

### Watermark
```python
events.withWatermark("event_timestamp", "10 minutes")
```

### Session Window (Custom Implementation)
```python
from pyspark.sql.functions import lag, sum as _sum, when
from pyspark.sql.window import Window

w = Window.partitionBy("user_id").orderBy("event_timestamp")

sessions = events.withColumn(
    "time_since_last",
    col("event_timestamp").cast("long") - lag("event_timestamp", 1).over(w).cast("long")
).withColumn(
    "new_session",
    when(col("time_since_last") > 1800, 1).otherwise(0)  # 30 min
).withColumn(
    "session_id",
    _sum("new_session").over(w.rowsBetween(Window.unboundedPreceding, Window.currentRow))
)
```

## Window Functions Comparison

### Flink

| Function Type | Characteristics | When to Use |
|--------------|-----------------|-------------|
| `AggregateFunction` | Incremental, most efficient | Simple aggregations (count, sum, avg) |
| `ReduceFunction` | Incremental, same input/output type | Combining events |
| `ProcessWindowFunction` | Access all events, has window metadata | Complex logic needing full window context |
| Combined (Agg + Process) | Efficient + metadata | Best of both worlds |

### Spark

| Pattern | Output Mode | State Size | Use Case |
|---------|-------------|------------|----------|
| `groupBy` + `window()` | append/complete | Grows | Time-based aggregations |
| `groupBy` (no window) | complete | Can grow large | Streaming tables |
| `mapGroupsWithState` | update | Controlled | Custom stateful logic |
| `flatMapGroupsWithState` | append/update | Controlled | Sessions with timeout |

## Time Concepts

```
Event Timeline:
Event A occurs:    10:00:00  (event time)
Event A ingested:  10:02:30  (ingestion time)
Event A processed: 10:05:00  (processing time)

Out-of-order scenario:
Event B: occurs 10:00:10, processed 10:04:00
Event C: occurs 10:00:05, processed 10:04:30

Processing order: B → C
Event time order: C → B  ✓ Correct for analytics
```

## Watermark Mechanics

```
Watermark = max_event_time - maxOutOfOrderness

Example:
- Max event time seen: 10:20:00
- maxOutOfOrderness: 10 minutes
- Current watermark: 10:10:00

Meaning: "All events before 10:10:00 have arrived"

Window [10:00 - 10:05]:
- Closes when watermark >= 10:05
- With 10-min watermark, closes around 10:15 processing time
```

## Late Data Handling

```
┌─────────────────────────────────────────┐
│          Window Lifecycle               │
├─────────────────────────────────────────┤
│ Window: [10:00 - 10:05]                 │
│                                         │
│ Phase 1: Active                         │
│   Watermark < 10:05                     │
│   Events are "on-time"                  │
│                                         │
│ Phase 2: Late (if allowed lateness)    │
│   10:05 <= Watermark < 10:10           │
│   (assuming 5-min allowed lateness)     │
│   Events trigger re-computation         │
│                                         │
│ Phase 3: Closed                         │
│   Watermark >= 10:10                    │
│   New events → side output or dropped   │
└─────────────────────────────────────────┘
```

## Configuration Recommendations

### Development
```java
// Flink
.forBoundedOutOfOrderness(Duration.ofMinutes(1))
.allowedLateness(Time.minutes(1))

// Spark
.withWatermark("event_timestamp", "1 minute")
```

### Production (adjust based on data characteristics)
```java
// Flink - Higher tolerance
.forBoundedOutOfOrderness(Duration.ofMinutes(10))
.allowedLateness(Time.minutes(5))

// Spark
.withWatermark("event_timestamp", "10 minutes")
```

## Common Patterns

### Pattern 1: Efficient Tumbling Window (Flink)
```java
stream
    .keyBy(...)
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .aggregate(
        new MyAggregateFunction(),        // Efficient incremental
        new AddWindowMetadata()           // Add window info
    );
```

### Pattern 2: Session with Metrics (Flink)
```java
stream
    .keyBy(Event::getUserId)
    .window(EventTimeSessionWindows.withGap(Time.minutes(30)))
    .process(new ProcessWindowFunction<Event, SessionMetrics, String, TimeWindow>() {
        @Override
        public void process(String key, Context ctx, Iterable<Event> events,
                          Collector<SessionMetrics> out) {
            TimeWindow window = ctx.window();
            // Compute metrics from all events in session
            // Emit SessionMetrics with window start/end
        }
    });
```

### Pattern 3: Sliding Window for Anomaly Detection (Spark)
```python
# Calculate baseline
baseline = events.groupBy(
    window("event_timestamp", "1 hour")
).agg(avg("metric").alias("baseline_avg"))

# Calculate current (5-min window, 1-min slide)
current = events.groupBy(
    window("event_timestamp", "5 minutes", "1 minute")
).agg(avg("metric").alias("current_avg"))

# Join and compare
alerts = current.join(baseline,
    current.window.start >= baseline.window.start
).filter(
    col("current_avg") > col("baseline_avg") * 2
)
```

### Pattern 4: Late Data to Side Output (Flink)
```java
OutputTag<Event> lateTag = new OutputTag<Event>("late-events"){};

SingleOutputStreamOperator<Result> result = stream
    .keyBy(...)
    .window(...)
    .sideOutputLateData(lateTag)
    .aggregate(...);

// Main results
result.addSink(...);

// Late events
result.getSideOutput(lateTag).addSink(...);
```

## Performance Tuning

### State Size Optimization

**Flink:**
```java
// Use AggregateFunction (incremental) instead of ProcessWindowFunction (buffering)
// BAD: Buffers all events
.process(new ProcessWindowFunction<Event, Result, String, TimeWindow>() {...})

// GOOD: Incremental aggregation
.aggregate(new AggregateFunction<Event, Acc, Result>() {...})

// BEST: Combine both
.aggregate(new MyAggregator(), new AddMetadata())
```

**Spark:**
```python
# Use append mode when possible
.outputMode("append")  # Writes only new results

# Instead of complete mode
.outputMode("complete")  # Keeps entire result table in memory
```

### Parallelism

**Flink:**
```java
env.setParallelism(4);  // Set based on CPU cores

// Or per operator
stream.keyBy(...).window(...).aggregate(...).setParallelism(8);
```

**Spark:**
```python
spark.conf.set("spark.sql.shuffle.partitions", "8")
```

## Debugging Tips

### Check Watermark Progress (Flink UI)
1. Open http://localhost:8081
2. Click on running job
3. View "Watermarks" tab
4. Look for watermark lag

### Check Event Time Skew (Spark)
```python
# Check timestamp distribution
events.groupBy(
    window("event_timestamp", "1 minute")
).count().orderBy("window").show(100)
```

### Verify Window Closure
```sql
-- Check if windows are closing as expected
SELECT window_start, window_end, COUNT(*)
FROM event_counts_5min
GROUP BY window_start, window_end
ORDER BY window_start DESC
LIMIT 20;

-- Gap indicates watermark hasn't advanced
```

## Testing

### Flink Unit Test
```java
@Test
public void testWindowAggregation() {
    OneInputStreamOperatorTestHarness<Event, Result> harness = ...;

    // Add events with timestamps
    harness.processElement(new StreamRecord<>(event1, 1000L));
    harness.processElement(new StreamRecord<>(event2, 2000L));

    // Advance watermark to close window
    harness.processWatermark(new Watermark(10000L));

    // Assert results
    assertEquals(expected, harness.extractOutputValues());
}
```

### Spark Integration Test
```python
# Use memory sink for testing
query = events.writeStream \
    .format("memory") \
    .queryName("test_table") \
    .start()

# Verify results
result = spark.sql("SELECT * FROM test_table")
assert result.count() == expected_count
```

## Further Reading

- **Streaming Systems Book** (Tyler Akidau): Foundational concepts
- **Flink Docs**: https://nightlies.apache.org/flink/flink-docs-stable/
- **Spark Structured Streaming**: https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html
- **Dataflow Model Paper**: Original watermark concepts
