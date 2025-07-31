# Go Implementation

High-performance document processing pipeline with Temporal workflow orchestration.

## Quick Start

```bash
# Install dependencies
go mod download

# Run complete demo (starts worker + workflow)
make demo

# Or run separately:
make run-worker        # Terminal 1
make run-starter       # Terminal 2
```

## Resource-Optimized Execution

Run specialized workers for CPU/GPU/IO separation:

```bash
# Start all workers
make run-resource-optimized

# Execute workflow
make start-resource-optimized
```

## Project Structure

```
go/
├── activities/          # Activity implementations
│   ├── cpu/            # Text processing, validation
│   ├── gpu/            # ML inference, embeddings
│   ├── io/             # Network, disk, database
│   └── ...             # Pipeline stage activities
├── workflows/          # Workflow implementations
├── cmd/                # Worker and starter mains
└── internal/           # Shared models and utils
```

## Available Commands

```bash
make build              # Build all binaries
make test               # Run tests
make run-worker         # Run basic worker
make run-starter        # Start workflow
make demo               # Run complete demo
make docker-build       # Build Docker image
make clean              # Clean artifacts
```

## Performance Characteristics

- **Throughput**: 15,000+ workflows/second
- **Memory**: ~100MB per worker
- **Startup**: <1 second
- **Best for**: High-throughput, resource-constrained environments

## Configuration

Environment variables:
- `TEMPORAL_HOST_URL`: Temporal server (default: localhost:7233)
- `TEMPORAL_NAMESPACE`: Namespace (default: default)
- `S3_ENDPOINT`: MinIO endpoint (default: http://localhost:9000)
- `VECTOR_DB_URL`: ChromaDB URL (default: http://localhost:8000)

See [main README](../README.md) for complete documentation.