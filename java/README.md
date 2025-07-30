# Java Document Pipeline Implementation

This is the Java implementation of the Temporal-based document processing pipeline for RAG (Retrieval-Augmented Generation).

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker and Docker Compose
- Running Temporal cluster
- Running PostgreSQL, MinIO, ChromaDB, and Mock LLM service

## Project Structure

```
java/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/
│       │       ├── activities/           # Activity implementations
│       │       │   ├── ingestion/       # CSV parsing, document downloading
│       │       │   ├── preprocessing/   # Text extraction, chunking
│       │       │   ├── inference/       # Embeddings, summarization
│       │       │   ├── postprocessing/  # Quality scoring, metadata
│       │       │   └── storage/         # Vector DB, S3, PostgreSQL
│       │       ├── models/              # Data models (POJOs)
│       │       ├── utils/               # Utility classes
│       │       ├── workflows/           # Workflow implementation
│       │       ├── config/              # Spring configuration
│       │       ├── DocumentPipelineWorker.java    # Worker main class
│       │       └── DocumentPipelineStarter.java   # Workflow starter
│       └── resources/
│           └── application.yml          # Spring Boot configuration
├── scripts/                             # Build and run scripts
├── pom.xml                              # Maven configuration
└── Dockerfile                           # Container configuration
```

## Quick Start

### 1. Build the Project

```bash
cd java
./scripts/build.sh
```

### 2. Start Services

Make sure all required services are running:

```bash
# From the root directory
docker-compose up -d
```

### 3. Run the Worker

```bash
./scripts/run-worker.sh
```

### 4. Start a Workflow

```bash
# Basic usage
./scripts/start-workflow.sh ../data/documents.csv

# With custom parameters
./scripts/start-workflow.sh ../data/documents.csv 20 100

# Wait for completion
./scripts/start-workflow.sh ../data/documents.csv 10 50 wait
```

## Configuration

The application can be configured via `application.yml` or environment variables:

### Database Configuration
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/temporal_rag
    username: postgres
    password: postgres
```

### S3/MinIO Configuration
```yaml
s3:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
```

### ChromaDB Configuration
```yaml
chromadb:
  url: http://localhost:8000
```

### LLM Service Configuration
```yaml
llm:
  service:
    url: http://localhost:8081
  use-mock: true
```

## Docker Deployment

### Build Docker Image

```bash
docker build -t temporal-java-worker .
```

### Run with Docker Compose

```bash
docker-compose up
```

## Key Components

### Activities

1. **IngestionActivities**
   - `parseCSVActivity`: Reads document metadata from CSV
   - `downloadDocumentsActivity`: Downloads documents in batches

2. **PreprocessingActivities**
   - `preprocessDocuments`: Extracts text, chunks documents, detects language

3. **InferenceActivities**
   - `runInferenceBatch`: Generates embeddings and summaries

4. **PostprocessingActivities**
   - `postprocessDocuments`: Calculates quality scores, enriches metadata

5. **StorageActivities**
   - `storeInVectorDB`: Stores embeddings in ChromaDB
   - `storeInS3`: Stores processed documents in S3/MinIO
   - `storeMetadata`: Stores pipeline metadata in PostgreSQL

### Workflow Implementation

The `DocumentPipelineWorkflowImpl` orchestrates the entire pipeline:

1. Parses CSV to get document list
2. Processes documents in configurable batches
3. Each batch goes through all pipeline stages
4. Parallel processing within batches
5. Aggregates results and stores metadata

### Key Features

- **Batch Processing**: Configurable batch sizes for efficiency
- **Parallel Execution**: Within-batch parallelism using Temporal's Async API
- **Error Handling**: Graceful degradation with partial failures
- **Heartbeats**: Long-running activities report progress
- **Retries**: Configurable retry policies for resilience
- **Spring Boot Integration**: Leverages Spring's DI and configuration

## Performance Tuning

### JVM Options

The Dockerfile includes optimized JVM settings:

```dockerfile
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC"
```

### Worker Configuration

Adjust worker settings based on workload:

```java
WorkerFactoryOptions options = WorkerFactoryOptions.newBuilder()
    .setMaxWorkflowThreadCount(100)
    .setMaxActivityThreadCount(50)
    .build();
```

### Database Connection Pool

Configure HikariCP in `application.yml`:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
```

## Monitoring

### Logs

Application logs are written to console and can be redirected:

```bash
./scripts/run-worker.sh > worker.log 2>&1
```

### Metrics

The application exposes Spring Boot Actuator endpoints:

- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`

### Temporal Web UI

Monitor workflows at: `http://localhost:8088`

## Troubleshooting

### Common Issues

1. **OutOfMemoryError**
   - Increase heap size: `export JAVA_OPTS="-Xmx4g"`
   - Reduce batch size in workflow starter

2. **Connection Refused**
   - Verify all services are running: `docker-compose ps`
   - Check service URLs in configuration

3. **Slow Performance**
   - Enable async processing in workflow
   - Increase worker thread counts
   - Optimize batch sizes

### Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    com.example: DEBUG
    io.temporal: DEBUG
```

## Development

### Running Tests

```bash
mvn test
```

### Code Coverage

```bash
mvn jacoco:report
```

### Building JAR Only

```bash
mvn clean package -DskipTests
```

## Comparison with Go Implementation

| Aspect | Java | Go |
|--------|------|-----|
| Memory Usage | Higher (JVM overhead) | Lower |
| Startup Time | Slower (JVM warmup) | Faster |
| Throughput | High after warmup | Consistently high |
| Ecosystem | Rich (Spring, etc.) | Growing |
| Error Handling | Exceptions | Explicit errors |
| Concurrency | Thread-based | Goroutines |

## Next Steps

1. Add comprehensive unit tests
2. Implement integration tests
3. Add metrics collection
4. Implement caching layer
5. Add API endpoints for management
6. Enhance error recovery strategies