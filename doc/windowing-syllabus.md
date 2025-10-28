# Stream Processing Windows: Comprehensive Syllabus

## Overview
This syllabus provides a structured learning path for mastering windowing concepts in stream processing using Apache Flink and Apache Spark. Each module includes theory, implementation patterns, and hands-on exercises using your existing clickstream event infrastructure.

## Prerequisites
- Basic understanding of stream processing concepts
- Java/Scala knowledge (for Flink)
- Python knowledge (for Spark)
- Familiarity with Kafka
- Docker and Docker Compose

---

## Module 1: Time in Stream Processing (Foundation)

### Learning Objectives
- Understand the difference between event time, processing time, and ingestion time
- Recognize why event time is critical for accurate analytics
- Identify scenarios where out-of-order events occur

### Theory
**Event Time vs Processing Time**
- **Event Time**: When the event actually occurred (timestamp field in the event)
- **Processing Time**: When the system processes the event
- **Ingestion Time**: When the event enters the streaming system

**Why Event Time Matters**
```
Event A: occurred at 10:00:00, processed at 10:05:00
Event B: occurred at 10:00:05, processed at 10:04:00

Processing time order: B → A
Event time order: A → B

For accurate analytics, we need event time order!
```

### Hands-on Exercise 1.1: Observing Out-of-Order Events
**Objective**: Observe the difference between event time and processing time

**Task**:
1. Examine your existing clickstream events in Postgres
2. Compare `event_timestamp` (event time) vs `timestamp` (processing time)
3. Identify out-of-order events

**SQL Query**:
```sql
-- Find events that arrived out of order
SELECT
    event_id,
    user_id,
    event_type,
    event_timestamp,
    timestamp as processing_time,
    (timestamp - event_timestamp) as delay_ms
FROM clickstream_events
ORDER BY processing_time
LIMIT 100;
```

**Questions to Answer**:
1. What is the average delay between event time and processing time?
2. What is the maximum delay?
3. Can you find events that arrived more than 1 minute late?

---

## Module 2: Tumbling Windows

### Learning Objectives
- Understand fixed-size, non-overlapping windows
- Implement tumbling windows in both Spark and Flink
- Apply tumbling windows for basic aggregations

### Theory
**Tumbling Windows**: Fixed-size, non-overlapping time intervals

```
Timeline:
|---W1---|---W2---|---W3---|---W4---|
0       5       10      15      20 (minutes)

Each event belongs to exactly ONE window
Window closes after its end time + watermark delay
```

**Use Cases**:
- Hourly/daily metrics (e.g., requests per hour)
- Fixed reporting intervals
- Batch-like processing over streams

### Implementation Patterns

#### Flink (Java)
```java
// Key components:
DataStream<Event> events = ...;

// 1. Assign event time and watermark
events.assignTimestampsAndWatermarks(
    WatermarkStrategy
        .<Event>forBoundedOutOfOrderness(Duration.ofMinutes(10))
        .withTimestampAssigner((event, ts) -> event.getTimestamp())
);

// 2. Apply tumbling window
events
    .keyBy(Event::getUserId)
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .aggregate(new CountAggregator());
```

#### Spark (Python)
```python
# Key components:
events = spark.readStream.format("kafka")...

# 1. Convert timestamp to event time
events = events.withColumn("event_timestamp",
    to_timestamp(col("timestamp") / 1000))

# 2. Apply tumbling window via window() function
windowed = events.groupBy(
    window(col("event_timestamp"), "5 minutes"),
    col("user_id")
).count()
```

### Hands-on Exercise 2.1: Count Events Per 5-Minute Window
**Objective**: Implement a basic tumbling window aggregation

**Task (Flink)**:
1. Modify `ClickstreamProcessor.java` to count events per 5-minute window
2. Group by `event_type`
3. Output counts to Postgres table `event_counts_5min`

