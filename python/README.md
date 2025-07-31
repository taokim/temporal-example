# Python Implementation

Fast AI/ML pipeline development with Python's async/await and Temporal.

## Quick Start

```bash
# Install dependencies
pip install -r requirements.txt

# Run complete demo (starts all workers + workflow)
make demo

# Or run separately:
make run-workers        # Terminal 1
make run-workflow       # Terminal 2
```

## Resource-Optimized Execution

Run specialized workers for CPU/GPU/IO separation:

```bash
# Start all workers
make run-resource-optimized

# Execute workflow
python start_resource_optimized.py
```

## Project Structure

```
python/
├── activities/         # Activity implementations
│   ├── cpu/           # Text processing, validation
│   ├── gpu/           # ML inference, embeddings
│   ├── io/            # Network, disk, database
│   └── ...            # Pipeline stage activities
├── workflows/         # Workflow implementations
├── workers/           # Specialized worker scripts
├── models/            # Data models (dataclasses)
└── requirements.txt   # Dependencies
```

## Available Commands

```bash
make setup              # Install dependencies
make test               # Run tests
make run-workers        # Run all workers
make run-workflow       # Start workflow
make demo               # Run complete demo
make clean              # Clean artifacts
```

## Performance Characteristics

- **Throughput**: 5,000+ workflows/second
- **Memory**: ~150-200MB per worker
- **Startup**: 1-2 seconds
- **Best for**: AI/ML teams, rapid prototyping, data science

## Configuration

Environment variables:
- `TEMPORAL_HOST`: Temporal server (default: localhost:7233)
- `TEMPORAL_NAMESPACE`: Namespace (default: default)
- `S3_ENDPOINT`: MinIO endpoint (default: http://localhost:9000)
- `VECTOR_DB_URL`: ChromaDB URL (default: http://localhost:8000)

See [main README](../README.md) for complete documentation.