# Temporal Examples - AI/ML Pipeline & Resource Optimization

Production-ready examples demonstrating Temporal workflows for AI/ML pipelines, document processing, and resource-optimized execution patterns.

## 🎯 Quick Start

### Prerequisites
- Docker & Docker Compose
- Go 1.21+ / Java 17+ / Python 3.8+
- Temporal Server (via docker-compose)

### Running the Examples

```bash
# Start infrastructure
docker-compose up -d

# Choose your language and run
cd go     # or java, or python
make demo # Runs complete example
```

## 📂 Project Structure

```
temporal-example/
├── go/                 # Go implementation
├── java/              # Java implementation  
├── python/            # Python implementation
├── docs/              # Comprehensive documentation
│   ├── architecture/  # System design & patterns
│   ├── comparisons/   # Temporal vs other systems
│   ├── guides/        # Step-by-step guides
│   ├── patterns/      # Implementation patterns
│   └── quickstart/    # Getting started guides
├── mock-services/     # Mock LLM service
├── init-scripts/      # Database schemas
└── docker-compose.yml # Infrastructure setup
```

## 🚀 Key Examples

### 1. Document Processing Pipeline
5-stage AI pipeline for document processing and RAG:
- **Stage 1**: Data ingestion (CSV parsing, document download)
- **Stage 2**: Preprocessing (text extraction, chunking, PII removal)
- **Stage 3**: Model inference (embeddings, summaries, entity extraction)
- **Stage 4**: Post-processing (quality scoring, metadata enrichment)
- **Stage 5**: Storage (ChromaDB vectors, S3 documents, PostgreSQL metadata)

### 2. Resource-Optimized Workflows
Separating CPU, GPU, and IO-bound activities across specialized workers:
- **CPU Workers**: Text processing, validation, compression
- **GPU Workers**: ML inference, embeddings, OCR
- **IO Workers**: Downloads, uploads, database operations

## 📖 Documentation

See the [docs/](./docs/) directory for comprehensive documentation:

- **Getting Started**: Language-specific READMEs in each implementation folder
- **Architecture**: [AI Pipeline Implementation](./docs/architecture/ai-pipeline-implementation.md)
- **Patterns**: [Resource Optimization Patterns](./docs/patterns/cpu-gpu-io-implementation-summary.md)
- **Comparisons**: [Temporal vs Airflow](./docs/comparisons/temporal-vs-airflow-scalability.md), [Temporal vs Kafka](./docs/comparisons/temporal-vs-kafka-architectural-considerations.md)

## 🔧 Language-Specific Guides

### [Go Implementation](./go/)
- Best performance, minimal resource usage
- Native concurrency with goroutines
- Ideal for high-throughput pipelines

### [Java Implementation](./java/)
- Enterprise-ready with Spring Boot integration
- Rich ecosystem of libraries
- Best for complex business logic

### [Python Implementation](./python/)
- Fastest development with AI/ML libraries
- Native async/await support
- Perfect for data science teams

## 🏃 Running Examples

### Basic Pipeline
```bash
# Terminal 1 - Start worker
cd go && make run-worker

# Terminal 2 - Start workflow
cd go && make run-starter
```

### Resource-Optimized Pipeline
```bash
# Start all specialized workers
make run-resource-optimized

# Execute workflow
make start-resource-optimized
```

## 🔍 Monitoring

- **Temporal UI**: http://localhost:8080
- **Task Queue Metrics**: `temporal task-queue describe --task-queue document-processing`
- **Workflow Status**: `temporal workflow list`

## 🤝 Contributing

See [Documentation Guidelines](./docs/guides/DOCUMENTATION_GUIDELINES.md) for contributing standards.

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.