**Expected Output**:
```
window_start         | window_end           | event_type | count
2024-10-28 10:00:00 | 2024-10-28 10:05:00 | page_view  | 1523
2024-10-28 10:00:00 | 2024-10-28 10:05:00 | click      | 892
2024-10-28 10:05:00 | 2024-10-28 10:10:00 | page_view  | 1601
```

**Task (Spark)**:
1. Modify `spark_clickstream_events.py` to count events per 5-minute window
2. Group by `event_type`
3. Write to Delta Lake at `s3a://lakehouse/event-counts-5min/`

**Verification**:
- How many windows were created?
- What happens to late events (arriving after window closes)?

### Hands-on Exercise 2.2: Top Pages Per Window
**Objective**: Find most visited pages in each 5-minute window

**Task**:
1. Count page views per `page_url` within 5-minute tumbling windows
2. Rank pages by count within each window
3. Output top 10 pages per window

**Challenge**:
- How would you efficiently implement "top-N per window" in Flink?
- Compare approaches: ProcessWindowFunction vs AggregateFunction + Process

---

## Module 3: Sliding Windows

### Learning Objectives
- Understand overlapping windows
- Configure slide interval vs window size
- Handle the "multiple counting" implication

### Theory
**Sliding Windows**: Fixed-size windows that slide at regular intervals

```
Window size: 10 minutes, Slide: 5 minutes

Timeline:
|------W1------|
     |------W2------|
          |------W3------|
0    5    10   15   20 (minutes)

Events can belong to MULTIPLE windows
More compute-intensive than tumbling windows
```

**Use Cases**:
- Moving averages (e.g., average latency over last 5 minutes, updated every minute)
- Smoothing metrics
- Trend detection

### Implementation Patterns

#### Flink
```java
events
    .keyBy(Event::getUserId)
    .window(SlidingEventTimeWindows.of(
        Time.minutes(10),  // window size
        Time.minutes(5)    // slide interval
    ))
    .aggregate(new AverageAggregator());
```

#### Spark
```python
# Window size: 10 minutes, Slide: 5 minutes
windowed = events.groupBy(
    window(col("event_timestamp"), "10 minutes", "5 minutes"),
    col("user_id")
).agg(avg("latency"))
```

### Hands-on Exercise 3.1: Moving Average of Events
**Objective**: Calculate rolling average of events per user

**Task**:
1. Create 10-minute windows sliding every 2 minutes
2. Count events per user in each window
3. Observe how the count changes as the window slides

**Analysis Questions**:
- How many windows does a single event appear in?
- What is the relationship between window_size and slide_interval?
- If window_size = slide_interval, what do you get?

### Hands-on Exercise 3.2: Anomaly Detection with Sliding Windows
**Objective**: Detect unusual traffic patterns

**Task**:
1. Calculate average click rate per minute over 5-minute windows (slide: 1 minute)
2. Identify windows where click rate > 2x the average
3. Output potential anomalies

**Bonus**:
- Implement z-score calculation for anomaly detection
- Use ProcessWindowFunction to access historical statistics

---

## Module 4: Session Windows

### Learning Objectives
- Understand dynamic, gap-based windows
- Implement session timeout logic
- Handle session merging across multiple events

### Theory
**Session Windows**: Variable-size windows based on inactivity gaps

```
User A's events:
E1      E2  E3         (gap > 30min)      E4      E5
|---Session 1----|                    |---Session 2---|
0    10  15                60             70    75 (minutes)

Session closes after 30 minutes of inactivity
Each user can have multiple concurrent sessions
```

**Use Cases**:
- User session analytics
- Shopping cart sessions (abandoned cart detection)
- Device connection patterns
- Fraud detection (unusual session patterns)

### Implementation Patterns

#### Flink
```java
events
    .keyBy(Event::getUserId)
    .window(EventTimeSessionWindows.withGap(Time.minutes(30)))
    .process(new SessionAnalyzer());
```

