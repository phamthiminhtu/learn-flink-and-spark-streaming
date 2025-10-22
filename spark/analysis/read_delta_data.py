"""
Read Delta Lake data from MinIO storage
"""

from pyspark.sql import SparkSession


def create_spark_session(app_name="ReadDeltaData"):
    """
    Create and configure Spark session with Delta Lake and MinIO support.

    Returns:
        SparkSession configured for Delta Lake and S3-compatible storage
    """
    spark = SparkSession.builder \
        .appName(app_name) \
        .config("spark.jars.packages",
                "org.apache.hadoop:hadoop-aws:3.3.4,"
                "io.delta:delta-spark_2.12:3.2.0") \
        .config("spark.jars.repositories", "https://repo1.maven.org/maven2/") \
        .config("spark.jars.ivy", "/tmp/.ivy2") \
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
    return spark


def read_delta_table(spark, path):
    """
    Read Delta table from specified path.

    Args:
        spark: SparkSession instance
        path: S3 path to Delta table (e.g., "s3a://lakehouse/clickstream-events-delta/")

    Returns:
        DataFrame containing the Delta table data
    """
    df = spark.read.format("delta").load(path)
    return df


def main():
    # Delta table path in MinIO
    delta_path = "s3a://lakehouse/clickstream-events-delta/"

    print(f"Reading Delta table from: {delta_path}")

    # Create Spark session
    spark = create_spark_session()

    try:
        # Read Delta table
        df = read_delta_table(spark, delta_path)

        # Show basic information
        print(f"\nTotal records: {df.count()}")
        print("\nSchema:")
        df.printSchema()

        print("\nSample data (first 20 rows):")
        df.show(20, truncate=False)

        # Optional: Show some basic statistics
        print("\nEvent type distribution:")
        df.groupBy("event_type").count().orderBy("count", ascending=False).show()

    except Exception as e:
        print(f"Error reading Delta table: {e}")
    finally:
        spark.stop()


if __name__ == "__main__":
    main()
