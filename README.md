# Temporal Document Processing Pipeline

A production-ready document processing pipeline built with Temporal workflow orchestration. This system demonstrates how to build scalable AI/ML pipelines for document ingestion, processing, and RAG (Retrieval-Augmented Generation) preparation.

**Multi-Language Support**: Complete implementations in Go, Java, and Python with two workflow patterns: basic pipeline and resource-optimized execution.

## Architecture Overview

The pipeline implements a 5-stage document processing workflow:

1. **Data Ingestion** - Download documents from URLs or local paths
2. **Pre-processing** - Extract text, chunk documents, detect language, remove PII
3. **Model Inference** - Generate embeddings and summaries using LLM APIs
4. **Post-processing** - Quality scoring, metadata enrichment, search indexing
5. **Storage** - Store in vector DB (ChromaDB), S3 (MinIO), and PostgreSQL

### Why Temporal for Vector Embeddings?

Temporal is particularly well-suited for embedding generation because it provides:

- **Durability**: Automatic recovery from failures during long-running embedding generation
- **Scalability**: Easy horizontal scaling as data volumes grow
- **Reliability**: Built-in retries for flaky API calls and rate limits
- **Observability**: Complete audit trail of how each embedding was generated
- **Long-running support**: Process massive datasets over hours or days without timeouts

See [Why Temporal for Vector Embeddings](docs/temporal-vector-embeddings.md) for detailed analysis.

## Features

- **Multi-Language Support**: Complete implementations in Go, Java, and Python
- **Two Workflow Patterns**: 
  - **Basic Pipeline**: Single worker handling all activities
  - **Resource-Optimized**: Separate workers for CPU, GPU, and IO-bound activities
- **Scalable Architecture**: Worker-based processing with horizontal scaling
- **Fault Tolerance**: Automatic retries, state persistence, and graceful error handling
- **Observability**: Comprehensive metrics, logging, and workflow visualization
- **Local Development**: Complete Docker Compose environment with mock services
- **Production Ready**: Kubernetes configurations and cloud deployment guides
- **Human-in-the-Loop**: Support for quality review and manual intervention
- **Version Evolution**: Safe updates without affecting in-flight workflows

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Go 1.21+ (for Go implementation)
- Java 17+ and Gradle (for Java implementation)
- Python 3.11+ and pip (for Python implementation)
- Make (optional, for convenience commands)

### 1. Start the Infrastructure

```bash
# Start all services
docker-compose up -d

# Wait for services to be healthy
docker-compose ps

# Create S3 bucket
make -C go setup-minio

# Initialize database
make -C go setup-db
```

### 2. Run the Go Implementation

```bash
cd go

# Install dependencies
make deps

# Run the worker
make run-local-worker

# In another terminal, start a workflow
make run-local-starter
```

### 3. Run the Java Implementation

```bash
cd java

# Run the worker
./gradlew runWorker

# In another terminal, start a workflow
./gradlew runStarter
```

### 4. Run the Python Implementation

```bash
cd python

# Install dependencies
pip install -r requirements.txt

# Run the worker
make run-worker

# In another terminal, start a workflow
make run-starter
```

## Project Structure

```
temporal-example/
├── go/                      # Go implementation
│   ├── cmd/                 # Main applications
│   │   ├── worker/         # Basic worker process
│   │   ├── starter/        # Basic workflow starter
│   │   ├── cpu-worker/     # CPU-optimized worker
│   │   ├── gpu-worker/     # GPU-optimized worker
│   │   ├── io-worker/      # IO-optimized worker
│   │   ├── workflow-worker/ # Workflow-only worker
│   │   └── resource-optimized-starter/ # Resource-optimized starter
│   ├── activities/         # Activity implementations
│   │   ├── cpu/           # CPU-bound activities  
│   │   ├── gpu/           # GPU-bound activities
│   │   └── io/            # IO-bound activities
│   ├── workflows/          # Workflow definitions
│   ├── internal/models/    # Data models
│   └── Makefile           # Build and run commands
├── java/                    # Java implementation
│   ├── src/main/java/com/example/
│   │   ├── activities/     # Activity implementations
│   │   │   ├── cpu/       # CPU-bound activities
│   │   │   ├── gpu/       # GPU-bound activities
│   │   │   └── io/        # IO-bound activities
│   │   ├── workflows/      # Workflow definitions
│   │   ├── workers/        # Worker configurations
│   │   └── models/         # Data models
│   └── build.gradle        # Gradle build configuration
├── python/                  # Python implementation
│   ├── activities/         # Activity implementations
│   │   ├── cpu/           # CPU-bound activities
│   │   ├── gpu/           # GPU-bound activities
│   │   └── io/            # IO-bound activities
│   ├── workflows/          # Workflow definitions
│   ├── workers/            # Worker processes
│   ├── requirements.txt    # Python dependencies
│   └── Makefile           # Build and run commands
├── docker-compose.yml       # Local development environment
├── testdata/               # Sample test data
├── init-scripts/           # Database initialization
└── docs/                   # Additional documentation
```

