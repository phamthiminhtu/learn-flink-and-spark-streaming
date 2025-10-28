#!/bin/bash

# Flink Docker Deployment Script
# Builds JAR and submits to Flink cluster running in Docker Compose

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

JAR_FILE="target/flink-clickstream-1.0-SNAPSHOT.jar"
MAIN_CLASS=""
CONFIG_DIR="jobs/active"
SELECTED_CONFIG=""

function print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

function print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

function print_error() {
    echo -e "${RED}❌ $1${NC}"
}

function print_info() {
    echo -e "${CYAN}ℹ️  $1${NC}"
}

function print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

function parse_yaml() {
    local yaml_file=$1
    local query=$2

    # Use yq via Docker to parse YAML
    docker run --rm -v "$(pwd):/workdir" mikefarah/yq:4 \
        eval "$query" "/workdir/$yaml_file" 2>/dev/null || echo ""
}

function list_active_configs() {
    print_header "Available Job Configurations"

    if [ ! -d "$CONFIG_DIR" ]; then
        print_error "Config directory not found: $CONFIG_DIR"
        exit 1
    fi

    local configs=($(ls "$CONFIG_DIR"/*.yml 2>/dev/null))

    if [ ${#configs[@]} -eq 0 ]; then
        print_error "No job configurations found in $CONFIG_DIR"
        echo "Please create a .yml file in $CONFIG_DIR with job configuration"
        exit 1
    fi

    echo ""
    local i=1
    for config in "${configs[@]}"; do
        local job_name=$(parse_yaml "$config" ".job_name")
        local main_class=$(parse_yaml "$config" ".main_class")
        echo -e "${GREEN}[$i]${NC} $(basename $config)"
        echo -e "    Job: ${CYAN}$job_name${NC}"
        echo -e "    Class: ${CYAN}$main_class${NC}"
        echo ""
        i=$((i+1))
    done
}

function select_config() {
    local configs=($(ls "$CONFIG_DIR"/*.yml 2>/dev/null))

    if [ ${#configs[@]} -eq 0 ]; then
        print_error "No job configurations found in $CONFIG_DIR"
        exit 1
    fi

    # If only one config, use it automatically
    if [ ${#configs[@]} -eq 1 ]; then
        SELECTED_CONFIG="${configs[0]}"
        local job_name=$(parse_yaml "$SELECTED_CONFIG" ".job_name")
        print_info "Auto-selecting: $job_name ($(basename $SELECTED_CONFIG))"
        return
    fi

    # Multiple configs - show list and prompt
    list_active_configs

    echo -e "${YELLOW}Select a job configuration [1-${#configs[@]}]:${NC} "
    read -r selection

    if ! [[ "$selection" =~ ^[0-9]+$ ]] || [ "$selection" -lt 1 ] || [ "$selection" -gt ${#configs[@]} ]; then
        print_error "Invalid selection"
        exit 1
    fi

    SELECTED_CONFIG="${configs[$((selection-1))]}"
    local job_name=$(parse_yaml "$SELECTED_CONFIG" ".job_name")
    print_success "Selected: $job_name ($(basename $SELECTED_CONFIG))"
}

function load_config() {
    if [ -z "$SELECTED_CONFIG" ]; then
        select_config
    fi

    print_header "Loading Configuration"

    # Parse configuration values
    MAIN_CLASS=$(parse_yaml "$SELECTED_CONFIG" ".main_class")
    local kafka_servers=$(parse_yaml "$SELECTED_CONFIG" ".kafka.bootstrap_servers")
    local kafka_topic=$(parse_yaml "$SELECTED_CONFIG" ".kafka.topic")
    local output_path=$(parse_yaml "$SELECTED_CONFIG" ".storage.output_path")
    local checkpoint_path=$(parse_yaml "$SELECTED_CONFIG" ".storage.checkpoint_path")
    local parallelism=$(parse_yaml "$SELECTED_CONFIG" ".job_params.parallelism")

    # Display loaded configuration
    echo -e "${CYAN}Configuration loaded from: ${NC}$(basename $SELECTED_CONFIG)"
    echo -e "${CYAN}Main Class:${NC} $MAIN_CLASS"
    [ -n "$kafka_servers" ] && echo -e "${CYAN}Kafka Servers:${NC} $kafka_servers"
    [ -n "$kafka_topic" ] && echo -e "${CYAN}Kafka Topic:${NC} $kafka_topic"
    [ -n "$output_path" ] && echo -e "${CYAN}Output Path:${NC} $output_path"
    [ -n "$checkpoint_path" ] && echo -e "${CYAN}Checkpoint Path:${NC} $checkpoint_path"
    [ -n "$parallelism" ] && echo -e "${CYAN}Parallelism:${NC} $parallelism"
    echo ""

    # Export environment variables for the Flink job to use
    [ -n "$kafka_servers" ] && export KAFKA_BOOTSTRAP_SERVERS="$kafka_servers"
    [ -n "$kafka_topic" ] && export KAFKA_TOPIC="$kafka_topic"
    [ -n "$output_path" ] && export OUTPUT_PATH="$output_path"
    [ -n "$checkpoint_path" ] && export CHECKPOINT_PATH="$checkpoint_path"

    if [ -z "$MAIN_CLASS" ]; then
        print_error "main_class not found in configuration file"
        exit 1
    fi
}

function check_docker() {
    if ! docker ps >/dev/null 2>&1; then
        print_error "Docker is not running"
        echo "Please start Docker and try again"
        exit 1
    fi
}

function check_flink_cluster() {
    if ! docker ps | grep -q flink-jobmanager; then
        print_error "Flink cluster is not running"
        echo ""
        echo "Start the cluster with:"
        echo "  ${GREEN}docker-compose up -d flink-jobmanager flink-taskmanager${NC}"
        exit 1
    fi

    # Check if JobManager is healthy
    if ! docker exec flink-jobmanager curl -sf http://localhost:8081/overview >/dev/null 2>&1; then
        print_warning "Flink JobManager is not ready yet"
        print_info "Waiting for JobManager to become healthy..."
        sleep 5
    fi
}

function build_jar() {
    print_header "Building Flink Job"

    if [ ! -f "pom.xml" ]; then
        print_error "pom.xml not found. Are you in the flink/ directory?"
        exit 1
    fi

    # Check if Maven is available locally
    if command -v mvn &> /dev/null; then
        print_info "Using local Maven"
        mvn clean package -DskipTests
    else
        print_warning "Maven not found locally - using Docker to build"
        print_info "This may take a few minutes on first run..."
        echo ""

        # Use Maven Docker container to build
        docker run --rm \
            -v "$(pwd)":/app \
            -v "$HOME/.m2":/root/.m2 \
            -w /app \
            maven:3.9-eclipse-temurin-11 \
            mvn clean package -DskipTests
    fi

    if [ ! -f "$JAR_FILE" ]; then
        print_error "Build failed - JAR file not created"
        exit 1
    fi

    print_success "Build completed: $JAR_FILE"

    # Restart Flink to pick up new JAR
    print_info "Restarting Flink services to pick up new JAR..."
    docker compose restart flink-jobmanager flink-taskmanager > /dev/null 2>&1
    sleep 3  # Give containers time to start
}

function submit_job() {
    # Load configuration from YAML file
    load_config

    print_header "Submitting Job to Flink Cluster"

    print_info "JobManager UI: ${CYAN}http://localhost:8082${NC}"
    print_info "Submitting: $MAIN_CLASS"
    echo ""

    # Check JAR exists locally first
    if [ ! -f "$JAR_FILE" ]; then
        print_error "JAR file not found locally: $JAR_FILE"
        echo "Did the build complete successfully?"
        exit 1
    fi

    # The JAR path in the container (mounted from ./flink/target to /opt/flink/usrlib)
    CONTAINER_JAR_PATH="/opt/flink/usrlib/$(basename $JAR_FILE)"

    # Verify JAR is accessible in container
    print_info "Checking JAR in container: $CONTAINER_JAR_PATH"
    if ! docker exec flink-jobmanager test -f "$CONTAINER_JAR_PATH"; then
        print_warning "JAR not found in container, restarting Flink services..."
        docker compose restart flink-jobmanager flink-taskmanager > /dev/null 2>&1
        sleep 5

        if ! docker exec flink-jobmanager test -f "$CONTAINER_JAR_PATH"; then
            print_error "JAR file still not found in container: $CONTAINER_JAR_PATH"
            echo ""
            echo "Available files in /opt/flink/usrlib:"
            docker exec flink-jobmanager ls -lh /opt/flink/usrlib/ || true
            exit 1
        fi
    fi

    print_success "JAR found in container"
    echo ""

    # Submit job
    docker exec flink-jobmanager flink run \
        --class "$MAIN_CLASS" \
        --detached \
        "$CONTAINER_JAR_PATH"

    print_success "Job submitted successfully!"
    echo ""
    print_info "View job status:"
    echo "  - Web UI: ${CYAN}http://localhost:8082${NC}"
    echo "  - List jobs: ${GREEN}docker exec flink-jobmanager flink list${NC}"
    echo "  - Cancel job: ${GREEN}docker exec flink-jobmanager flink cancel <job-id>${NC}"
}

function list_jobs() {
    print_header "Listing Flink Jobs"
    docker exec flink-jobmanager flink list
}

function cancel_job() {
    local job_id=$1

    if [ -z "$job_id" ]; then
        print_error "Job ID required"
        echo "Usage: $0 cancel <job-id>"
        echo ""
        list_jobs
        exit 1
    fi

    print_header "Cancelling Job: $job_id"
    docker exec flink-jobmanager flink cancel "$job_id"
    print_success "Job cancelled"
}

function show_logs() {
    print_header "Flink Job Logs"
    print_info "Press Ctrl+C to stop"
    echo ""
    docker logs -f flink-jobmanager
}

function show_help() {
    echo "Flink Docker Deployment Script"
    echo ""
    echo "Usage: ./docker-run.sh [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  build          Build the JAR file"
    echo "  submit         Build and submit job to Flink cluster"
    echo "  config         List available job configurations"
    echo "  list           List running jobs"
    echo "  cancel <id>    Cancel a specific job"
    echo "  logs           Show Flink JobManager logs"
    echo "  status         Show cluster status"
    echo "  help           Show this help message"
    echo ""
    echo "Configuration:"
    echo "  - Active jobs:   ${CYAN}jobs/active/*.yml${NC}"
    echo "  - Inactive jobs: ${CYAN}jobs/inactive/*.yml${NC}"
    echo "  - Move YAML files between folders to enable/disable jobs"
    echo ""
    echo "Full Workflow:"
    echo "  1. Start services:   ${GREEN}docker-compose up -d${NC}"
    echo "  2. Configure job:    ${GREEN}Edit jobs/active/*.yml${NC}"
    echo "  3. Submit job:       ${GREEN}./docker-run.sh submit${NC}"
    echo "  4. View UI:          ${CYAN}http://localhost:8082${NC}"
    echo "  5. Check output:     ${GREEN}docker exec -it minio mc ls myminio/lakehouse/${NC}"
}

function show_status() {
    print_header "Flink Cluster Status"

    echo -e "${CYAN}Docker Containers:${NC}"
    docker ps --filter "name=flink" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

    echo ""
    echo -e "${CYAN}Flink JobManager UI:${NC}"
    if docker exec flink-jobmanager curl -sf http://localhost:8081/overview >/dev/null 2>&1; then
        echo -e "${GREEN}✅ Available at: http://localhost:8082${NC}"
    else
        echo -e "${RED}❌ Not available${NC}"
    fi

    echo ""
    echo -e "${CYAN}Running Jobs:${NC}"
    docker exec flink-jobmanager flink list 2>/dev/null || echo "No jobs running"
}

# Main script logic
check_docker

case "${1:-submit}" in
    build)
        build_jar
        ;;
    submit)
        check_flink_cluster
        build_jar
        submit_job
        ;;
    config)
        list_active_configs
        ;;
    list)
        check_flink_cluster
        list_jobs
        ;;
    cancel)
        check_flink_cluster
        cancel_job "$2"
        ;;
    logs)
        check_flink_cluster
        show_logs
        ;;
    status)
        show_status
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