#### Spark
```python
# Spark doesn't have built-in session windows
# Must implement using custom logic with watermarks

from pyspark.sql import Window
from pyspark.sql.functions import lag, sum as _sum, when

# Calculate time since last event
w = Window.partitionBy("user_id").orderBy("event_timestamp")
sessions = events.withColumn(
    "time_since_last",
    col("event_timestamp").cast("long") - lag("event_timestamp", 1).over(w).cast("long")
).withColumn(
    "new_session",
    when(col("time_since_last") > 1800, 1).otherwise(0)  # 30 min = 1800 sec
).withColumn(
    "session_id",
    _sum("new_session").over(w)
)
```

### Hands-on Exercise 4.1: Basic Session Identification (DELIVERABLE)
**Objective**: Implement 30-minute timeout session windows

**Task (Flink)**:
1. Create session windows with 30-minute gap
2. Key by `user_id`
3. Calculate per session:
   - Session duration
   - Event count
   - First and last event type
   - Pages visited
4. Store in `user_sessions` table

**Expected Output**:
```
session_id | user_id | start_time | end_time | duration_min | event_count | pages_visited
s1         | user123 | 10:00:00  | 10:25:00 | 25           | 15          | 8
s2         | user123 | 11:00:00  | 11:10:00 | 10           | 5           | 3
```

**Task (Spark)**:
1. Implement custom session logic using windowing functions
2. Identify session boundaries (gap > 30 minutes)
3. Assign session IDs
4. Calculate same metrics as Flink version
5. Write to Delta Lake

**Analysis Questions**:
- What is the average session duration?
- What is the distribution of events per session?
- How many users have more than 5 sessions in the dataset?

### Hands-on Exercise 4.2: Session Analytics Dashboard Data
**Objective**: Create aggregated session metrics for visualization

**Task**:
1. Build session metrics:
   - Sessions per hour
   - Average session duration per hour
   - Bounce rate (sessions with only 1 event)
   - Conversion rate (sessions ending with 'purchase' event)
2. Calculate per device_type and country
3. Store as time-series data for dashboarding

**SQL for Verification**:
```sql
-- Bounce rate analysis
SELECT
    DATE_TRUNC('hour', start_time) as hour,
    COUNT(*) as total_sessions,
    COUNT(*) FILTER (WHERE event_count = 1) as bounce_sessions,
    ROUND(100.0 * COUNT(*) FILTER (WHERE event_count = 1) / COUNT(*), 2) as bounce_rate
FROM user_sessions
GROUP BY hour
ORDER BY hour;
```

### Hands-on Exercise 4.3: Abandoned Cart Detection
**Objective**: Detect user sessions that added items but didn't purchase

**Task**:
1. Filter sessions containing 'add_to_cart' events
2. Check if session ends without 'purchase' event
3. Calculate time since last activity
4. Trigger alerts for carts abandoned > 1 hour

**Business Logic**:
```
Session contains 'add_to_cart': YES
Session contains 'purchase': NO
Time since last event: > 60 minutes
→ Abandoned cart candidate
```

---

## Module 5: Watermarks and Late Data

### Learning Objectives
- Understand watermark semantics
- Configure allowed lateness
- Handle late data with side outputs

### Theory
**Watermarks**: Special timestamps that indicate "all events before this time have arrived"

```
Watermark Strategy:
maxOutOfOrderness = 10 minutes

Event timestamps: 10:00, 10:02, 10:01, 10:03
Processing time:  10:05, 10:06, 10:07, 10:08

Watermark at 10:08: 10:03 - 10:00 = 9:53
Meaning: "All events before 9:53 have arrived"

Window [10:00-10:05] closes when watermark reaches 10:05
```

**Late Data Scenarios**:
1. **On-time**: Event arrives before watermark passes window end
2. **Late but allowed**: Event arrives after watermark but within allowed lateness
3. **Too late**: Event arrives after allowed lateness period → dropped or side output

### Implementation Patterns