## Workflow Examples

This project demonstrates two different workflow patterns:

### 1. Basic Document Pipeline

A traditional single-worker approach where one worker handles all activity types:

- **Single Task Queue**: `document-processing`
- **Worker Configuration**: Handles all activities (CPU, GPU, IO-bound)
- **Use Case**: Simple setups, small to medium workloads
- **Files**: 
  - Go: `workflows/document_pipeline.go`
  - Java: `workflows/DocumentPipelineWorkflow.java`
  - Python: `workflows/document_pipeline.py`

### 2. Resource-Optimized Pipeline

Advanced multi-worker approach with resource-specific task queues:

- **Task Queues**:
  - `document-pipeline-queue`: Workflow orchestration only
  - `cpu-bound-queue`: Text processing, validation, compression
  - `gpu-bound-queue`: ML inference, embeddings, OCR
  - `io-bound-queue`: Downloads, uploads, database operations
- **Worker Specialization**: Each worker optimized for specific resource types
- **Use Case**: High-throughput, production workloads, resource optimization
- **Files**:
  - Go: `workflows/resource_optimized.go`
  - Java: `workflows/ResourceOptimizedWorkflow.java`
  - Python: `workflows/resource_optimized.py`

### Choosing the Right Pattern

| Factor | Basic Pipeline | Resource-Optimized |
|--------|---------------|-------------------|
| Setup Complexity | Simple (1 worker) | Complex (4 workers) |
| Resource Efficiency | Mixed workload | Optimized per type |
| Scalability | Vertical scaling | Horizontal scaling |
| Best For | Development, small scale | Production, high scale |

## Implementation Guides

Each language implementation has its own detailed setup guide:

- **Go**: [go/README.md](go/README.md) - High-performance implementation
- **Java**: [java/README.md](java/README.md) - Enterprise-ready implementation  
- **Python**: [python/README.md](python/README.md) - Rapid development implementation

## Core Services

### Temporal
- **UI**: http://localhost:8080
- **gRPC**: localhost:7233

Monitor workflow executions, view history, and debug issues.

### MinIO (S3 Compatible)
- **Console**: http://localhost:9001 (minioadmin/minioadmin)
- **API**: http://localhost:9000

Object storage for processed documents.

### ChromaDB
- **API**: http://localhost:8000

Vector database for document embeddings.

### PostgreSQL
- **Metadata DB**: localhost:5433 (docuser/docpass)

Stores pipeline metadata and processing history.

## Development

### Running Tests

```bash
# Go tests
cd go && make test

# Java tests
cd java && ./gradlew test
```

### Building Docker Images

```bash
# Go worker
cd go && docker build -t temporal-rag-go:latest .

# Java worker
cd java && ./gradlew buildDocker
```

### Configuration

Environment variables for worker configuration:

```bash
# Temporal
TEMPORAL_HOST_URL=localhost:7233

# Storage
S3_ENDPOINT=http://localhost:9000
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin
S3_BUCKET=documents

# Vector DB
VECTOR_DB_URL=http://localhost:8000

# Metadata DB
METADATA_DB_HOST=localhost
METADATA_DB_PORT=5433
METADATA_DB_NAME=document_metadata
METADATA_DB_USER=docuser
METADATA_DB_PASSWORD=docpass

# LLM Service
LLM_SERVICE_URL=http://localhost:8081
USE_MOCK_SERVICES=true
```

## Workflow Options

When starting a workflow, you can configure:

```bash
# Go starter
./bin/starter \
  --csv-file ../testdata/documents.csv \
  --s3-bucket documents \
  --vector-db-collection documents \
  --embedding-model text-embedding-3-small \
  --summary-model gpt-3.5-turbo \
  --remove-pii \
  --languages en,es

# Java starter
java -jar build/libs/temporal-rag.jar \
  --csv-file ../testdata/documents.csv \
  --s3-bucket documents \
  --vector-db-collection documents \
  --embedding-model text-embedding-3-small \
  --summary-model gpt-3.5-turbo
```

## Performance Comparison

