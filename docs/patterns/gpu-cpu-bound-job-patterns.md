# GPU-Bound vs CPU-Bound Job Patterns in Temporal Workflows

## Executive Summary

Understanding the distinction between GPU-bound and CPU-bound jobs is crucial for optimal resource utilization in Temporal workflows. This document provides architectural patterns, implementation examples, and best practices for handling both types of workloads efficiently.

## Resource Characteristics

### CPU-Bound Jobs
- **Characteristics**: High CPU utilization, minimal memory requirements, computation-intensive
- **Examples**: Text processing, data validation, mathematical calculations, parsing
- **Bottleneck**: CPU cores and clock speed
- **Scalability**: Horizontal scaling across CPU cores

### GPU-Bound Jobs  
- **Characteristics**: Parallel processing, high memory bandwidth, specialized compute units
- **Examples**: ML inference, image processing, vector embeddings, neural network training
- **Bottleneck**: GPU memory, CUDA cores, tensor units
- **Scalability**: Vertical scaling with better GPUs, horizontal with multiple GPUs

## Architectural Patterns

### Pattern 1: Separate Task Queues

```java
// CPU-bound activities use CPU-optimized workers
@ActivityInterface
public interface CPUBoundActivities {
    @ActivityMethod
    TextProcessingResult preprocessText(TextInput input);
    
    @ActivityMethod
    ValidationResult validateDocument(Document doc);
    
    @ActivityMethod
    ParsingResult parseStructuredData(RawData data);
}

// GPU-bound activities use GPU-optimized workers
@ActivityInterface  
public interface GPUBoundActivities {
    @ActivityMethod
    EmbeddingResult generateEmbeddings(TextChunks chunks);
    
    @ActivityMethod
    ClassificationResult classifyDocument(Document doc);
    
    @ActivityMethod
    OCRResult performOCR(ImageData image);
}
```

### Pattern 2: Worker Configuration

```java
// CPU-optimized worker configuration
@Component
public class CPUWorkerConfig {
    
    @Bean("cpuWorkerFactory")
    public WorkerFactory createCPUWorkerFactory() {
        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
        
        Worker cpuWorker = factory.newWorker("cpu-bound-queue");
        
        // Configure for CPU-intensive tasks
        cpuWorker.registerWorkflowImplementationTypes(DocumentProcessingWorkflow.class);
        cpuWorker.registerActivitiesImplementations(
            new TextProcessingActivitiesImpl(),
            new ValidationActivitiesImpl()
        );
        
        return factory;
    }
}

// GPU-optimized worker configuration
@Component  
public class GPUWorkerConfig {
    
    @Bean("gpuWorkerFactory")
    public WorkerFactory createGPUWorkerFactory() {
        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
        
        Worker gpuWorker = factory.newWorker("gpu-bound-queue");
        
        // Configure for GPU-intensive tasks
        gpuWorker.registerActivitiesImplementations(
            new MLInferenceActivitiesImpl(),
            new ImageProcessingActivitiesImpl()
        );
        
        return factory;
    }
}
```

## Implementation Examples

### CPU-Bound Activity: Text Processing

```java
@Component
public class TextProcessingActivitiesImpl implements CPUBoundActivities {
    
    @Override
    @ActivityMethod
    public TextProcessingResult preprocessText(TextInput input) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        
        // CPU-intensive text processing
        return ActivityRetryOptions.newBuilder()
            .setInitialInterval(Duration.ofSeconds(1))
            .setMaximumInterval(Duration.ofMinutes(1))
            .setBackoffCoefficient(2.0)
            .setMaximumAttempts(3)
            .build()
            .retry(() -> {
                context.heartbeat("Processing text: " + input.getDocumentId());
                
                // Parallel CPU processing using all available cores
                return processTextParallel(input);
            });
    }
    
    private TextProcessingResult processTextParallel(TextInput input) {
        // Utilize all CPU cores for text processing
        int processors = Runtime.getRuntime().availableProcessors();
        ForkJoinPool customThreadPool = new ForkJoinPool(processors);
        
        try {
            return customThreadPool.submit(() -> 
                input.getChunks().parallelStream()
                    .map(this::processChunk)
                    .collect(Collectors.toList())
            ).get();
        } catch (Exception e) {
            throw new ActivityFailure("Text processing failed", e);
        } finally {
            customThreadPool.shutdown();
        }
    }
    
    private ProcessedChunk processChunk(TextChunk chunk) {
        // CPU-intensive operations:
        // - Text normalization
        // - Language detection  
        // - Entity extraction
        // - Sentiment analysis
        return ProcessedChunk.builder()
            .content(normalizeText(chunk.getContent()))
            .language(detectLanguage(chunk.getContent()))
            .entities(extractEntities(chunk.getContent()))
            .sentiment(analyzeSentiment(chunk.getContent()))
            .build();
    }
}
```