#### Flink - Allowed Lateness
```java
events
    .keyBy(Event::getUserId)
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .allowedLateness(Time.minutes(5))  // Accept late data up to 5 min
    .sideOutputLateData(lateDataTag)   // Capture too-late events
    .aggregate(new CountAggregator());
```

#### Spark - Watermarking
```python
# Events arriving more than 10 minutes late are dropped
events.withWatermark("event_timestamp", "10 minutes") \
    .groupBy(window("event_timestamp", "5 minutes"), "user_id") \
    .count()
```

### Hands-on Exercise 5.1: Watermark Tuning
**Objective**: Observe impact of different watermark configurations

**Task**:
1. Implement the same aggregation with different watermark strategies:
   - Strategy A: 5-minute maxOutOfOrderness
   - Strategy B: 30-minute maxOutOfOrderness
   - Strategy C: 1-minute maxOutOfOrderness
2. Track late events for each strategy
3. Measure:
   - Number of events dropped
   - Window closure time
   - Result accuracy

**Analysis**:
- Trade-off: Accuracy vs Latency
- How many events arrive late in your dataset?
- What is the optimal watermark for your use case?

### Hands-on Exercise 5.2: Late Data Handling
**Objective**: Implement comprehensive late data strategy

**Task (Flink)**:
1. Set watermark: 10-minute maxOutOfOrderness
2. Set allowed lateness: 5 minutes
3. Capture late data to side output
4. Write late events to `late_events` table with reason

**Task (Spark)**:
1. Set watermark: 10 minutes
2. Count events before and after watermark
3. Log dropped events (use separate stream without watermark)

**Verification SQL**:
```sql
-- Check late events
SELECT
    event_id,
    event_timestamp,
    processing_timestamp,
    (processing_timestamp - event_timestamp) as lateness,
    'Too late for window' as reason
FROM late_events
ORDER BY lateness DESC
LIMIT 20;
```

---

## Module 6: Advanced Window Patterns

### Learning Objectives
- Use custom triggers for early results
- Implement custom evictors
- Apply ProcessWindowFunction for complex logic

### Theory

**Flink Window Lifecycle**:
```
WindowAssigner: Decides which window(s) an event belongs to
Trigger: Decides when to evaluate the window
Evictor: Decides which elements to keep/remove from window
Function: Computes result (Aggregate, Reduce, Process)
```

**Custom Triggers Use Cases**:
- Early results (before watermark)
- Periodic updates
- Element count-based triggers

**Custom Evictors Use Cases**:
- Keep only recent N elements
- Remove outliers
- Sample events

### Hands-on Exercise 6.1: Global Window with Custom Trigger
**Objective**: Implement count-based trigger in global window

**Task**:
1. Create GlobalWindow (never closes naturally)
2. Implement trigger that fires every 100 events
3. Count events per user
4. Compare to time-based windowing

**Flink Code Structure**:
```java
events
    .keyBy(Event::getUserId)
    .window(GlobalWindows.create())
    .trigger(CountTrigger.of(100))
    .aggregate(new EventCountAggregator());
```

### Hands-on Exercise 6.2: Early, On-time, and Late Results
**Objective**: Emit results at different stages of window lifecycle

**Task**:
1. Configure tumbling 10-minute windows
2. Use trigger to emit:
   - Early result after 5 minutes of processing time
   - On-time result when watermark passes
   - Late updates if late data arrives (within allowed lateness)
3. Tag each result with firing reason

**Expected Output**:
```
window_start | window_end  | count | firing_type | emitted_at
10:00:00    | 10:10:00    | 150   | EARLY       | 10:05:00
10:00:00    | 10:10:00    | 200   | ON_TIME     | 10:20:00
10:00:00    | 10:10:00    | 203   | LATE        | 10:25:00
```

---

## Module 7: Real-World Patterns

### Hands-on Exercise 7.1: Sessionization with Cross-Channel Events
**Objective**: Advanced session analytics across multiple event sources