| Aspect | Go | Java | Python |
|--------|----|---------|---------|
| Throughput | 15,000+ workflows/sec | 10,000+ workflows/sec | 5,000+ workflows/sec |
| Memory Usage | ~100MB | ~500MB | ~200MB |
| Startup Time | <1 second | 5-10 seconds | 2-3 seconds |
| GC Pause | Minimal | Configurable | Automatic |
| Development Speed | Fast | Moderate | Very Fast |
| Best For | High throughput, microservices | Enterprise integration, complex logic | Rapid prototyping, AI/ML |

## Production Deployment

### Kubernetes

See `docs/kubernetes-deployment.md` for:
- Helm charts
- Resource configurations
- Auto-scaling setup
- Monitoring integration

### Cloud Providers

- **AWS**: Use EKS with Aurora PostgreSQL and S3
- **GCP**: Use GKE with Cloud SQL and GCS
- **Azure**: Use AKS with Azure PostgreSQL and Blob Storage

## Monitoring

### Metrics
- Workflow completion rate
- Document processing throughput
- Error rates by stage
- Model inference latency
- Storage usage trends

### Recommended Stack
- Prometheus + Grafana for metrics
- Elasticsearch + Kibana for logs
- Jaeger for distributed tracing

## Troubleshooting

### Common Issues

1. **Worker not connecting**
   - Check Temporal is running: `docker-compose ps`
   - Verify network connectivity
   - Check logs: `docker-compose logs temporal`

2. **Storage errors**
   - Ensure MinIO bucket exists
   - Check credentials in environment
   - Verify PostgreSQL schema is created

3. **Embedding failures**
   - Check LLM service is running
   - Verify API keys (if using real services)
   - Check rate limits

### Debug Commands

```bash
# View worker logs
docker-compose logs -f go-worker

# Check Temporal workflows
temporal workflow list

# Inspect PostgreSQL
psql -h localhost -p 5433 -U docuser -d document_metadata

# Test MinIO connection
mc alias set local http://localhost:9000 minioadmin minioadmin
mc ls local/
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

MIT License - see LICENSE file for details

## Acknowledgments

- Temporal.io for workflow orchestration
- ChromaDB for vector storage
- MinIO for S3-compatible storage
- OpenAI for embedding models

## Resource-Optimized Execution

All three implementations support resource-optimized execution. See the detailed implementation guides for setup instructions:

- **Go**: [go/README.md#resource-optimized-execution](go/README.md#resource-optimized-execution)
- **Java**: [java/README.md#resource-optimized-execution](java/README.md#resource-optimized-execution)  
- **Python**: [python/README.md#resource-optimized-execution](python/README.md#resource-optimized-execution)

### Benefits of Resource Optimization

1. **Better Resource Utilization**: Each worker type is optimized for its workload
2. **Improved Scalability**: Scale workers independently based on bottlenecks
3. **Reduced Contention**: Different resource types don't compete
4. **Flexible Deployment**: Deploy workers on appropriate hardware

### Task Queue Architecture

- **document-pipeline-queue**: Workflow orchestration only
- **cpu-bound-queue**: CPU-intensive activities (text processing, validation)
- **gpu-bound-queue**: GPU-accelerated activities (ML inference, embeddings)
- **io-bound-queue**: IO-intensive activities (downloads, uploads, DB operations)

## Documentation

### Implementation Guides

- **[Go Implementation Guide](go/README.md)** - High-performance implementation with detailed setup instructions
- **[Java Implementation Guide](java/README.md)** - Enterprise-ready implementation with Spring Boot integration
- **[Python Implementation Guide](python/README.md)** - Rapid development implementation with AI/ML focus

### Architecture Documentation

- **[Resource-Optimized Execution Guide](docs/resource-optimized-execution-guide.md)** - Complete guide to resource-specific task queues
- **[Workflow Orchestration Comparison](docs/workflow-orchestration-comparison.md)** - Temporal vs other orchestration approaches
- **[AI Pipeline Implementation](docs/ai-workflow-implementation.md)** - AI/ML pipeline patterns and best practices

### Setup and Operations

- **[CPU vs GPU Implementation Summary](docs/cpu-gpu-io-implementation-summary.md)** - Resource optimization strategies
- **[Python Workflow Implementation](docs/python-workflow-implementation.md)** - Python-specific patterns and optimizations

### External Resources

- [Temporal Documentation](https://docs.temporal.io) - Official Temporal documentation
- [ChromaDB Documentation](https://docs.trychroma.com) - Vector database documentation
- [MinIO Documentation](https://docs.min.io) - S3-compatible storage documentation