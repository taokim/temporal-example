# Resource-Optimized Temporal Execution Guide

## Overview

This guide explains how to run the resource-optimized Temporal workflow that separates CPU-bound, GPU-bound, and IO-bound activities into different task queues for optimal resource utilization.

## Architecture

### Task Queue Separation

The resource-optimized implementation uses four separate task queues:

1. **`document-pipeline-queue`** - Workflow orchestration only
2. **`cpu-bound-queue`** - CPU-intensive activities (text processing, validation, compression)
3. **`gpu-bound-queue`** - GPU-accelerated activities (ML inference, embeddings, OCR)
4. **`io-bound-queue`** - IO-intensive activities (downloads, uploads, database queries)

### Worker Types

#### 1. Workflow Worker
- **Purpose**: Orchestrates workflows without executing activities
- **Resource Profile**: Low memory, high concurrency
- **Configuration**: 
  - Max concurrent workflows: 1000
  - No activity execution
  - Minimal resource requirements

#### 2. CPU Worker
- **Purpose**: Executes CPU-intensive text processing tasks
- **Resource Profile**: High CPU, moderate memory
- **Configuration**:
  - Max concurrent activities: CPU cores × 2
  - Uses ForkJoinPool for parallel processing
  - Optimized for compute-intensive operations

#### 3. GPU Worker
- **Purpose**: Executes ML inference and GPU-accelerated tasks
- **Resource Profile**: GPU memory, CUDA cores
- **Configuration**:
  - Max concurrent activities: Number of GPUs
  - Batch processing for efficiency
  - GPU resource management

#### 4. IO Worker
- **Purpose**: Handles network and database operations
- **Resource Profile**: High concurrency, low CPU
- **Configuration**:
  - Max concurrent activities: 200
  - Async IO operations
  - Connection pooling

## Running the Resource-Optimized Pipeline

### Step 1: Start Docker Services

```bash
cd /Users/musinsa/ws/temporal-example
docker-compose up -d
```

### Step 2: Build the Java Project

```bash
cd java
./gradlew clean build
```

### Step 3: Start Workers (in separate terminals)

#### Terminal 1: Workflow Worker
```bash
./gradlew runWorkflowWorker
```

Expected output:
```
[WORKFLOW-WORKER] - Workflow Worker started on task queue: document-pipeline-queue
[WORKFLOW-WORKER] - This worker handles workflow orchestration only
[WORKFLOW-WORKER] - Activities are executed on specialized workers:
[WORKFLOW-WORKER] -   - CPU-bound activities: cpu-bound-queue
[WORKFLOW-WORKER] -   - GPU-bound activities: gpu-bound-queue
[WORKFLOW-WORKER] -   - IO-bound activities: io-bound-queue
```

#### Terminal 2: CPU Worker
```bash
./gradlew runCPUWorker
```

Expected output:
```
[CPU-WORKER] - CPU Worker started on task queue: cpu-bound-queue with 8 cores
[CPU-WORKER] - Optimized for CPU-intensive operations
```

#### Terminal 3: GPU Worker
```bash
# Set GPU count if different from default
export GPU_COUNT=2
./gradlew runGPUWorker
```

Expected output:
```
[GPU-WORKER] - GPU Worker started on task queue: gpu-bound-queue with 2 GPUs
[GPU-WORKER] - Optimized for GPU-accelerated ML inference and image processing
```

#### Terminal 4: IO Worker
```bash
./gradlew runIOWorker
```

Expected output:
```
[IO-WORKER] - IO Worker started on task queue: io-bound-queue
[IO-WORKER] - Optimized for network IO, database queries, and external API calls
[IO-WORKER] - Configured for high concurrency with async IO operations
```

### Step 4: Run the Optimized Workflow

In a new terminal:
```bash
./gradlew runOptimizedStarter
```

Or with custom parameters:
```bash
java -cp build/libs/temporal-rag-pipeline-spring-1.0.0.jar \
  com.example.ResourceOptimizedStarter \
  --csv-file ../testdata/documents.csv \
  --batch-size 10 \
  --timeout 120
```

## Activity Distribution

### CPU-Bound Activities
- **Text Preprocessing**: Normalization, tokenization, language detection
- **Document Validation**: Structure validation, format checking
- **Text Extraction**: Parsing documents, extracting content
- **Compression**: GZIP compression of documents
- **Word Frequency Analysis**: Statistical text analysis

### GPU-Bound Activities
- **Embedding Generation**: Vector embeddings using transformer models
- **Document Classification**: ML-based categorization
- **OCR Processing**: Optical character recognition
- **Image Analysis**: Object detection, scene understanding
- **LLM Inference**: Large language model inference

