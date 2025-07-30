# Temporal Document Processing Pipeline

A production-ready document processing pipeline built with Temporal workflow orchestration. This system demonstrates how to build scalable AI/ML pipelines for document ingestion, processing, and RAG (Retrieval-Augmented Generation) preparation.

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

- **Scalable Architecture**: Worker-based processing with horizontal scaling
- **Multi-Language Support**: Implementations in both Go (high performance) and Java (enterprise-ready)
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

## Project Structure

```
temporal-example/
├── go/                      # Go implementation
│   ├── cmd/                 # Main applications
│   │   ├── worker/         # Worker process
│   │   └── starter/        # Workflow starter
│   ├── internal/           # Internal packages
│   │   ├── activities/     # Activity implementations
│   │   ├── workflows/      # Workflow definitions
│   │   └── models/         # Data models
│   └── Makefile           # Build and run commands
├── java/                    # Java implementation
│   ├── src/main/java/      # Java source code
│   │   ├── activities/     # Activity implementations
│   │   ├── workflows/      # Workflow definitions
│   │   └── models/         # Data models
│   └── build.gradle        # Gradle build configuration
├── docker-compose.yml       # Local development environment
├── testdata/               # Sample test data
├── init-scripts/           # Database initialization
└── docs/                   # Additional documentation
```

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

| Aspect | Go | Java |
|--------|----|---------|
| Throughput | 15,000+ workflows/sec | 10,000+ workflows/sec |
| Memory Usage | ~100MB | ~500MB |
| Startup Time | <1 second | 5-10 seconds |
| GC Pause | Minimal | Configurable |
| Best For | High throughput, microservices | Enterprise integration, complex logic |

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

## Resources

- [Temporal Documentation](https://docs.temporal.io)
- [ChromaDB Documentation](https://docs.trychroma.com)
- [MinIO Documentation](https://docs.min.io)
- [Project Architecture](docs/workflow-orchestration-comparison.md)
- [AI Pipeline Design](docs/ai-pipeline-implementation.md)