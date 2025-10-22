"""
Spark Structured Streaming - Kafka Connection Test
Simple word count to verify Spark can read from Kafka
"""

from pyspark.sql import SparkSession
from pyspark.sql.functions import col, from_json, window, to_timestamp
from schema_utils import load_avro_schema_as_spark

# Initialize Spark with Kafka, S3 (MinIO), and Delta Lake dependencies
spark = SparkSession.builder \
    .appName("SparkKafkaConnectionTest") \
    .config("spark.jars.packages",
            "org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,"
            "org.apache.hadoop:hadoop-aws:3.3.4,"
            "io.delta:delta-spark_2.12:3.2.0") \
    .config("spark.jars.repositories", "https://repo1.maven.org/maven2/") \
    .config("spark.jars.ivy", "/tmp/.ivy2") \
    .config("spark.sql.streaming.schemaInference", "true") \
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension") \
    .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog") \
    .config("spark.hadoop.fs.s3a.endpoint", "http://localhost:9000") \
    .config("spark.hadoop.fs.s3a.access.key", "admin") \
    .config("spark.hadoop.fs.s3a.secret.key", "password123") \
    .config("spark.hadoop.fs.s3a.path.style.access", "true") \
    .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem") \
    .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false") \
    .master("local[*]") \
    .getOrCreate()

spark.sparkContext.setLogLevel("WARN")


# Load schema from Avro definition
# This ensures schema stays in sync with the Avro schema file
schema = load_avro_schema_as_spark("data/schemas/clickstream-event-readable.avsc")

print("📋 Loaded schema from Avro:")
print(schema)

print("📖 Reading from Kafka topic: clickstream")

# Read from Kafka
df = spark.readStream \
    .format("kafka") \
    .option("kafka.bootstrap.servers", "localhost:9092") \
    .option("subscribe", "clickstream") \
    .option("startingOffsets", "earliest") \
    .load()

# Parse JSON from Kafka value field using the Avro-based schema
events = df.select(
    col("key").cast("string").alias("user_id_key"),
    from_json(col("value").cast("string"), schema).alias("data")
).select("data.*") \
    .withColumn("event_timestamp", to_timestamp(col("timestamp") / 1000)) \
    .withColumn("time_window", window(col("event_timestamp"), "5 minutes")) \
    .select(
        "*",
        col("time_window.start").alias("event_timestamp_start"),
        col("time_window.end").alias("event_timestamp_end")
    ).drop("time_window")

# # Simple aggregation: count events by type
# event_counts = events \
#     .withWatermark("event_timestamp", "10 minutes") \
#     .groupBy(
#         window(col("event_timestamp"), "5 minutes"),
#         col("event_type")
#     ) \
#     .count()

# Write to MinIO Delta Lake (S3-compatible storage) with checkpointing
print("🔄 Starting streaming query with Delta Lake on MinIO...")
print("   💾 Delta Output: s3a://lakehouse/clickstream-counts-delta/")
print("   🔖 Checkpoint: s3a://checkpoints/clickstream-counts-delta/")
print("   📊 5-minute windows with 10-minute watermark")
print("   ⏰ Windows finalized ~15 minutes after start (append mode)")
print("   Press Ctrl+C to stop\n")

query = events.writeStream \
    .format("delta") \
    .outputMode("append") \
    .option("path", "s3a://lakehouse/clickstream-events-delta/") \
    .option("checkpointLocation", "s3a://checkpoints/clickstream-events-delta/") \
    .trigger(processingTime='30 seconds') \
    .start()  # Append mode: waits for watermark to finalize windows

# Alternative: Real-time running totals (no windows)
# Uncomment to use update mode without windows:
#
# event_counts_realtime = events.groupBy(col("event_type")).count()
#
# query = event_counts_realtime.writeStream \
#     .format("delta") \
#     .outputMode("update") \            # ← Update works without windows
#     .option("path", "s3a://lakehouse/clickstream-realtime/") \
#     .option("checkpointLocation", "s3a://checkpoints/clickstream-realtime/") \
#     .trigger(processingTime='10 seconds') \
#     .start()

try:
    query.awaitTermination()
except KeyboardInterrupt:
    print("\n⚠️  Stopping query...")
    query.stop()
    spark.stop()
    print("✅ Spark stopped")