### IO-Bound Activities
- **Document Download**: HTTP/HTTPS file downloads
- **S3 Upload**: MinIO/S3 object storage
- **Database Queries**: PostgreSQL metadata queries
- **Vector Storage**: ChromaDB embedding storage
- **External API Calls**: REST API integrations

## Performance Optimization

### CPU Worker Optimization
```java
// Parallel processing using all cores
ForkJoinPool customThreadPool = new ForkJoinPool(processors);
customThreadPool.submit(() -> 
    input.getChunks().parallelStream()
        .map(this::processChunk)
        .collect(Collectors.toList())
).get();
```

### GPU Worker Optimization
```java
// Batch processing for GPU efficiency
int batchSize = calculateOptimalBatchSize(chunks.size());
for (int i = 0; i < chunks.size(); i += batchSize) {
    List<TextChunk> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
    float[][] batchEmbeddings = processBatchOnGPU(batch, gpuDevice);
}
```

### IO Worker Optimization
```java
// Async IO operations
CompletableFuture<byte[]> downloadFuture = CompletableFuture.supplyAsync(() -> {
    // Non-blocking download
}, ioExecutor);
```

## Monitoring and Scaling

### Worker Metrics

Each worker type exposes specific metrics:

#### CPU Worker Metrics
- CPU utilization percentage
- Processing rate (documents/second)
- Thread pool statistics
- Memory usage

#### GPU Worker Metrics
- GPU memory usage
- GPU temperature
- Inference throughput
- Batch processing efficiency

#### IO Worker Metrics
- Active connections
- Request latency
- Throughput (MB/s)
- Connection pool statistics

### Scaling Strategies

#### Horizontal Scaling
```bash
# Scale CPU workers based on load
for i in {1..4}; do
  ./gradlew runCPUWorker &
done

# Scale IO workers for high throughput
for i in {1..2}; do
  ./gradlew runIOWorker &
done
```

#### Vertical Scaling
```bash
# Increase memory for GPU worker
./gradlew runGPUWorker -Dorg.gradle.jvmargs="-Xmx8g"

# Increase CPU worker threads
export WORKER_CPU_THREADS=16
./gradlew runCPUWorker
```

## Kubernetes Deployment

### CPU Worker Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: temporal-cpu-worker
spec:
  replicas: 4
  selector:
    matchLabels:
      app: temporal-cpu-worker
  template:
    spec:
      containers:
      - name: cpu-worker
        image: temporal-rag-java:latest
        args: ["cpu-worker"]
        resources:
          requests:
            cpu: "2"
            memory: "4Gi"
          limits:
            cpu: "4"
            memory: "8Gi"
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "cpu-worker"
```

### GPU Worker Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: temporal-gpu-worker
spec:
  replicas: 2
  selector:
    matchLabels:
      app: temporal-gpu-worker
  template:
    spec:
      containers:
      - name: gpu-worker
        image: temporal-rag-java-gpu:latest
        args: ["gpu-worker"]
        resources:
          requests:
            nvidia.com/gpu: 1
          limits:
            nvidia.com/gpu: 1
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "gpu-worker"
      nodeSelector:
        accelerator: nvidia-tesla-v100
```

## Troubleshooting

### Common Issues

1. **Worker not picking up activities**
   - Verify task queue names match exactly
   - Check worker is registered for the correct activities
   - Ensure Temporal server is accessible

2. **GPU out of memory**
   - Reduce batch size in GPU activities
   - Check GPU memory reservation settings
   - Monitor concurrent GPU activities

3. **IO worker connection exhaustion**
   - Increase connection pool size
   - Check for connection leaks
   - Monitor external service availability

4. **CPU worker performance degradation**
   - Check for excessive thread contention
   - Monitor GC activity
   - Verify CPU affinity settings

### Debug Commands

```bash
# Check worker status
temporal task-queue describe --task-queue cpu-bound-queue

# Monitor workflow execution
temporal workflow show --workflow-id resource-optimized-pipeline-xxx

# Check activity failures
temporal workflow list --query 'ExecutionStatus="Failed"'
```

## Best Practices

1. **Resource Allocation**
   - Dedicate nodes for GPU workers
   - Use CPU affinity for CPU workers
   - Isolate IO workers from compute-intensive workloads

2. **Queue Management**
   - Monitor queue depths
   - Set appropriate activity timeouts
   - Use dead letter queues for failed activities

3. **Error Handling**
   - Implement activity-specific retry policies
   - Use compensation patterns for critical operations
   - Monitor and alert on repeated failures

4. **Performance Tuning**
   - Profile activities to identify bottlenecks
   - Optimize batch sizes for GPU operations
   - Use connection pooling for IO operations
   - Implement caching where appropriate

This resource-optimized approach enables efficient utilization of different hardware resources, improved scalability, and better performance for document processing pipelines.