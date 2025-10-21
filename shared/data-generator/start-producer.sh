#!/bin/bash

################################################################################
# Clickstream Data Producer - Onboarding Script
# 
# This script helps new users start producing clickstream events to Kafka.
# It performs prerequisite checks, starts required services, and launches
# the data producer.
################################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

################################################################################
# Helper Functions
################################################################################

print_header() {
    echo ""
    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}$1${NC}"
    echo -e "${CYAN}========================================${NC}"
    echo ""
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC}  $1"
}

print_info() {
    echo -e "${BLUE}ℹ${NC}  $1"
}

print_step() {
    echo -e "${CYAN}➜${NC} $1"
}

################################################################################
# Prerequisite Checks
################################################################################

check_prerequisites() {
    print_header "Step 1: Checking Prerequisites"
    
    local all_good=true
    
    # Check Docker
    if command -v docker &> /dev/null; then
        print_success "Docker is installed ($(docker --version | cut -d' ' -f3 | sed 's/,//'))"
    else
        print_error "Docker is not installed"
        print_info "Please install Docker from: https://docs.docker.com/get-docker/"
        all_good=false
    fi
    
    # Check Docker Compose
    if command -v docker compose &> /dev/null; then
        print_success "Docker Compose is installed"
    elif command -v docker-compose &> /dev/null; then
        print_success "Docker Compose is installed (standalone)"
    else
        print_error "Docker Compose is not installed"
        print_info "Please install Docker Compose from: https://docs.docker.com/compose/install/"
        all_good=false
    fi
    
    # Check if Docker daemon is running
    if docker ps &> /dev/null; then
        print_success "Docker daemon is running"
    else
        print_error "Docker daemon is not running"
        print_info "Please start Docker Desktop or Docker daemon"
        all_good=false
    fi
    
    if [ "$all_good" = false ]; then
        echo ""
        print_error "Prerequisites not met. Please fix the issues above and try again."
        exit 1
    fi
    
    echo ""
    print_success "All prerequisites met!"
}

################################################################################
# Service Management
################################################################################

check_services() {
    print_header "Step 2: Checking Required Services"
    
    cd "$PROJECT_ROOT"
    
    # Check if Kafka is running
    if docker ps --format '{{.Names}}' | grep -q '^kafka$'; then
        KAFKA_STATUS=$(docker inspect -f '{{.State.Health.Status}}' kafka 2>/dev/null || echo "unknown")
        if [ "$KAFKA_STATUS" = "healthy" ]; then
            print_success "Kafka is running and healthy"
            KAFKA_RUNNING=true
        else
            print_warning "Kafka is running but not healthy yet (status: $KAFKA_STATUS)"
            KAFKA_RUNNING=false
        fi
    else
        print_warning "Kafka is not running"
        KAFKA_RUNNING=false
    fi
    
    # Check if Schema Registry is running
    if docker ps --format '{{.Names}}' | grep -q '^schema-registry$'; then
        print_success "Schema Registry is running"
        SCHEMA_REGISTRY_RUNNING=true
    else
        print_warning "Schema Registry is not running"
        SCHEMA_REGISTRY_RUNNING=false
    fi
    
    # Check if Producer container exists
    if docker ps -a --format '{{.Names}}' | grep -q '^clickstream-producer$'; then
        if docker ps --format '{{.Names}}' | grep -q '^clickstream-producer$'; then
            print_success "Producer container is running"
            PRODUCER_RUNNING=true
        else
            print_warning "Producer container exists but is not running"
            PRODUCER_RUNNING=false
        fi
    else
        print_warning "Producer container does not exist"
        PRODUCER_RUNNING=false
    fi
}

start_services() {
    if [ "$KAFKA_RUNNING" = false ] || [ "$SCHEMA_REGISTRY_RUNNING" = false ] || [ "$PRODUCER_RUNNING" = false ]; then
        echo ""
        print_step "Starting required services..."
        echo ""
        
        cd "$PROJECT_ROOT"
        docker compose up -d kafka schema-registry producer
        
        echo ""
        print_step "Waiting for Kafka to be ready (this may take 30-60 seconds)..."
        
        # Wait for Kafka to be healthy
        local max_wait=60
        local waited=0
        while [ $waited -lt $max_wait ]; do
            if docker inspect -f '{{.State.Health.Status}}' kafka 2>/dev/null | grep -q "healthy"; then
                break
            fi
            echo -n "."
            sleep 2
            waited=$((waited + 2))
        done
        echo ""
        
        if [ $waited -ge $max_wait ]; then
            print_error "Kafka did not become healthy in time"
            print_info "Check logs with: docker compose logs kafka"
            exit 1
        fi
        
        print_success "Services started successfully!"
        
        # Give producer container time to install dependencies
        print_step "Waiting for producer dependencies to install..."
        sleep 5
    else
        print_success "All services are already running"
    fi
}

################################################################################
# Topic Verification
################################################################################

verify_topics() {
    print_header "Step 3: Verifying Kafka Topics"
    
    cd "$PROJECT_ROOT"
    
    # Run kafka-setup to ensure topics exist
    print_step "Ensuring topics are created..."
    docker compose up kafka-setup > /dev/null 2>&1 || true
    
    # List topics
    TOPICS=$(docker exec kafka kafka-topics --list --bootstrap-server localhost:9092 2>/dev/null | grep -E "^clickstream")
    
    if echo "$TOPICS" | grep -q "^clickstream$"; then
        print_success "Topic 'clickstream' exists"
    else
        print_error "Topic 'clickstream' not found"
        exit 1
    fi
    
    if echo "$TOPICS" | grep -q "^clickstream-dlq$"; then
        print_success "Topic 'clickstream-dlq' exists (dead letter queue)"
    fi
    
    # Show topic details
    echo ""
    print_info "Topic details:"
    docker exec kafka kafka-topics --describe --topic clickstream --bootstrap-server localhost:9092 2>/dev/null | grep -E "(Topic:|PartitionCount:|ReplicationFactor:)" | sed 's/^/  /'
}

