#!/bin/bash

# Build Flink JAR using Docker (no local Maven required)
# This script uses a Maven Docker container to build the project

set -e

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Building Flink JAR with Docker${NC}"
echo -e "${BLUE}(No local Maven required!)${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check Docker is running
if ! docker ps >/dev/null 2>&1; then
    echo -e "${RED}❌ Docker is not running${NC}"
    exit 1
fi

echo -e "${CYAN}ℹ️  Using Maven Docker image to build...${NC}"
echo ""

# Run Maven in Docker container
# - Mounts current directory to /app
# - Mounts .m2 cache to speed up subsequent builds
# - Runs mvn clean package
docker run --rm \
    -v "$(pwd)":/app \
    -v "$HOME/.m2":/root/.m2 \
    -w /app \
    maven:3.9-eclipse-temurin-11 \
    mvn clean package -DskipTests

echo ""
echo -e "${GREEN}✅ Build complete!${NC}"
echo -e "JAR file: ${CYAN}target/flink-clickstream-1.0-SNAPSHOT.jar${NC}"