### GPU-Bound Activity: ML Inference

```java
@Component
public class MLInferenceActivitiesImpl implements GPUBoundActivities {
    
    private final GPUModelManager modelManager;
    private final GPUResourcePool gpuPool;
    
    @Override
    @ActivityMethod  
    public EmbeddingResult generateEmbeddings(TextChunks chunks) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        
        // GPU resource management with batching
        return gpuPool.withGPUResource(gpu -> {
            context.heartbeat("Generating embeddings on GPU: " + gpu.getDeviceId());
            
            // Batch processing for GPU efficiency
            List<float[]> embeddings = new ArrayList<>();
            
            // Process in optimal batch sizes for GPU memory
            int batchSize = calculateOptimalBatchSize(chunks.size(), gpu.getMemoryMB());
            
            for (int i = 0; i < chunks.size(); i += batchSize) {
                List<TextChunk> batch = chunks.getChunks()
                    .subList(i, Math.min(i + batchSize, chunks.size()));
                
                // GPU-accelerated embedding generation
                float[][] batchEmbeddings = generateEmbeddingsBatch(batch, gpu);
                embeddings.addAll(Arrays.asList(batchEmbeddings));
                
                // Report progress for long-running GPU jobs
                context.heartbeat(String.format("Processed %d/%d chunks", 
                    Math.min(i + batchSize, chunks.size()), chunks.size()));
            }
            
            return EmbeddingResult.builder()
                .embeddings(embeddings)
                .model(modelManager.getCurrentModel())
                .gpuDevice(gpu.getDeviceId())
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();
        });
    }
    
    private float[][] generateEmbeddingsBatch(List<TextChunk> batch, GPUDevice gpu) {
        // GPU-specific implementation:
        // - CUDA kernel execution
        // - Tensor operations
        // - Memory transfer optimization
        // - Model inference batching
        
        try (GPUContext context = gpu.createContext()) {
            // Load model to GPU memory if not already loaded
            if (!context.isModelLoaded(modelManager.getCurrentModel())) {
                context.loadModel(modelManager.getCurrentModel());
            }
            
            // Prepare input tensors
            float[][] inputTensors = prepareInputTensors(batch);
            
            // Execute inference on GPU
            float[][] outputTensors = context.inferBatch(inputTensors);
            
            return outputTensors;
        }
    }
    
    private int calculateOptimalBatchSize(int totalItems, int gpuMemoryMB) {
        // Calculate batch size based on:
        // - Available GPU memory
        // - Model memory requirements  
        // - Input/output tensor sizes
        int modelMemoryMB = modelManager.getCurrentModel().getMemoryRequirementMB();
        int availableMemoryMB = gpuMemoryMB - modelMemoryMB;
        
        // Estimate memory per item (input + output tensors)
        int memoryPerItemKB = estimateMemoryPerItem();
        
        return Math.max(1, (availableMemoryMB * 1024) / memoryPerItemKB);
    }
}
```

## Resource Management Strategies

### CPU-Bound Resource Management

```java
@Configuration
public class CPUResourceConfig {
    
    @Bean
    @ConfigurationProperties("temporal.cpu-worker")
    public CPUWorkerOptions cpuWorkerOptions() {
        return CPUWorkerOptions.builder()
            .maxConcurrentActivityExecutions(Runtime.getRuntime().availableProcessors() * 2)
            .maxConcurrentWorkflowTaskExecutions(100)
            .maxConcurrentLocalActivityExecutions(Runtime.getRuntime().availableProcessors())
            .build();
    }
    
    @Bean
    public ThreadPoolExecutor cpuBoundExecutor() {
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maximumPoolSize = corePoolSize * 2;
        
        return new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadFactoryBuilder()
                .setNameFormat("cpu-worker-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()
        );
    }
}
```

### GPU-Bound Resource Management

```java
@Configuration
public class GPUResourceConfig {
    
    @Bean
    @ConfigurationProperties("temporal.gpu-worker")
    public GPUWorkerOptions gpuWorkerOptions() {
        return GPUWorkerOptions.builder()
            // Limit concurrent GPU activities to available GPUs
            .maxConcurrentActivityExecutions(getAvailableGPUCount())
            .maxConcurrentWorkflowTaskExecutions(50)
            // GPU activities should not run locally
            .maxConcurrentLocalActivityExecutions(0)
            .build();
    }
    
    @Bean
    public GPUResourcePool gpuResourcePool() {
        return GPUResourcePool.builder()
            .withGPUDevices(detectAvailableGPUs())
            .withResourceTimeout(Duration.ofMinutes(30))
            .withHealthCheckInterval(Duration.ofSeconds(30))
            .withMemoryReservation(0.8f) // Reserve 80% of GPU memory
            .build();
    }
    
    private List<GPUDevice> detectAvailableGPUs() {
        // Detect CUDA devices, check memory, capabilities
        return CUDAManager.getInstance().getAvailableDevices()
            .stream()
            .filter(gpu -> gpu.getMemoryMB() >= MIN_GPU_MEMORY_MB)
            .filter(gpu -> gpu.getComputeCapability() >= MIN_COMPUTE_CAPABILITY)
            .collect(Collectors.toList());
    }
}
```

