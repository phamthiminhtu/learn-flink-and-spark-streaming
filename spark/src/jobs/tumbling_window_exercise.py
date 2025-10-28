"""
Exercise 2.1 & 2.2: Tumbling Windows (Spark Implementation)

Part 1: Count events per 5-minute window grouped by event_type
Part 2: Top pages per window

Key Concepts:
- window() function for tumbling windows
- groupBy with window + key columns
- Writing aggregated results to Delta Lake
"""

from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, from_json, to_timestamp, window, count, desc
)
import sys
import os

# Add parent directory to path to import schema_utils
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from schema_utils import load_avro_schema_as_spark

# Initialize Spark
spark = SparkSession.builder \
    .appName("SparkTumblingWindowExercise") \
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
print("Tumbling Window Exercise (Spark)")
print("Window size: 5 minutes")
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

# Add watermark - allows 10 minutes of late data
events = events.withWatermark("event_timestamp", "10 minutes")

# ============ PART 1: Event Type Counts per 5-minute Window ============

print("\n[Part 1] Counting events by type per 5-minute window...")

# Group by 5-minute tumbling window and event_type
event_type_counts = events.groupBy(
    window(col("event_timestamp"), "5 minutes"),
    col("event_type")
).agg(
    count("*").alias("count")
).select(
    col("window.start").alias("window_start"),
    col("window.end").alias("window_end"),
    col("event_type"),
    col("count")
)

# Write Part 1 to Delta Lake
query1 = event_type_counts.writeStream \
    .format("delta") \
    .outputMode("append") \
    .option("path", "s3a://lakehouse/event-counts-5min-delta/") \
    .option("checkpointLocation", "s3a://checkpoints/event-counts-5min-delta/") \
    .trigger(processingTime='30 seconds') \
    .start()

print("   Output: s3a://lakehouse/event-counts-5min-delta/")

# ============ PART 2: Page View Counts per 5-minute Window ============

print("\n[Part 2] Counting page views per URL per 5-minute window...")

# Filter for page_view events and count by page_url
page_view_counts = events \
    .filter(col("event_type") == "page_view") \
    .groupBy(
        window(col("event_timestamp"), "5 minutes"),
        col("page_url")
    ).agg(
        count("*").alias("count")
    ).select(
        col("window.start").alias("window_start"),
        col("window.end").alias("window_end"),
        col("page_url"),
        col("count")
    )

# Write Part 2 to Delta Lake
query2 = page_view_counts.writeStream \
    .format("delta") \
    .outputMode("append") \
    .option("path", "s3a://lakehouse/page-views-5min-delta/") \
    .option("checkpointLocation", "s3a://checkpoints/page-views-5min-delta/") \
    .trigger(processingTime='30 seconds') \
    .start()

print("   Output: s3a://lakehouse/page-views-5min-delta/")

print("\n" + "=" * 80)
print("Both queries running. Press Ctrl+C to stop")
print("=" * 80)
print()

# Wait for both queries
try:
    spark.streams.awaitAnyTermination()
except KeyboardInterrupt:
    print("\nStopping queries...")
    query1.stop()
    query2.stop()
    spark.stop()
    print("Spark stopped")

# ============ Query Top Pages (Run separately after data is written) ============
"""
To find top 10 pages per window, read the Delta table and use window functions:

from pyspark.sql.functions import row_number
from pyspark.sql.window import Window

page_views = spark.read.format("delta").load("s3a://lakehouse/page-views-5min-delta/")

window_spec = Window.partitionBy("window_start").orderBy(desc("count"))

top_pages = page_views.withColumn("rank", row_number().over(window_spec)) \
    .filter(col("rank") <= 10) \
    .select("window_start", "window_end", "page_url", "count", "rank") \
    .orderBy("window_start", "rank")

top_pages.show(50, truncate=False)
"""
