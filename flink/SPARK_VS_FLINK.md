# Spark vs Flink: Side-by-Side Comparison

This document compares the Spark and Flink implementations of the clickstream processor, highlighting the different approaches and concepts.

## Architecture Comparison

### Spark Structured Streaming
- **Processing Model**: Micro-batch (30-second intervals)
- **API**: DataFrame/SQL-based, declarative
- **Type System**: Semi-structured (DataFrame with schema)
- **Output**: Delta Lake (ACID transactions, schema evolution)

### Flink DataStream API
- **Processing Model**: True streaming (event-by-event)
- **API**: DataStream, functional/imperative
- **Type System**: Strongly-typed POJOs
- **Output**: JSONL files (can integrate with Iceberg/Hudi)

## Code Comparison

### 1. Reading from Kafka

#### Spark
```python
df = spark.readStream \
    .format("kafka") \
    .option("kafka.bootstrap.servers", "localhost:9092") \
    .option("subscribe", "clickstream") \
    .option("startingOffsets", "earliest") \
    .load()
```

#### Flink
```java
KafkaSource<ClickstreamEvent> source = KafkaSource.<ClickstreamEvent>builder()
    .setBootstrapServers("localhost:9092")
    .setTopics("clickstream")
    .setStartingOffsets(OffsetsInitializer.earliest())
    .setValueOnlyDeserializer(new ClickstreamEventDeserializationSchema())
    .build();
```

**Key Differences:**
- Spark uses format options (string-based)
- Flink uses builder pattern with type-safe configuration
- Flink requires custom deserialization schema
- Spark auto-detects schema or uses schema inference

---

### 2. Schema Definition

#### Spark
```python
# Load from Avro schema
schema = load_avro_schema_as_spark("data/schemas/clickstream-event-readable.avsc")

# Apply to DataFrame
events = df.select(
    from_json(col("value").cast("string"), schema).alias("data")
).select("data.*")
```

#### Flink
```java
// Define POJO class
public class ClickstreamEvent implements Serializable {
    @JsonProperty("event_id")
    private String eventId;
    // ... other fields with getters/setters
}

// Deserialization happens automatically with Jackson
```

**Key Differences:**
- Spark: Runtime schema validation (DataFrame)
- Flink: Compile-time type checking (POJO)
- Spark: Schema from Avro file
- Flink: Java class definition

---

### 3. Event Time & Watermarks

#### Spark
```python
events = events \
    .withColumn("event_timestamp", to_timestamp(col("timestamp") / 1000))

# Watermark is implicit in window function
# Or can be explicit with:
events.withWatermark("event_timestamp", "10 minutes")
```

#### Flink
```java
DataStream<ClickstreamEvent> events = env.fromSource(
    source,
    WatermarkStrategy
        .<ClickstreamEvent>forBoundedOutOfOrderness(Duration.ofMinutes(10))
        .withTimestampAssigner((event, timestamp) -> event.getTimestamp()),
    "Kafka Source"
);
```

**Key Differences:**
- Spark: Watermark as DataFrame transformation
- Flink: Watermark as part of source creation
- Spark: String-based duration ("10 minutes")
- Flink: Type-safe Duration objects
- Flink: Explicit timestamp extractor function

---

### 4. Windowing

#### Spark
```python
events = events \
    .withColumn("time_window", window(col("event_timestamp"), "5 minutes")) \
    .select(
        "*",
        col("time_window.start").alias("event_timestamp_start"),
        col("time_window.end").alias("event_timestamp_end")
    ).drop("time_window")
```

#### Flink
```java
DataStream<EnrichedClickstreamEvent> enrichedEvents = events
    .keyBy(ClickstreamEvent::getUserId)
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .process(new ProcessWindowFunction<>() {
        public void process(String key, Context context,
                          Iterable<ClickstreamEvent> elements,
                          Collector<EnrichedClickstreamEvent> out) {
            long windowStart = context.window().getStart();
            long windowEnd = context.window().getEnd();
            // Enrich and emit events
        }
    });
```

**Key Differences:**
- Spark: Window as a column operation
- Flink: Window as a stream transformation operator
- Spark: Automatic window metadata in struct
- Flink: Manual access via context in ProcessFunction
- Flink: Requires keying for parallel processing
- Spark: Implicit parallelization

---

### 5. Writing Output

