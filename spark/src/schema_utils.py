"""
Utility functions for converting Avro schemas to Spark schemas.
This allows maintaining a single source of truth in Avro format.
"""

import json
from pyspark.sql.types import (
    StructType, StructField, StringType, IntegerType, LongType,
    FloatType, DoubleType, BooleanType, TimestampType, ArrayType, MapType
)


def avro_type_to_spark_type(avro_type):
    """
    Convert Avro type to Spark DataType.

    Args:
        avro_type: Avro type definition (string or dict or list for union types)

    Returns:
        Spark DataType object
    """
    # Handle union types (e.g., ["null", "string"] for nullable fields)
    if isinstance(avro_type, list):
        # Filter out "null" and get the actual type
        non_null_types = [t for t in avro_type if t != "null"]
        if len(non_null_types) == 1:
            return avro_type_to_spark_type(non_null_types[0])
        else:
            # Complex union types - default to string
            return StringType()

    # Handle primitive types (string definitions)
    if isinstance(avro_type, str):
        type_mapping = {
            "null": StringType(),      # Default for null
            "boolean": BooleanType(),
            "int": IntegerType(),
            "long": LongType(),
            "float": FloatType(),
            "double": DoubleType(),
            "bytes": StringType(),     # Could use BinaryType()
            "string": StringType(),
        }
        return type_mapping.get(avro_type, StringType())

    # Handle complex types (dict definitions)
    if isinstance(avro_type, dict):
        avro_type_name = avro_type.get("type")

        # Check for logical types
        logical_type = avro_type.get("logicalType")
        if logical_type == "timestamp-millis" or logical_type == "timestamp-micros":
            # Keep as LongType for Kafka timestamp compatibility
            # Can convert to TimestampType() if preferred
            return LongType()

        if avro_type_name == "array":
            element_type = avro_type_to_spark_type(avro_type.get("items"))
            return ArrayType(element_type)

        if avro_type_name == "map":
            value_type = avro_type_to_spark_type(avro_type.get("values"))
            return MapType(StringType(), value_type)

        if avro_type_name == "record":
            # Nested record - recursively convert
            fields = avro_type.get("fields", [])
            spark_fields = [avro_field_to_spark_field(f) for f in fields]
            return StructType(spark_fields)

    # Default fallback
    return StringType()


def avro_field_to_spark_field(avro_field):
    """
    Convert an Avro field definition to a Spark StructField.

    Args:
        avro_field: Dictionary with "name", "type", and optional "default"

    Returns:
        Spark StructField object
    """
    field_name = avro_field["name"]
    avro_type = avro_field["type"]

    # Determine if field is nullable
    # If type is a union with "null", it's nullable
    nullable = False
    if isinstance(avro_type, list) and "null" in avro_type:
        nullable = True

    spark_type = avro_type_to_spark_type(avro_type)

    return StructField(field_name, spark_type, nullable)


def load_avro_schema_as_spark(avro_schema_path):
    """
    Load an Avro schema file and convert it to a Spark StructType.

    Args:
        avro_schema_path: Path to the .avsc file

    Returns:
        Spark StructType schema

    Example:
        schema = load_avro_schema_as_spark("data/schemas/clickstream-event-readable.avsc")
    """
    with open(avro_schema_path, 'r') as f:
        avro_schema = json.load(f)

    if avro_schema.get("type") != "record":
        raise ValueError(f"Expected Avro record type, got {avro_schema.get('type')}")

    fields = avro_schema.get("fields", [])
    spark_fields = [avro_field_to_spark_field(field) for field in fields]

    return StructType(spark_fields)


def print_schema_comparison(avro_path, spark_schema=None):
    """
    Print a comparison of Avro schema and generated Spark schema.
    Useful for debugging and validation.

    Args:
        avro_path: Path to Avro schema file
        spark_schema: Optional existing Spark schema to compare
    """
    generated_schema = load_avro_schema_as_spark(avro_path)

    print("=" * 80)
    print("GENERATED SPARK SCHEMA FROM AVRO")
    print("=" * 80)
    for field in generated_schema.fields:
        nullable = "nullable" if field.nullable else "not null"
        print(f"  |-- {field.name}: {field.dataType.simpleString()} ({nullable})")

    if spark_schema:
        print("\n" + "=" * 80)
        print("EXISTING SPARK SCHEMA")
        print("=" * 80)
        for field in spark_schema.fields:
            nullable = "nullable" if field.nullable else "not null"
            print(f"  |-- {field.name}: {field.dataType.simpleString()} ({nullable})")