## Workflow Orchestration Patterns

### Pattern 1: Sequential CPU-GPU Pipeline

```java
@WorkflowImpl
public class DocumentProcessingWorkflowImpl implements DocumentProcessingWorkflow {
    
    private final CPUBoundActivities cpuActivities = 
        Workflow.newActivityStub(CPUBoundActivities.class,
            ActivityOptions.newBuilder()
                .setTaskQueue("cpu-bound-queue")
                .setStartToCloseTimeout(Duration.ofMinutes(30))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3)
                    .build())
                .build());
    
    private final GPUBoundActivities gpuActivities = 
        Workflow.newActivityStub(GPUBoundActivities.class,
            ActivityOptions.newBuilder()
                .setTaskQueue("gpu-bound-queue")
                .setStartToCloseTimeout(Duration.ofHours(2))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(2)
                    .setNonRetryableErrorTypes(OutOfMemoryError.class)
                    .build())
                .build());
    
    @Override
    public ProcessingResult processDocument(DocumentInput input) {
        // CPU-bound preprocessing
        TextProcessingResult textResult = cpuActivities.preprocessText(
            TextInput.from(input)
        );
        
        // GPU-bound inference
        EmbeddingResult embeddings = gpuActivities.generateEmbeddings(
            TextChunks.from(textResult.getProcessedChunks())
        );
        
        // CPU-bound post-processing
        ValidationResult validation = cpuActivities.validateDocument(
            Document.from(textResult, embeddings)
        );
        
        return ProcessingResult.builder()
            .textProcessing(textResult)
            .embeddings(embeddings)
            .validation(validation)
            .build();
    }
}
```

### Pattern 2: Parallel CPU-GPU Processing

```java
@WorkflowImpl
public class ParallelProcessingWorkflowImpl implements ParallelProcessingWorkflow {
    
    @Override
    public ProcessingResult processDocumentParallel(DocumentInput input) {
        // Start CPU and GPU work in parallel
        Promise<TextProcessingResult> cpuWork = Async.function(cpuActivities::preprocessText, 
            TextInput.from(input));
        
        Promise<OCRResult> gpuWork = Async.function(gpuActivities::performOCR,
            ImageData.from(input));
        
        // Wait for both to complete
        TextProcessingResult textResult = cpuWork.get();
        OCRResult ocrResult = gpuWork.get();
        
        // Combine results for final GPU processing
        EmbeddingResult embeddings = gpuActivities.generateEmbeddings(
            TextChunks.combine(textResult.getChunks(), ocrResult.getExtractedText())
        );
        
        return ProcessingResult.builder()
            .textProcessing(textResult)
            .ocr(ocrResult)
            .embeddings(embeddings)
            .build();
    }
}
```

## Performance Optimization Strategies

### CPU-Bound Optimizations

```java
// Optimize for CPU-bound workloads
@Component
public class CPUOptimizationConfig {
    
    // Use work-stealing for better CPU utilization
    @Bean
    public ForkJoinPool cpuWorkStealingPool() {
        return ForkJoinPool.commonPool();
    }
    
    // Configure JVM for CPU-bound tasks
    @PostConstruct
    public void optimizeJVMForCPU() {
        // JVM flags for CPU optimization:
        // -XX:+UseG1GC (low-latency GC)
        // -XX:MaxGCPauseMillis=100
        // -XX:+UseStringDeduplication
        // -XX:+OptimizeStringConcat
    }
}
```

### GPU-Bound Optimizations

```java
// Optimize for GPU-bound workloads
@Component  
public class GPUOptimizationConfig {
    
    // Memory pool for GPU operations
    @Bean
    public MemoryPool gpuMemoryPool() {
        return MemoryPool.builder()
            .withDirectMemory(true) // Use off-heap memory
            .withPreallocation(true) // Pre-allocate buffers
            .withPoolSize(GPU_MEMORY_POOL_SIZE)
            .build();
    }
    
    // Async GPU operation executor
    @Bean
    public ExecutorService gpuAsyncExecutor() {
        return Executors.newFixedThreadPool(
            getAvailableGPUCount(),
            new ThreadFactoryBuilder()
                .setNameFormat("gpu-async-%d")
                .setPriority(Thread.MAX_PRIORITY)
                .build()
        );
    }
}
```

## Monitoring and Observability

### CPU-Bound Metrics