**Scenario**: Users interact across web, mobile app, and email
- Each source has its own Kafka topic
- Need to unify sessions across channels
- Session gap: 30 minutes

**Task**:
1. Read from multiple Kafka topics (or single topic with channel field)
2. Union streams
3. Apply session windows keyed by user_id
4. Calculate:
   - Cross-channel journey (sequence of touchpoints)
   - Time spent per channel
   - Conversion path analysis

### Hands-on Exercise 7.2: Real-time Funnel Analysis
**Objective**: Track conversion funnel in real-time

**Funnel Stages**:
1. Landing page view
2. Product page view
3. Add to cart
4. Checkout
5. Purchase

**Task**:
1. Use session windows to track user journey
2. Identify which stage users drop off
3. Calculate conversion rate per stage per hour
4. Track average time between stages

**Output Metrics**:
```
hour    | stage        | users_entered | users_converted | conversion_rate | avg_time_to_next
10:00   | landing      | 1000         | 400            | 40%            | 30s
10:00   | product      | 400          | 250            | 62.5%          | 120s
10:00   | add_cart     | 250          | 100            | 40%            | 180s
10:00   | checkout     | 100          | 80             | 80%            | 300s
10:00   | purchase     | 80           | 80             | 100%           | 0s
```

### Hands-on Exercise 7.3: Streaming Join with Windowing
**Objective**: Join clickstream with user profile updates

**Scenario**:
- Stream A: Clickstream events (high volume)
- Stream B: User profile updates (low volume)
- Goal: Enrich clickstream with latest user profile within 5-minute window

**Task (Flink)**:
1. Create interval join between streams
2. Window: 5 minutes
3. Join condition: user_id match, timestamps within window
4. Output enriched events

**Flink Code**:
```java
clickstream
    .keyBy(Event::getUserId)
    .intervalJoin(profiles.keyBy(Profile::getUserId))
    .between(Time.minutes(-5), Time.minutes(0))
    .process(new EnrichmentProcessor());
```

---

## Module 8: Performance and Optimization

### Learning Objectives
- Optimize window state size
- Choose appropriate window functions
- Monitor backpressure and late events

### Best Practices

**1. Choose Right Window Function**
- Use `AggregateFunction` for incremental aggregation (most efficient)
- Use `ProcessWindowFunction` only when you need window metadata
- Combine both for optimal performance:
  ```java
  .aggregate(new SumAggregator(), new WindowMetadataProcessor())
  ```

**2. State Management**
- Session windows can accumulate large state
- Use TTL (Time-To-Live) for state cleanup:
  ```java
  StateTtlConfig ttlConfig = StateTtlConfig
      .newBuilder(Time.hours(24))
      .setUpdateType(UpdateType.OnCreateAndWrite)
      .build();
  ```

**3. Parallelism Tuning**
- Tumbling windows: High parallelism works well
- Session windows: Limited by key distribution
- Monitor task manager metrics

### Hands-on Exercise 8.1: Performance Benchmarking
**Objective**: Compare performance of different window implementations

**Task**:
1. Implement same count aggregation using:
   - AggregateFunction
   - ReduceFunction
   - ProcessWindowFunction
2. Measure for each:
   - Processing latency
   - Memory usage (state size)
   - Throughput (events/sec)
3. Test with varying data volumes

**Metrics Collection**:
- Use Flink Web UI metrics
- Track checkpoint size
- Monitor GC behavior

---

## Module 9: Testing and Validation

### Learning Objectives
- Write unit tests for windowing logic
- Use event time test harness
- Validate watermark handling

### Testing Strategy

**Unit Tests (Flink)**:
```java
@Test
public void testTumblingWindowCount() {
    // Setup test harness
    OneInputStreamOperatorTestHarness<Event, WindowResult> harness =
        new KeyedOneInputStreamOperatorTestHarness<>(...);

    // Add test events with specific timestamps
    harness.processElement(new StreamRecord<>(event1, 1000L));
    harness.processElement(new StreamRecord<>(event2, 2000L));

    // Advance watermark
    harness.processWatermark(new Watermark(10000L));

    // Assert output
    assertEquals(expectedCount, harness.extractOutputValues().size());
}
```