################################################################################
# Producer Management
################################################################################

check_producer_status() {
    print_header "Step 4: Producer Status"
    
    # Check if producer script is already running
    if docker exec clickstream-producer pgrep -f "produce-test-events.py" > /dev/null 2>&1; then
        print_warning "Producer script is already running!"
        echo ""
        print_info "To view logs: docker compose logs -f producer"
        print_info "To stop it: docker exec clickstream-producer pkill -f produce-test-events.py"
        echo ""
        read -p "Do you want to stop the existing producer and start a new one? (y/N): " -n 1 -r
        echo ""
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            docker exec clickstream-producer pkill -f produce-test-events.py 2>/dev/null || true
            sleep 2
            print_success "Stopped existing producer"
        else
            print_info "Exiting. Use the commands above to manage the existing producer."
            exit 0
        fi
    else
        print_info "No producer is currently running"
    fi
}

start_producer() {
    print_header "Step 5: Starting Producer"
    
    echo ""
    print_info "The producer will generate realistic e-commerce clickstream events:"
    echo "  • Event types: VIEW, ADD_TO_CART, REMOVE_FROM_CART, CHECKOUT, PURCHASE"
    echo "  • Rate: 10 events/second (configurable)"
    echo "  • Users: 100 simulated users"
    echo "  • Products: 50 products in catalog"
    echo "  • Late data: 5% of events arrive 10-120 seconds late"
    echo ""
    
    print_step "Choose how to run the producer:"
    echo ""
    echo "  1) Interactive mode (see live output, press Ctrl+C to stop)"
    echo "  2) Background mode (runs in background, check logs separately)"
    echo "  3) Custom configuration (edit settings first)"
    echo "  4) Exit"
    echo ""
    
    read -p "Enter your choice (1-4): " -n 1 -r
    echo ""
    echo ""
    
    case $REPLY in
        1)
            print_step "Starting producer in interactive mode..."
            echo ""
            print_info "Press Ctrl+C to stop the producer"
            print_info "Statistics will be displayed every 100 events"
            echo ""
            sleep 2
            docker exec -it clickstream-producer python3 produce-test-events.py
            ;;
        2)
            print_step "Starting producer in background mode..."
            docker exec -d clickstream-producer python3 produce-test-events.py
            sleep 2
            print_success "Producer started in background!"
            echo ""
            print_info "Monitor with: docker compose logs -f producer"
            print_info "Stop with: docker exec clickstream-producer pkill -f produce-test-events.py"
            ;;
        3)
            print_step "Opening configuration file..."
            echo ""
            print_info "Edit the following parameters in: kafka/produce-test-events.py"
            echo "  • EVENTS_PER_SECOND = 10"
            echo "  • NUM_USERS = 100"
            echo "  • NUM_PRODUCTS = 50"
            echo "  • LATE_DATA_PERCENTAGE = 0.05"
            echo ""
            print_info "After editing, run this script again or execute:"
            echo "  docker exec -it clickstream-producer python3 produce-test-events.py"
            ;;
        4)
            print_info "Exiting..."
            exit 0
            ;;
        *)
            print_error "Invalid choice"
            exit 1
            ;;
    esac
}

################################################################################
# Monitoring & Utilities
################################################################################

show_monitoring_info() {
    print_header "Monitoring & Useful Commands"
    
    echo -e "${CYAN}View Producer Logs:${NC}"
    echo "  docker compose logs -f producer"
    echo ""
    
    echo -e "${CYAN}Check Kafka Topics:${NC}"
    echo "  docker exec kafka kafka-topics --list --bootstrap-server localhost:9092"
    echo ""
    
    echo -e "${CYAN}Consume Messages (view data):${NC}"
    echo "  docker exec kafka kafka-console-consumer \\"
    echo "    --bootstrap-server localhost:9092 \\"
    echo "    --topic clickstream \\"
    echo "    --from-beginning \\"
    echo "    --max-messages 10"
    echo ""
    
    echo -e "${CYAN}Kafka UI (Web Interface):${NC}"
    echo "  http://localhost:8080"
    echo ""
    
    echo -e "${CYAN}Stop Producer:${NC}"
    echo "  docker exec clickstream-producer pkill -f produce-test-events.py"
    echo ""
    
    echo -e "${CYAN}Stop All Services:${NC}"
    echo "  docker compose down"
    echo ""
}

################################################################################
# Main Execution
################################################################################

main() {
    clear
    echo ""
    echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                                                            ║${NC}"
    echo -e "${GREEN}║         Clickstream Data Producer - Onboarding             ║${NC}"
    echo -e "${GREEN}║                                                            ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    # Run all steps
    check_prerequisites
    check_services
    start_services
    verify_topics
    check_producer_status
    start_producer
    
    # Show monitoring info if producer was started
    if [[ $REPLY =~ ^[12]$ ]]; then
        echo ""
        show_monitoring_info
    fi
    
    echo ""
    print_success "Setup complete! Happy streaming! 🚀"
    echo ""
}

# Run main function
main