```java
@Component
public class CPUMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    @EventListener
    public void onCPUActivityStart(ActivityStartEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        meterRegistry.counter("cpu.activity.started",
            "activity", event.getActivityType(),
            "worker", event.getWorkerIdentity()
        ).increment();
        
        // Track CPU utilization
        meterRegistry.gauge("cpu.utilization.percent",
            getCurrentCPUUtilization());
    }
    
    @EventListener
    public void onCPUActivityComplete(ActivityCompleteEvent event) {
        meterRegistry.counter("cpu.activity.completed",
            "activity", event.getActivityType(),
            "result", event.getResult().getStatus()
        ).increment();
        
        // Track processing rate
        meterRegistry.gauge("cpu.processing.rate",
            calculateProcessingRate(event));
    }
}
```

### GPU-Bound Metrics

```java
@Component
public class GPUMetricsCollector {
    
    @EventListener
    public void onGPUActivityStart(ActivityStartEvent event) {
        meterRegistry.counter("gpu.activity.started",
            "activity", event.getActivityType(),
            "gpu_device", event.getGPUDeviceId()
        ).increment();
        
        // Track GPU memory usage
        meterRegistry.gauge("gpu.memory.used.mb",
            Tags.of("device", event.getGPUDeviceId()),
            getGPUMemoryUsage(event.getGPUDeviceId()));
        
        // Track GPU temperature
        meterRegistry.gauge("gpu.temperature.celsius",
            Tags.of("device", event.getGPUDeviceId()),
            getGPUTemperature(event.getGPUDeviceId()));
    }
    
    @EventListener
    public void onGPUActivityComplete(ActivityCompleteEvent event) {
        meterRegistry.timer("gpu.activity.duration",
            "activity", event.getActivityType(),
            "gpu_device", event.getGPUDeviceId()
        ).record(event.getDuration());
        
        // Track throughput (items processed per second)
        meterRegistry.gauge("gpu.throughput.items_per_second",
            calculateGPUThroughput(event));
    }
}
```

## Deployment Considerations

### CPU-Optimized Deployment

```yaml
# CPU-optimized Kubernetes deployment
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
    metadata:
      labels:
        app: temporal-cpu-worker
    spec:
      containers:
      - name: cpu-worker
        image: temporal-rag-java:latest
        resources:
          requests:
            cpu: "2000m"
            memory: "4Gi"
          limits:
            cpu: "4000m"
            memory: "8Gi"
        env:
        - name: TEMPORAL_TASK_QUEUE
          value: "cpu-bound-queue"
        - name: JVM_OPTS
          value: "-Xmx6g -XX:+UseG1GC -XX:MaxGCPauseMillis=100"
        nodeSelector:
          workload-type: cpu-optimized
```

### GPU-Optimized Deployment

```yaml
# GPU-optimized Kubernetes deployment
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
    metadata:
      labels:
        app: temporal-gpu-worker
    spec:
      containers:
      - name: gpu-worker
        image: temporal-rag-java-gpu:latest
        resources:
          requests:
            nvidia.com/gpu: 1
            cpu: "1000m"
            memory: "8Gi"
          limits:
            nvidia.com/gpu: 1
            cpu: "2000m"
            memory: "16Gi"
        env:
        - name: TEMPORAL_TASK_QUEUE
          value: "gpu-bound-queue"
        - name: CUDA_VISIBLE_DEVICES
          value: "0"
        - name: JVM_OPTS
          value: "-Xmx12g -XX:+UseG1GC"
        nodeSelector:
          accelerator: nvidia-tesla-v100
        tolerations:
        - key: nvidia.com/gpu
          operator: Exists
          effect: NoSchedule
```

## Best Practices Summary

### CPU-Bound Jobs
✅ **Maximize Core Utilization**: Use parallel processing and work-stealing pools  
✅ **Optimize Memory Access**: Use memory-efficient data structures  
✅ **Monitor CPU Utilization**: Track per-core usage and thread contention  
✅ **Scale Horizontally**: Add more CPU cores/nodes for increased throughput  

### GPU-Bound Jobs  
✅ **Batch Processing**: Optimize batch sizes for GPU memory and throughput  
✅ **Memory Management**: Pre-allocate GPU memory and use memory pools  
✅ **Resource Isolation**: Use dedicated GPU workers and resource scheduling  
✅ **Monitor GPU Health**: Track temperature, memory usage, and utilization  

### General Principles
✅ **Separate Task Queues**: Use different queues for CPU and GPU workloads  
✅ **Resource-Aware Scaling**: Scale based on resource utilization, not just throughput  
✅ **Failure Handling**: Different retry strategies for resource-constrained failures  
✅ **Cost Optimization**: Balance performance requirements with infrastructure costs  

This comprehensive guide enables teams to build efficient, resource-aware Temporal workflows that properly utilize both CPU and GPU resources for optimal performance and cost-effectiveness.