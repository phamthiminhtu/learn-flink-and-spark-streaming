"""
Exercise 4.1: Session Analytics with 30-minute Gap (Spark Implementation)

Spark doesn't have built-in session windows, so we implement custom logic:
1. Use window functions to calculate time since last event
2. Identify session boundaries (gap > 30 minutes)
3. Assign session IDs
4. Aggregate session metrics

Key Concepts:
- Window functions (lag, sum over partition)
- Custom session logic with watermarks
- Stateful streaming with groupBy
"""

from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, from_json, to_timestamp, lag, sum as _sum, when,
    count, countDistinct, min as _min, max as _max,
    first, last, concat, lit, round as _round
)
from pyspark.sql.window import Window
from pyspark.sql.types import StructType, StructField, StringType, LongType
import sys
import os

# Add parent directory to path to import schema_utils
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from schema_utils import load_avro_schema_as_spark

# Initialize Spark
spark = SparkSession.builder \
    .appName("SparkSessionAnalytics") \
    .config("spark.sql.streaming.schemaInference", "true") \
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension") \
    .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog") \
    .config("spark.hadoop.fs.s3a.endpoint", "http://minio:9000") \
    .config("spark.hadoop.fs.s3a.access.key", "admin") \
    .config("spark.hadoop.fs.s3a.secret.key", "password123") \
    .config("spark.hadoop.fs.s3a.path.style.access", "true") \
    .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem") \
    .config("spark.hadoop.fs.s3a.aws.credentials.provider",
            "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider") \
    .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false") \
    .getOrCreate()

spark.sparkContext.setLogLevel("WARN")

print("=" * 80)
print("Session Analytics Processor (Spark)")
print("Session gap: 30 minutes")
print("=" * 80)

# Load schema
schema = load_avro_schema_as_spark("/opt/spark-data/schemas/clickstream-event-readable.avsc")

# Read from Kafka
df = spark.readStream \
    .format("kafka") \
    .option("kafka.bootstrap.servers", "kafka:29092") \
    .option("subscribe", "clickstream") \
    .option("startingOffsets", "earliest") \
    .load()

# Parse events
events = df.select(
    from_json(col("value").cast("string"), schema).alias("data")
).select("data.*") \
    .withColumn("event_timestamp", to_timestamp(col("timestamp") / 1000))

# Add watermark (10-minute tolerance for late events)
events = events.withWatermark("event_timestamp", "10 minutes")

# Step 1: Calculate time since last event per user
# This uses a window function over user_id partition ordered by event_timestamp
user_window = Window.partitionBy("user_id").orderBy("event_timestamp")

events_with_gaps = events.withColumn(
    "prev_event_timestamp",
    lag("event_timestamp", 1).over(user_window)
).withColumn(
    "time_since_last_seconds",
    when(
        col("prev_event_timestamp").isNotNull(),
        col("event_timestamp").cast("long") - col("prev_event_timestamp").cast("long")
    ).otherwise(0)
).withColumn(
    # Mark as new session if gap > 30 minutes (1800 seconds)
    "is_new_session",
    when(col("time_since_last_seconds") > 1800, 1).otherwise(0)
)

# Step 2: Assign session IDs by cumulative sum of new session markers
events_with_sessions = events_with_gaps.withColumn(
    "session_number",
    _sum("is_new_session").over(
        Window.partitionBy("user_id").orderBy("event_timestamp")
            .rowsBetween(Window.unboundedPreceding, Window.currentRow)
    )
).withColumn(
    # Create unique session ID
    "session_id",
    concat(col("user_id"), lit("-"), col("session_number"))
)

# Step 3: Aggregate session metrics
# Note: In streaming, we use groupBy without time window for session aggregation
# The watermark ensures we don't wait indefinitely for late events
session_metrics = events_with_sessions.groupBy(
    "session_id",
    "user_id"
).agg(
    _min("event_timestamp").alias("start_time"),
    _max("event_timestamp").alias("end_time"),
    count("*").alias("event_count"),
    countDistinct("page_url").alias("pages_visited"),
    first("event_type").alias("first_event_type"),
    last("event_type").alias("last_event_type"),
    first("device_type").alias("device_type"),
    first("country").alias("country")
).withColumn(
    "duration_minutes",
    _round(
        (col("end_time").cast("long") - col("start_time").cast("long")) / 60.0,
        2
    )
)

# Select final columns to match Flink output
session_output = session_metrics.select(
    "session_id",
    "user_id",
    "start_time",
    "end_time",
    "duration_minutes",
    "event_count",
    "pages_visited",
    "first_event_type",
    "last_event_type",
    "device_type",
    "country"
)

# Write to Delta Lake
print("\nWriting session metrics to Delta Lake...")
print("   Output: s3a://lakehouse/user-sessions-delta/")
print("   Checkpoint: s3a://checkpoints/user-sessions-delta/")
print("   Trigger: 30 seconds")
print("\nPress Ctrl+C to stop\n")

query = session_output.writeStream \
    .format("delta") \
    .outputMode("complete") \
    .option("path", "s3a://lakehouse/user-sessions-delta/") \
    .option("checkpointLocation", "s3a://checkpoints/user-sessions-delta/") \
    .trigger(processingTime='30 seconds') \
    .start()

try:
    query.awaitTermination()
except KeyboardInterrupt:
    print("\nStopping query...")
    query.stop()
    spark.stop()
    print("Spark stopped")

# LIMITATIONS OF THIS APPROACH:
# 1. Window functions in streaming require careful state management
# 2. "complete" output mode stores entire result table (can grow large)
# 3. For production, consider using arbitrary stateful operations (mapGroupsWithState)
#    which gives more control over session state and timeouts
#
# ALTERNATIVE APPROACH (more production-ready):
# Use flatMapGroupsWithState with custom session state management
# This allows explicit timeout handling and state cleanup