#### Spark
```python
query = events.writeStream \
    .format("delta") \
    .outputMode("append") \
    .option("path", "s3a://lakehouse/clickstream-events-delta/") \
    .option("checkpointLocation", "s3a://checkpoints/clickstream-events-delta/") \
    .trigger(processingTime='30 seconds') \
    .start()

query.awaitTermination()
```

#### Flink
```java
FileSink<EnrichedClickstreamEvent> sink = FileSink
    .forRowFormat(new Path(OUTPUT_PATH), new EnrichedEventEncoder())
    .build();

enrichedEvents.sinkTo(sink);

env.execute("Flink Clickstream Processor");
```

**Key Differences:**
- Spark: Built-in Delta Lake support
- Flink: Generic FileSink (requires custom encoder)
- Spark: Trigger controls micro-batch frequency
- Flink: Continuous processing (checkpointing separate)
- Spark: Query object for lifecycle management
- Flink: Job submitted to execution environment

---

## Checkpointing & Fault Tolerance

### Spark
```python
# Checkpointing configured via options
.option("checkpointLocation", "s3a://checkpoints/...")

# Trigger defines micro-batch interval
.trigger(processingTime='30 seconds')
```

### Flink
```java
// Explicit checkpoint configuration
env.enableCheckpointing(30000, CheckpointingMode.EXACTLY_ONCE);
env.getCheckpointConfig().setCheckpointStorage(CHECKPOINT_PATH);
env.getCheckpointConfig().setMinPauseBetweenCheckpoints(10000);

// Restart strategy
env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, Time.seconds(10)));
```

**Key Differences:**
- Spark: Checkpointing tied to output sink
- Flink: Checkpointing is environment-level configuration
- Spark: Implicit fault tolerance
- Flink: Explicit restart strategies and checkpoint tuning
- Flink: More granular control over checkpoint behavior

---

## Processing Guarantees

| Aspect | Spark | Flink |
|--------|-------|-------|
| **Exactly-Once** | ✅ Via Delta Lake | ✅ Via distributed snapshots |
| **Mechanism** | Checkpoint + idempotent writes | Chandy-Lamport algorithm |
| **State** | Implicit in aggregations | Explicit state management |
| **Recovery** | Replay from checkpoint | Restore from snapshot |

---

## Performance Characteristics

### Latency

| Metric | Spark | Flink |
|--------|-------|-------|
| **Typical Latency** | Seconds (micro-batch) | Milliseconds (streaming) |
| **Best Case** | ~1 second (small batches) | ~10 milliseconds |
| **Configuration** | Trigger interval (30s default) | Network buffer timeout |

### Throughput

Both can handle high throughput, but:
- **Spark**: Better for very high throughput with acceptable latency (e.g., 100K events/sec with 30s latency)
- **Flink**: Better for high throughput with low latency (e.g., 50K events/sec with 100ms latency)

---

## When to Use Which?

### Use Spark Structured Streaming When:
- ✅ You need Delta Lake features (ACID, time travel, schema evolution)
- ✅ SQL/DataFrame API is preferred (data analysts)
- ✅ Micro-batch latency (seconds) is acceptable
- ✅ You already have Spark infrastructure
- ✅ Batch + streaming unification is important
- ✅ PySpark is preferred over JVM languages

### Use Flink DataStream API When:
- ✅ Low latency is critical (milliseconds)
- ✅ Complex event processing (CEP) is needed
- ✅ Stateful processing with large state
- ✅ Fine-grained control over windowing/watermarks
- ✅ True streaming semantics are required
- ✅ Integration with Kafka ecosystem (KSQL alternative)
- ✅ You need advanced features (process functions, timers, side outputs)

---

## Core Concepts Mapping

| Concept | Spark | Flink |
|---------|-------|-------|
| **Stream** | DataFrame | DataStream |
| **Transformation** | `select`, `filter`, `groupBy` | `map`, `filter`, `keyBy` |
| **Time** | Event time column | Event time characteristic |
| **Watermark** | `withWatermark()` | `WatermarkStrategy` |
| **Window** | `window()` function | Window operators |
| **Aggregation** | `agg()`, `count()` | `aggregate()`, `reduce()` |
| **Stateful** | Implicit in agg | Explicit `MapState`, etc. |
| **Output** | `writeStream` | `addSink()` / `sinkTo()` |

---

---

## Why Flink Seems More Complicated: Deep Dive

### Question 1: "Why do we need model classes?"

#### Answer: Different Data Abstractions

