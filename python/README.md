# Python Implementation - Document Processing Pipeline

Rapid development document processing pipeline implementation in Python with Temporal workflow orchestration. This implementation provides fast prototyping capabilities with excellent AI/ML library integration.

## Prerequisites

- Python 3.11 or later
- pip (Python package manager)
- Docker and Docker Compose (for dependencies)

## Features

- **Rapid Development**: Fast iteration and prototyping
- **AI/ML Integration**: Native support for popular ML libraries
- **Async Operations**: Built on asyncio for high concurrency
- **Resource-Optimized Execution**: Specialized workers for CPU, GPU, and IO operations
- **Easy Setup**: Simple pip install and minimal configuration
- **Development Friendly**: Interactive development with comprehensive testing

## Project Structure

```
python/
├── activities/              # Activity implementations
│   ├── cpu/                # CPU-bound activities
│   ├── gpu/                # GPU-bound activities
│   ├── io/                 # IO-bound activities
│   ├── ingestion/         # CSV parsing, document downloading
│   ├── preprocessing/     # Text extraction, chunking
│   ├── inference/         # Embeddings, summarization
│   ├── postprocessing/    # Quality scoring, metadata
│   └── storage/           # Vector DB, S3, PostgreSQL
├── workflows/             # Workflow implementations
├── workers/               # Worker implementations
├── models/                # Data models
├── utils/                 # Utility functions
├── tests/                 # Test files
├── requirements.txt       # Python dependencies
├── Makefile              # Build and run commands
├── start_resource_optimized.py  # Resource-optimized starter
└── README.md             # This file
```

## Quick Start

### 1. Install Dependencies

```bash
# Install Python dependencies
pip install -r requirements.txt

# Or using virtual environment (recommended)
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt

# Or using Make
make install
```

### 2. Start Infrastructure

```bash
# From project root
docker-compose up -d

# Verify services are running
docker-compose ps
```

### 3. Run Resource-Optimized Pipeline

```bash
# Terminal 1: Workflow orchestration only
make run-workflow-worker
# Or: python workers/workflow_worker.py

# Terminal 2: CPU-intensive activities
make run-cpu-worker
# Or: python workers/cpu_worker.py

# Terminal 3: GPU-accelerated activities  
make run-gpu-worker
# Or: python workers/gpu_worker.py

# Terminal 4: IO-intensive activities
make run-io-worker
# Or: python workers/io_worker.py

# Terminal 5: Start resource-optimized workflow
make run-optimized
# Or: python test_resource_optimized.py
```

## Workflow Patterns

### Resource-Optimized Pipeline

The Python implementation focuses on the resource-optimized pattern with specialized workers:

- **File**: `workflows/resource_optimized.py`
- **Use Case**: AI/ML workloads, high-concurrency processing
- **Setup**: 4 workers optimized for different resource types
- **Performance**: Optimized for machine learning pipelines

### Worker Specialization

Each worker type is optimized for specific operations:

- **Workflow Worker**: Pure orchestration, no activity execution
- **CPU Worker**: Text processing, validation (multiprocessing support)
- **GPU Worker**: ML inference, embeddings (GPU scheduling)
- **IO Worker**: Downloads, uploads, database operations (high async concurrency)

## Key Components

### Resource-Optimized Activities

#### CPU-Bound Activities (cpu-bound-queue)
- `preprocess_text`: Text normalization and cleaning
- `validate_document_structure`: Structure validation
- `extract_text_from_document`: CPU-intensive text extraction
- `tokenize_text`: Text tokenization and analysis
- `compress_document`: Document compression

#### GPU-Bound Activities (gpu-bound-queue)
- `generate_embeddings`: Vector embeddings using GPU
- `classify_document`: Document classification with ML models
- `perform_ocr`: Optical character recognition
- `analyze_images`: Image analysis and feature extraction
- `run_llm_inference`: Large language model inference

