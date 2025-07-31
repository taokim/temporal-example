# Java Implementation

Enterprise-ready document processing pipeline with Spring Boot and Temporal.

## Quick Start

```bash
# Run complete demo (starts worker + workflow)
./gradlew demo

# Or run separately:
./gradlew runWorker     # Terminal 1
./gradlew runStarter    # Terminal 2
```

## Resource-Optimized Execution

Run specialized workers for CPU/GPU/IO separation:

```bash
# Start all workers
./gradlew runResourceOptimized

# Execute workflow
./gradlew startResourceOptimized
```

## Project Structure

```
java/
├── src/main/java/com/example/
│   ├── activities/     # Activity implementations
│   │   ├── cpu/       # Text processing, validation
│   │   ├── gpu/       # ML inference, embeddings
│   │   ├── io/        # Network, disk, database
│   │   └── ...        # Pipeline stage activities
│   ├── workflows/     # Workflow implementations
│   ├── workers/       # Specialized worker classes
│   ├── models/        # Data models (with Lombok)
│   └── config/        # Spring configuration
└── build.gradle       # Dependencies and tasks
```

## Available Commands

```bash
./gradlew build               # Build project
./gradlew test                # Run tests
./gradlew runWorker           # Run basic worker
./gradlew runStarter          # Start workflow
./gradlew demo                # Run complete demo
./gradlew buildDocker         # Build Docker image
./gradlew shadowJar           # Create fat JAR
```

## Performance Characteristics

- **Throughput**: 10,000+ workflows/second
- **Memory**: ~200-300MB per worker (JVM)
- **Startup**: 3-5 seconds
- **Best for**: Enterprise environments, complex business logic

## Configuration

Spring profiles for different workers:
- `workflow-worker`: Workflow-only worker
- `cpu-worker`: CPU-bound activities
- `gpu-worker`: GPU-bound activities  
- `io-worker`: IO-bound activities

Environment variables:
- `TEMPORAL_SERVICE_ADDRESS`: Temporal server
- `TEMPORAL_NAMESPACE`: Namespace
- `S3_ENDPOINT`: MinIO endpoint
- `VECTOR_DB_URL`: ChromaDB URL

See [main README](../README.md) for complete documentation.