**Spark uses DataFrames (schema-first):**
```python
# Schema is separate from data (like a database table)
schema = StructType([
    StructField("event_id", StringType()),
    StructField("user_id", StringType()),
])

# Apply schema to data
df = df.select(from_json(col("value"), schema))

# Access fields dynamically by name (runtime lookup)
df.select("event_id", "user_id")
```

- Schema is a **runtime object** (StructType)
- Fields accessed by **string names** (no compile-time checking)
- Can add/remove fields **dynamically** at runtime

**Flink uses POJOs (class-first):**
```java
// Schema IS the class definition
public class ClickstreamEvent implements Serializable {
    private String eventId;  // Field defined at compile-time
    private String userId;

    public String getEventId() { return eventId; }
    public String getUserId() { return userId; }
}

// Access fields via methods (compile-time checking)
String id = event.getEventId();  // IDE autocomplete!
```

- Schema is **embedded in class structure** (Java class)
- Fields accessed via **methods** (compile-time checking)
- **Cannot change** structure at runtime (rigid but safe)

**Why this difference?**
- Spark: Designed for **SQL-like operations** (flexible schema like databases)
- Flink: Designed for **Java/Scala processing** (type-safe like programming)

---

### Question 2: "Why do we need explicit serialization?"

#### Answer: Control vs Convenience

**What is serialization?**
Converting objects to bytes (and back) for:
1. Sending over network to other machines
2. Saving to disk (checkpoints)
3. Storing in memory efficiently

**Spark: Automatic Magic**
```python
# Spark handles ALL serialization automatically
df = df.filter(lambda row: row.user_id.startswith("user_"))

# Behind the scenes:
# 1. Spark knows schema from DataFrame metadata
# 2. Uses Tungsten binary format (optimized columnar storage)
# 3. Code generation for fast serialization
# 4. All transparent to you!
```

**Why it works:**
- DataFrames have **uniform structure** (every row = same schema)
- Spark **controls the format** (Tungsten)
- No choices needed (one-size-fits-all)

**Flink: Explicit Control**
```java
// Input serialization (you define it)
public class ClickstreamEventDeserializationSchema
    implements DeserializationSchema<ClickstreamEvent> {

    @Override
    public ClickstreamEvent deserialize(byte[] bytes) {
        // YOU choose how to convert bytes → object
        // Could be JSON, Avro, Protobuf, custom binary, etc.
        return objectMapper.readValue(bytes, ClickstreamEvent.class);
    }
}

// Output serialization (you define it)
public class EnrichedEventEncoder
    implements Encoder<EnrichedClickstreamEvent> {

    @Override
    public void encode(EnrichedClickstreamEvent event, OutputStream out) {
        // YOU choose output format
        // We chose JSONL, but could be Parquet, ORC, Avro, etc.
        out.write(objectMapper.writeValueAsBytes(event));
        out.write('\n');  // JSONL = JSON + newline
    }
}
```

**Why explicit?**
- Flink doesn't assume your **input format** (could be anything)
- Flink doesn't assume your **output format** (could be anything)
- You get **full control** over serialization
- Trade-off: More code, but more flexibility

**Internal serialization:**
Flink DOES handle serialization automatically for POJOs **between operators** using its TypeInformation system. You only need custom serialization for I/O boundaries (sources/sinks).

---

### Question 3: "Why do we need models for just getting Avro schema?"

#### Answer: Flink doesn't use schemas like Spark does

**Spark Approach: Schema as First-Class Object**

```python
# 1. Load Avro schema file (JSON)
with open("clickstream-event.avsc") as f:
    avro_schema = json.load(f)

# 2. Convert to Spark schema (runtime object)
spark_schema = load_avro_schema_as_spark(avro_schema)
# Returns: StructType(StructField("event_id", StringType()), ...)

# 3. Use schema for parsing JSON
df = df.select(from_json(col("value"), spark_schema).alias("data"))

# 4. Access any field dynamically
df.select("data.event_id", "data.user_id")

# 5. Add new fields at runtime
df = df.withColumn("new_field", lit("value"))
```

**Key insight:** Schema is a **data structure** that Spark uses at runtime to:
- Parse JSON
- Validate types
- Enable SQL queries
- Store data efficiently

**Flink Approach: No Runtime Schema Concept**