#### IO-Bound Activities (io-bound-queue)
- `parse_csv`: CSV file parsing
- `download_document`: Async document downloading
- `upload_to_s3`: S3/MinIO uploads with streaming
- `query_metadata_database`: Database queries
- `store_vector_embeddings`: ChromaDB storage
- `call_external_api`: External API calls

### Workflow Implementation

The `ResourceOptimizedWorkflow` orchestrates the document processing pipeline:

1. **Task Queue Separation**: Routes activities to specialized workers
2. **Resource-Aware Scheduling**: CPU, GPU, and IO tasks run on appropriate workers
3. **Improved Parallelism**: Different resource types process concurrently
4. **Better Resource Utilization**: Prevents resource contention

### Worker Configurations

**Workflow Worker**:
- Only handles workflow orchestration
- No activity execution

**CPU Worker**:
- Uses multiprocessing for CPU-intensive tasks
- Configured for `cpu_count * 2` concurrent activities

**GPU Worker**:
- Round-robin GPU scheduling
- Configured for `gpu_count * 4` concurrent activities

**IO Worker**:
- High concurrency (200 concurrent activities)
- Optimized for async I/O operations

## Development

### Code Formatting

```bash
make format
```

### Linting

```bash
make lint
```

### Running Tests

```bash
make test
```

### Clean Build Artifacts

```bash
make clean
```

## Configuration

All configuration is done through environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| TEMPORAL_HOST | Temporal server address | localhost:7233 |
| S3_ENDPOINT | S3/MinIO endpoint | http://localhost:9000 |
| S3_ACCESS_KEY | S3 access key | minioadmin |
| S3_SECRET_KEY | S3 secret key | minioadmin |
| S3_BUCKET | S3 bucket name | documents |
| VECTOR_DB_URL | ChromaDB URL | http://localhost:8000 |
| METADATA_DB_HOST | PostgreSQL host | localhost |
| METADATA_DB_PORT | PostgreSQL port | 5433 |
| METADATA_DB_NAME | Database name | document_metadata |
| METADATA_DB_USER | Database user | docuser |
| METADATA_DB_PASSWORD | Database password | docpass |
| GPU_COUNT | Number of GPUs | 2 |

## Performance Tuning

### Worker Concurrency

Adjust the `max_concurrent_activities` parameter in each worker:

```python
# CPU Worker
max_concurrent_activities=multiprocessing.cpu_count() * 2

# GPU Worker
max_concurrent_activities=gpu_count * 4

# IO Worker
max_concurrent_activities=200
```

### Batch Size

Optimize batch size based on your workload:

```bash
python start_resource_optimized.py --batch-size 20
```

### Activity Timeouts

Adjust timeouts in the workflow based on your document sizes and processing requirements.

## Monitoring

### Temporal Web UI

Monitor workflows at: `http://localhost:8088`

### Application Logs

Each worker outputs detailed logs to help with debugging and monitoring.

## Comparison with Other Implementations

| Aspect | Python | Go | Java |
|--------|--------|-----|------|
| Async Support | Native (asyncio) | Goroutines | CompletableFuture |
| Performance | Good | Excellent | Good after warmup |
| Memory Usage | Medium | Low | High |
| Type Safety | Type hints | Strong | Strong |
| Startup Time | Fast | Fast | Slow |
| Ecosystem | Rich ML/AI libs | Growing | Mature |

## Troubleshooting

### Common Issues

1. **ImportError**: Make sure virtual environment is activated
   ```bash
   source venv/bin/activate
   ```

2. **Connection Refused**: Verify all services are running
   ```bash
   docker-compose ps
   ```

3. **Activity Timeout**: Increase timeout in workflow activity options

4. **Memory Issues**: Reduce batch size or worker concurrency

## Next Steps

1. Add comprehensive unit tests
2. Implement integration tests
3. Add metrics collection with Prometheus
4. Implement caching for embeddings
5. Add real GPU support (currently simulated)
6. Enhance error recovery strategies