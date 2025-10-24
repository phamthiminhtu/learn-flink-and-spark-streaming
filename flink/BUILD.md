# Building the Flink JAR

You have **3 options** to build the Flink job JAR file:

## Option 1: Automatic (Recommended) ⭐

The `docker-run.sh` script **automatically detects** if you have Maven installed:

```bash
cd flink/
./docker-run.sh submit
```

**What happens:**
- ✅ **Has Maven?** Uses local Maven (fast)
- ❌ **No Maven?** Uses Docker Maven container (automatic!)

**You don't need to install anything!**

---

## Option 2: Docker Maven (Manual)

Build using Docker explicitly (no local Maven needed):

```bash
cd flink/

# Build with Docker
./docker-build.sh

# Output: target/flink-clickstream-1.0-SNAPSHOT.jar
```

**How it works:**
```bash
docker run --rm \
    -v "$(pwd)":/app \
    -v "$HOME/.m2":/root/.m2 \
    -w /app \
    maven:3.9-eclipse-temurin-11 \
    mvn clean package -DskipTests
```

**Advantages:**
- ✅ No local Java/Maven installation needed
- ✅ Consistent build environment (same as CI/CD)
- ✅ Dependencies cached in `~/.m2` (fast subsequent builds)

**First Build:**
- Downloads Maven Docker image (~300MB)
- Downloads project dependencies (~200MB)
- Takes 2-3 minutes

**Subsequent Builds:**
- Uses cached image and dependencies
- Takes ~30 seconds

---

## Option 3: Local Maven (Fastest)

If you have Maven installed locally:

```bash
cd flink/

# Build
mvn clean package

# Skip tests (faster)
mvn clean package -DskipTests
```

**Install Maven:**

```bash
# macOS
brew install maven

# Linux (Ubuntu/Debian)
sudo apt install maven

# Linux (Fedora/RHEL)
sudo dnf install maven

# Verify
mvn -version
```

**Advantages:**
- ✅ Fastest builds (~15-20 seconds)
- ✅ Better IDE integration
- ✅ Easier debugging

**Disadvantages:**
- ❌ Requires local Java 11+ and Maven
- ❌ Environment-specific issues possible

---

## Comparison

| Method | First Build | Subsequent | Setup | Best For |
|--------|-------------|------------|-------|----------|
| **Auto (Option 1)** | 2-3 min | 30 sec | None! | ⭐ Everyone |
| **Docker (Option 2)** | 2-3 min | 30 sec | None! | CI/CD, Consistency |
| **Local (Option 3)** | 30 sec | 15 sec | Install Maven | Power users |

---

## What Gets Built?

All methods produce the same output:

```
flink/target/flink-clickstream-1.0-SNAPSHOT.jar
```

This is a **fat JAR** (uber-jar) containing:
- Your application code
- All dependencies (Flink, Kafka, Jackson, etc.)
- Ready to run anywhere with just Java

**Size:** ~100 MB

---

## Troubleshooting

### "Docker: Cannot connect to the Docker daemon"

**Problem:** Docker is not running

**Solution:**
```bash
# Start Docker Desktop
# Or on Linux:
sudo systemctl start docker
```

### "mvn: command not found"

**Problem:** Maven not installed (Option 3)

**Solution:**
- Use Option 1 or 2 (no Maven needed!)
- Or install Maven: `brew install maven`

### "Permission denied: ./docker-run.sh"

**Problem:** Script not executable

**Solution:**
```bash
chmod +x flink/docker-run.sh
chmod +x flink/docker-build.sh
```

### Build takes forever

**First build is slow!** Maven downloads:
- Docker image: ~300MB
- Java dependencies: ~200MB

**Subsequent builds are MUCH faster** (cached).

**Speed it up:**
```bash
# Pre-download Maven image
docker pull maven:3.9-eclipse-temurin-11

# Pre-download dependencies
./docker-build.sh
```

### "Failed to read artifact descriptor"

**Problem:** Network issues or Maven repository down

**Solution:**
```bash
# Retry the build
./docker-run.sh submit

# Or clear Maven cache and retry
rm -rf ~/.m2/repository
./docker-build.sh
```

---

## How Docker Build Works

### Step 1: Mount Directories
```bash
-v "$(pwd)":/app              # Project code → /app in container
-v "$HOME/.m2":/root/.m2      # Maven cache → container cache
```

### Step 2: Run Maven in Container
```bash
maven:3.9-eclipse-temurin-11  # Base image (Java 11 + Maven 3.9)
mvn clean package             # Build command
```

### Step 3: Output to Host
```bash
# JAR written to /app/target/... in container
# Which is mounted from $(pwd)/target/... on host
# Result: JAR appears in your local target/ directory!
```

### Why Cache `~/.m2`?

Maven downloads dependencies to `~/.m2/repository/`:
```
~/.m2/repository/
├── org/apache/flink/
│   ├── flink-streaming-java/1.18.0/...
│   ├── flink-connector-kafka/3.0.1-1.18/...
├── com/fasterxml/jackson/...
└── org/apache/kafka/...
```

**Without caching:**
- Every build re-downloads ~200MB
- Takes 2-3 minutes EVERY time

**With caching:**
- First build: Download once
- Subsequent builds: Reuse cached JARs
- Takes ~30 seconds

---

## Advanced: Maven in Docker Compose

You can also add a build service to `docker-compose.yml`:

```yaml
maven-build:
  image: maven:3.9-eclipse-temurin-11
  volumes:
    - ./flink:/app
    - ~/.m2:/root/.m2
  working_dir: /app
  command: mvn clean package -DskipTests
```

Then:
```bash
docker-compose run --rm maven-build
```

---

## Summary

**Just getting started?**
→ Use `./docker-run.sh submit` (Option 1)

**Building frequently / developing?**
→ Install Maven locally (Option 3)

**CI/CD or consistent builds?**
→ Use `./docker-build.sh` (Option 2)

**All options produce the same JAR!** Choose what works best for you. 🚀