```java
// Flink doesn't have a "schema" object!
// Instead, it uses Java classes directly:

// 1. Define class (this IS your schema)
public class ClickstreamEvent {
    private String eventId;
    private String userId;
    // ...
}

// 2. Jackson reads JSON directly into class
ObjectMapper mapper = new ObjectMapper();
ClickstreamEvent event = mapper.readValue(json, ClickstreamEvent.class);

// 3. Access fields via methods
String id = event.getEventId();

// 4. Cannot add fields at runtime (compile-time structure)
```

**Key insight:** Flink uses Java's **type system** instead of runtime schemas:
- No schema objects
- Class structure = schema
- Type checking at compile time
- Cannot change dynamically

**Why the difference?**

| Aspect | Spark | Flink |
|--------|-------|-------|
| **Design goal** | SQL-like data processing | Event-driven programming |
| **Primary API** | DataFrame (table abstraction) | DataStream (event stream) |
| **Schema concept** | Runtime metadata | Compile-time types |
| **Type checking** | Runtime | Compile-time |
| **Flexibility** | Dynamic (add columns anytime) | Static (fixed at compile) |
| **Safety** | Runtime errors | Compile-time errors |

---

### The Real Trade-off: Code Volume

Let's count lines of code for the same functionality:

#### Spark (total: ~100 lines)
```
spark_clickstream_events.py     ~50 lines
schema_utils.py                 ~50 lines
──────────────────────────────────────
Total:                          ~100 lines
```

#### Flink (total: ~720 lines)
```
ClickstreamProcessor.java                       ~180 lines
ClickstreamEvent.java                           ~200 lines
EnrichedClickstreamEvent.java                   ~230 lines
ClickstreamEventDeserializationSchema.java      ~60 lines
EnrichedEventEncoder.java                       ~50 lines
──────────────────────────────────────────────────────
Total:                                          ~720 lines
```

**Flink requires 7x more code for the same task!**

**Why?**
1. **POJOs require boilerplate:**
   - Private fields
   - Constructors (2 per class)
   - Getters (1 per field)
   - Setters (1 per field)
   - equals/hashCode/toString

2. **Explicit serialization:**
   - Custom deserializer for Kafka input
   - Custom encoder for file output

3. **Type conversions:**
   - Separate model for enriched events
   - Factory method to convert between models

**Can we reduce Flink boilerplate?**

Yes! Using **Lombok** and **Avro code generation**:

```java
// With Lombok (90% less boilerplate)
import lombok.Data;

@Data  // Auto-generates getters, setters, equals, hashCode, toString
public class ClickstreamEvent implements Serializable {
    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("user_id")
    private String userId;
    // ... just field declarations, no methods needed!
}
```

With Lombok, your POJO goes from 200 lines → 50 lines!

---

### Why Not Just Use Spark Then?

**Spark wins on simplicity, but Flink offers:**

1. **True Streaming (not micro-batches)**
   - Spark: Processes in 30-second batches
   - Flink: Processes events as they arrive (millisecond latency)

2. **Advanced Features**
   - Complex event processing (CEP)
   - Stateful processing with timers
   - Side outputs
   - Custom window triggers

3. **Performance Control**
   - Fine-tune checkpointing
   - Custom serializers for performance
   - Memory management control

4. **Type Safety**
   - Catch errors at compile time (before deployment!)
   - IDE autocomplete and refactoring
   - No runtime surprises

**Example: Typo in field name**

Spark (runtime error):
```python
df.select("event_idd")  # Typo! But code compiles
# ERROR at runtime: "Column 'event_idd' does not exist"
```

Flink (compile error):
```java
event.getEventIdd()  // Typo! Won't compile
// ERROR: Cannot resolve method 'getEventIdd()'
```

---

## Summary

**Spark Structured Streaming** is ideal for:
- Unified batch/streaming pipelines
- SQL-based analytics
- Delta Lake integration
- Python-first teams
- **Rapid development** (7x less code!)
- **Dynamic schemas** (add fields at runtime)

**Flink DataStream API** is ideal for:
- Real-time event processing
- Low-latency requirements
- Complex stateful processing
- Java/Scala teams
- **Type safety** (compile-time checking)
- **Performance control** (custom serialization)

Both frameworks are excellent choices for stream processing. The decision depends on your specific requirements for latency, existing infrastructure, team expertise, and feature needs.

In this project, both implementations achieve the same goal (process clickstream events with windowing), but demonstrate different paradigms:
- **Spark**: High-level, declarative, batch-like (schema-first, dynamic)
- **Flink**: Low-level, imperative, streaming-native (class-first, static)
