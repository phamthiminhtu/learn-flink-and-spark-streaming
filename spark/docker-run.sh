#!/bin/bash

# Spark Docker Job Submission Script
# Usage: ./docker-run.sh <command> [args]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Spark configuration
SPARK_MASTER="spark://spark-master:7077"
SPARK_APP_NAME="Clickstream Processor"

print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_info() {
    echo -e "${CYAN}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

check_spark_cluster() {
    print_info "Checking Spark cluster status..."

    if ! docker ps | grep -q "spark-master"; then
        print_error "Spark master is not running!"
        echo "Start it with: docker compose up -d spark-master spark-worker-1 spark-worker-2"
        exit 1
    fi

    print_success "Spark cluster is running"
}

submit_job() {
    local script_name=$1
    shift
    local args="$@"

    print_header "Submitting Spark Job"
    print_info "Script: ${script_name}"
    print_info "Master: ${SPARK_MASTER}"

    # Check if script exists
    if [ ! -f "${SCRIPT_DIR}/src/${script_name}" ]; then
        print_error "Script not found: ${SCRIPT_DIR}/src/${script_name}"
        exit 1
    fi

    print_success "Script found"

    # Submit to Spark cluster
    # Using Hadoop 3.3.4 (same as Spark 3.5 uses internally) to avoid ClassCastException
    docker exec spark-master /opt/spark/bin/spark-submit \
        --master "${SPARK_MASTER}" \
        --name "${SPARK_APP_NAME}" \
        --conf "spark.hadoop.fs.s3a.endpoint=http://minio:9000" \
        --conf "spark.hadoop.fs.s3a.access.key=admin" \
        --conf "spark.hadoop.fs.s3a.secret.key=password123" \
        --conf "spark.hadoop.fs.s3a.path.style.access=true" \
        --conf "spark.hadoop.fs.s3a.impl=org.apache.hadoop.fs.s3a.S3AFileSystem" \
        --conf "spark.hadoop.fs.s3a.aws.credentials.provider=org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider" \
        --conf "spark.sql.extensions=io.delta.sql.DeltaSparkSessionExtension" \
        --conf "spark.sql.catalog.spark_catalog=org.apache.spark.sql.delta.catalog.DeltaCatalog" \
        --packages "org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,io.delta:delta-spark_2.12:3.0.0,org.apache.hadoop:hadoop-aws:3.3.4,com.amazonaws:aws-java-sdk-bundle:1.12.262" \
        "/opt/spark-apps/src/${script_name}" \
        ${args}

    if [ $? -eq 0 ]; then
        print_success "Job completed successfully!"
    else
        print_error "Job failed!"
        exit 1
    fi
}

show_ui() {
    print_header "Spark Web UIs"
    echo -e "${CYAN}Master UI:${NC}  http://localhost:8080"
    echo -e "${CYAN}Job Status:${NC} Check running applications"
    echo ""
    echo "Opening Master UI..."
    if command -v open &> /dev/null; then
        open "http://localhost:8080"
    elif command -v xdg-open &> /dev/null; then
        xdg-open "http://localhost:8080"
    fi
}

show_logs() {
    print_header "Spark Master Logs"
    docker logs spark-master --tail 100 -f
}

show_help() {
    cat << EOF
Spark Docker Job Submission Script

Usage:
    ./docker-run.sh <command> [args]

Commands:
    submit <script.py> [args]    Submit a Spark job
    ui                           Open Spark Master Web UI
    logs                         Show Spark master logs
    shell                        Open PySpark shell
    status                       Check cluster status
    help                         Show this help message

Examples:
    ./docker-run.sh submit spark_clickstream_events.py
    ./docker-run.sh ui
    ./docker-run.sh logs
    ./docker-run.sh shell

EOF
}

cluster_status() {
    print_header "Spark Cluster Status"

    echo "Master:"
    docker ps --filter "name=spark-master" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

    echo ""
    echo "Workers:"
    docker ps --filter "name=spark-worker" --format "table {{.Names}}\t{{.Status}}"
}

spark_shell() {
    print_header "Starting PySpark Shell"
    print_info "Connecting to ${SPARK_MASTER}"

    docker exec -it spark-master /opt/spark/bin/pyspark \
        --master "${SPARK_MASTER}" \
        --conf "spark.hadoop.fs.s3a.endpoint=http://minio:9000" \
        --conf "spark.hadoop.fs.s3a.access.key=admin" \
        --conf "spark.hadoop.fs.s3a.secret.key=password123" \
        --conf "spark.hadoop.fs.s3a.path.style.access=true" \
        --packages "org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,io.delta:delta-core_2.12:2.4.0,org.apache.hadoop:hadoop-aws:3.3.4"
}

# Main script logic
case "${1:-help}" in
    submit)
        check_spark_cluster
        shift
        submit_job "$@"
        ;;
    ui)
        show_ui
        ;;
    logs)
        show_logs
        ;;
    shell)
        check_spark_cluster
        spark_shell
        ;;
    status)
        cluster_status
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        print_error "Unknown command: $1"
        echo ""
        show_help
        exit 1
        ;;
esac