**Integration Tests**:
- Use test Kafka cluster (Testcontainers)
- Generate controlled event sequences
- Verify window results end-to-end

### Hands-on Exercise 9.1: Test Suite for Session Windows
**Objective**: Create comprehensive tests for session window logic

**Test Cases**:
1. Single session with multiple events
2. Multiple sessions with proper gaps
3. Late events within allowed lateness
4. Late events beyond allowed lateness
5. Concurrent sessions for different users

---

## Final Project: E-commerce Session Analytics Platform

### Objective
Build a production-ready session analytics system that demonstrates all learned concepts.

### Requirements

**1. Data Pipeline**
- Ingest clickstream events from Kafka
- Support 3 event types: page_view, add_to_cart, purchase
- Handle 10k+ events/second

**2. Session Processing**
- 30-minute session gap
- Track session metrics:
  - Duration
  - Page views
  - Cart additions
  - Purchase amount
  - Device & location

**3. Real-time Metrics**
- Active sessions (last 5 minutes)
- Conversion rate (last hour, sliding every 5 min)
- Abandoned carts (last 2 hours)
- Top products viewed (tumbling 15-min windows)

**4. Late Data Handling**
- 15-minute watermark
- 10-minute allowed lateness
- Log all dropped late events

**5. Output**
- Session details: Postgres table
- Real-time metrics: Postgres (updated by Flink)
- Historical data: Delta Lake on MinIO (Spark batch)
- Late events: Separate tracking table

**6. Monitoring**
- Track processing lag
- Monitor late event rate
- Alert on anomalies (session spikes)

### Deliverables
1. Flink job for real-time session processing
2. Spark job for historical analysis and backfill
3. SQL queries for analytics
4. Test suite (unit + integration)
5. Performance benchmark report
6. Documentation with architecture diagram

### Evaluation Criteria
- Correctness of session identification
- Proper watermark configuration
- Efficient state management
- Comprehensive testing
- Code quality and documentation

---

## Additional Resources

### Documentation
- Flink Windows: https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/operators/windows/
- Spark Structured Streaming: https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html
- Watermark Strategies: https://flink.apache.org/learn-flink/streaming-analytics/time-based-operations.html

### Books
- "Stream Processing with Apache Flink" by Fabian Hueske and Vasiliki Kalavri
- "Streaming Systems" by Tyler Akidau, Slava Chernyak, and Reuven Lax

### Practice Datasets
- Your existing clickstream generator
- Public datasets: NYC Taxi, Wikipedia edits, Twitter stream

---

## Appendix: Quick Reference

### Flink Window Types
```java
// Tumbling
TumblingEventTimeWindows.of(Time.minutes(5))

// Sliding
SlidingEventTimeWindows.of(Time.minutes(10), Time.minutes(5))

// Session
EventTimeSessionWindows.withGap(Time.minutes(30))

// Global (with custom trigger)
GlobalWindows.create()
```

### Spark Window Syntax
```python
# Tumbling
window("event_timestamp", "5 minutes")

# Sliding
window("event_timestamp", "10 minutes", "5 minutes")

# Watermark
withWatermark("event_timestamp", "10 minutes")
```

### Common Patterns Cheat Sheet
```
Pattern                  | Window Type | Slide    | Use Case
-------------------------|-------------|----------|------------------
Hourly aggregation       | Tumbling    | N/A      | Daily reports
5-min moving average     | Sliding     | 1 min    | Trend detection
User sessions            | Session     | Gap-based| User analytics
Real-time dashboard      | Tumbling    | N/A      | Live metrics
Anomaly detection        | Sliding     | Small    | Monitoring
```
