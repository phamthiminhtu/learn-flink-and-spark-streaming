"""
Exercise 3.1: Sliding Windows - Moving Average

Calculate rolling metrics using 10-minute windows sliding every 2 minutes

Key Concepts:
- Overlapping windows
- Same event appears in multiple windows
- Slide interval < window size creates overlap
- Useful for smoothing metrics and trend detection
"""

from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, from_json, to_timestamp, window, count, avg, round as _round
)
import sys
import os

# Add parent directory to path to import schema_utils
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from schema_utils import load_avro_schema_as_spark

spark = SparkSession.builder \
    .appName("SparkSlidingWindowExercise") \
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
print("Sliding Window Exercise (Spark)")
print("Window: 10 minutes, Slide: 2 minutes")
print("=" * 80)

# Load schema and read from Kafka
schema = load_avro_schema_as_spark("/opt/spark-data/schemas/clickstream-event-readable.avsc")

df = spark.readStream \
    .format("kafka") \
    .option("kafka.bootstrap.servers", "kafka:29092") \
    .option("subscribe", "clickstream") \
    .option("startingOffsets", "earliest") \
    .load()

events = df.select(
    from_json(col("value").cast("string"), schema).alias("data")
).select("data.*") \
    .withColumn("event_timestamp", to_timestamp(col("timestamp") / 1000))

events = events.withWatermark("event_timestamp", "10 minutes")

# ============ Exercise 3.1: Moving Average of Events per User ============

print("\nCalculating rolling event count per user...")
print("Each event will appear in ~5 windows (10min / 2min)")

# 10-minute window sliding every 2 minutes
# windowDuration = "10 minutes"
# slideDuration = "2 minutes"
user_event_counts = events.groupBy(
    window(col("event_timestamp"), "10 minutes", "2 minutes"),
    col("user_id")
).agg(
    count("*").alias("event_count")
).select(
    col("window.start").alias("window_start"),
    col("window.end").alias("window_end"),
    col("user_id"),
    col("event_count")
)

# Write to Delta Lake
query1 = user_event_counts.writeStream \
    .format("delta") \
    .outputMode("append") \
    .option("path", "s3a://lakehouse/user-events-sliding-delta/") \
    .option("checkpointLocation", "s3a://checkpoints/user-events-sliding-delta/") \
    .trigger(processingTime='30 seconds') \
    .start()

print("   Output: s3a://lakehouse/user-events-sliding-delta/")

# ============ Exercise 3.2: Anomaly Detection - High Click Rate ============

print("\nDetecting anomalies in click rate...")
print("Alert when window click rate > 2x baseline")

# Calculate click rate per 5-minute sliding windows (slide every 1 minute)
click_rates = events \
    .filter(col("event_type") == "click") \
    .groupBy(
        window(col("event_timestamp"), "5 minutes", "1 minute")
    ).agg(
        count("*").alias("click_count")
    ).select(
        col("window.start").alias("window_start"),
        col("window.end").alias("window_end"),
        col("click_count"),
        # Calculate rate per minute
        _round(col("click_count") / 5.0, 2).alias("clicks_per_minute")
    )

# For anomaly detection, you would:
# 1. Calculate baseline (e.g., average over last hour)
# 2. Compare current window to baseline
# 3. Alert if current > threshold * baseline
#
# In practice, use arbitrary stateful operations to maintain baseline state

query2 = click_rates.writeStream \
    .format("delta") \
    .outputMode("append") \
    .option("path", "s3a://lakehouse/click-rates-sliding-delta/") \
    .option("checkpointLocation", "s3a://checkpoints/click-rates-sliding-delta/") \
    .trigger(processingTime='30 seconds') \
    .start()

print("   Output: s3a://lakehouse/click-rates-sliding-delta/")

print("\n" + "=" * 80)
print("Queries running. Press Ctrl+C to stop")
print("=" * 80)
print("\nAnalysis Questions:")
print("1. How many windows does a single event appear in?")
print("   Answer: window_size / slide_interval = 10 / 2 = 5 windows")
print("2. What if window_size = slide_interval?")
print("   Answer: Becomes a tumbling window (no overlap)")
print("3. Trade-off?")
print("   Answer: More windows = more computation, but smoother metrics")
print()

try:
    spark.streams.awaitAnyTermination()
except KeyboardInterrupt:
    print("\nStopping queries...")
    query1.stop()
    query2.stop()
    spark.stop()
    print("Spark stopped")

# ============ Analysis Queries (Run separately) ============
"""
# Observe how a single event appears in multiple windows:

sliding_data = spark.read.format("delta") \
    .load("s3a://lakehouse/user-events-sliding-delta/")

# Pick a specific timestamp and see which windows it falls into
from pyspark.sql.functions import lit
specific_time = "2024-10-28 10:15:00"

overlapping_windows = sliding_data.filter(
    (col("window_start") <= lit(specific_time)) &
    (col("window_end") > lit(specific_time))
).select("window_start", "window_end").distinct().orderBy("window_start")

print(f"Windows containing event at {specific_time}:")
overlapping_windows.show(truncate=False)

# Compare sliding vs tumbling for same metric:
tumbling = events.groupBy(
    window("event_timestamp", "10 minutes")
).count()

sliding = events.groupBy(
    window("event_timestamp", "10 minutes", "2 minutes")
).count()

# Tumbling will have fewer rows (non-overlapping)
# Sliding will have more rows (overlapping) but smoother trends
"""
