# Temporal 워크플로우에서 GPU 바운드 vs CPU 바운드 작업 패턴

## 요약

Temporal 워크플로우에서 최적의 리소스 활용을 위해서는 GPU 바운드와 CPU 바운드 작업의 차이를 이해하는 것이 중요합니다. 이 문서는 두 가지 유형의 워크로드를 효율적으로 처리하기 위한 아키텍처 패턴, 구현 예제, 모범 사례를 제공합니다.

## 리소스 특성

### CPU 바운드 작업
- **특성**: 높은 CPU 사용률, 최소 메모리 요구사항, 계산 집약적
- **예시**: 텍스트 처리, 데이터 검증, 수학적 계산, 파싱
- **병목**: CPU 코어와 클럭 속도
- **확장성**: CPU 코어 전반에 걸친 수평 확장

### GPU 바운드 작업
- **특성**: 병렬 처리, 높은 메모리 대역폭, 특수 연산 장치
- **예시**: ML 추론, 이미지 처리, 벡터 임베딩, 신경망 학습
- **병목**: GPU 메모리, CUDA 코어, 텐서 유닛
- **확장성**: 더 나은 GPU로 수직 확장, 여러 GPU로 수평 확장

## 아키텍처 패턴

### 패턴 1: 별도의 작업 큐

```java
// CPU 바운드 액티비티는 CPU 최적화 워커 사용
@ActivityInterface
public interface CPUBoundActivities {
    @ActivityMethod
    TextProcessingResult preprocessText(TextInput input);
    
    @ActivityMethod
    ValidationResult validateDocument(Document doc);
    
    @ActivityMethod
    ParsingResult parseStructuredData(RawData data);
}

// GPU 바운드 액티비티는 GPU 최적화 워커 사용
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

### 패턴 2: 워커 구성

```java
// CPU 최적화 워커 구성
@Component
public class CPUWorkerConfig {
    
    @Bean("cpuWorkerFactory")
    public WorkerFactory createCPUWorkerFactory() {
        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
        
        Worker cpuWorker = factory.newWorker("cpu-bound-queue");
        
        // CPU 집약적 작업을 위한 구성
        cpuWorker.registerWorkflowImplementationTypes(DocumentProcessingWorkflow.class);
        cpuWorker.registerActivitiesImplementations(
            new TextProcessingActivitiesImpl(),
            new ValidationActivitiesImpl()
        );
        
        return factory;
    }
}

// GPU 최적화 워커 구성
@Component  
public class GPUWorkerConfig {
    
    @Bean("gpuWorkerFactory")
    public WorkerFactory createGPUWorkerFactory() {
        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
        
        Worker gpuWorker = factory.newWorker("gpu-bound-queue");
        
        // GPU 집약적 작업을 위한 구성
        gpuWorker.registerActivitiesImplementations(
            new MLInferenceActivitiesImpl(),
            new ImageProcessingActivitiesImpl()
        );
        
        return factory;
    }
}
```

## 구현 예제

### CPU 바운드 액티비티: 텍스트 처리

```java
@Component
public class TextProcessingActivitiesImpl implements CPUBoundActivities {
    
    @Override
    @ActivityMethod
    public TextProcessingResult preprocessText(TextInput input) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        
        // CPU 집약적 텍스트 처리
        return ActivityRetryOptions.newBuilder()
            .setInitialInterval(Duration.ofSeconds(1))
            .setMaximumInterval(Duration.ofMinutes(1))
            .setBackoffCoefficient(2.0)
            .setMaximumAttempts(3)
            .build()
            .retry(() -> {
                context.heartbeat("텍스트 처리 중: " + input.getDocumentId());
                
                // 모든 가용 코어를 사용한 병렬 CPU 처리
                return processTextParallel(input);
            });
    }
    
    private TextProcessingResult processTextParallel(TextInput input) {
        // 텍스트 처리를 위해 모든 CPU 코어 활용
        int processors = Runtime.getRuntime().availableProcessors();
        ForkJoinPool customThreadPool = new ForkJoinPool(processors);
        
        try {
            return customThreadPool.submit(() -> 
                input.getChunks().parallelStream()
                    .map(this::processChunk)
                    .collect(Collectors.toList())
            ).get();
        } catch (Exception e) {
            throw new ActivityFailure("텍스트 처리 실패", e);
        } finally {
            customThreadPool.shutdown();
        }
    }
    
    private ProcessedChunk processChunk(TextChunk chunk) {
        // CPU 집약적 작업:
        // - 텍스트 정규화
        // - 언어 감지
        // - 엔티티 추출
        // - 감정 분석
        return ProcessedChunk.builder()
            .content(normalizeText(chunk.getContent()))
            .language(detectLanguage(chunk.getContent()))
            .entities(extractEntities(chunk.getContent()))
            .sentiment(analyzeSentiment(chunk.getContent()))
            .build();
    }
}
```

### GPU 바운드 액티비티: ML 추론

```java
@Component
public class MLInferenceActivitiesImpl implements GPUBoundActivities {
    
    private final GPUModelManager modelManager;
    private final GPUResourcePool gpuPool;
    
    @Override
    @ActivityMethod  
    public EmbeddingResult generateEmbeddings(TextChunks chunks) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        
        // 배치 처리를 통한 GPU 리소스 관리
        return gpuPool.withGPUResource(gpu -> {
            context.heartbeat("GPU에서 임베딩 생성 중: " + gpu.getDeviceId());
            
            // GPU 효율성을 위한 배치 처리
            List<float[]> embeddings = new ArrayList<>();
            
            // GPU 메모리에 최적화된 배치 크기로 처리
            int batchSize = calculateOptimalBatchSize(chunks.size(), gpu.getMemoryMB());
            
            for (int i = 0; i < chunks.size(); i += batchSize) {
                List<TextChunk> batch = chunks.getChunks()
                    .subList(i, Math.min(i + batchSize, chunks.size()));
                
                // GPU 가속 임베딩 생성
                float[][] batchEmbeddings = generateEmbeddingsBatch(batch, gpu);
                embeddings.addAll(Arrays.asList(batchEmbeddings));
                
                // 장시간 실행되는 GPU 작업의 진행 상황 보고
                context.heartbeat(String.format("%d/%d 청크 처리 완료", 
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
        // GPU 특화 구현:
        // - CUDA 커널 실행
        // - 텐서 연산
        // - 메모리 전송 최적화
        // - 모델 추론 배치 처리
        
        try (GPUContext context = gpu.createContext()) {
            // 모델이 로드되지 않은 경우 GPU 메모리에 로드
            if (!context.isModelLoaded(modelManager.getCurrentModel())) {
                context.loadModel(modelManager.getCurrentModel());
            }
            
            // 입력 텐서 준비
            float[][] inputTensors = prepareInputTensors(batch);
            
            // GPU에서 추론 실행
            float[][] outputTensors = context.inferBatch(inputTensors);
            
            return outputTensors;
        }
    }
    
    private int calculateOptimalBatchSize(int totalItems, int gpuMemoryMB) {
        // 다음을 기반으로 배치 크기 계산:
        // - 사용 가능한 GPU 메모리
        // - 모델 메모리 요구사항
        // - 입력/출력 텐서 크기
        int modelMemoryMB = modelManager.getCurrentModel().getMemoryRequirementMB();
        int availableMemoryMB = gpuMemoryMB - modelMemoryMB;
        
        // 항목당 메모리 추정 (입력 + 출력 텐서)
        int memoryPerItemKB = estimateMemoryPerItem();
        
        return Math.max(1, (availableMemoryMB * 1024) / memoryPerItemKB);
    }
}
```

## 리소스 관리 전략

### CPU 바운드 리소스 관리

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

### GPU 바운드 리소스 관리

```java
@Configuration
public class GPUResourceConfig {
    
    @Bean
    @ConfigurationProperties("temporal.gpu-worker")
    public GPUWorkerOptions gpuWorkerOptions() {
        return GPUWorkerOptions.builder()
            // 동시 GPU 액티비티를 사용 가능한 GPU 수로 제한
            .maxConcurrentActivityExecutions(getAvailableGPUCount())
            .maxConcurrentWorkflowTaskExecutions(50)
            // GPU 액티비티는 로컬에서 실행되지 않아야 함
            .maxConcurrentLocalActivityExecutions(0)
            .build();
    }
    
    @Bean
    public GPUResourcePool gpuResourcePool() {
        return GPUResourcePool.builder()
            .withGPUDevices(detectAvailableGPUs())
            .withResourceTimeout(Duration.ofMinutes(30))
            .withHealthCheckInterval(Duration.ofSeconds(30))
            .withMemoryReservation(0.8f) // GPU 메모리의 80% 예약
            .build();
    }
    
    private List<GPUDevice> detectAvailableGPUs() {
        // CUDA 디바이스 감지, 메모리 및 기능 확인
        return CUDAManager.getInstance().getAvailableDevices()
            .stream()
            .filter(gpu -> gpu.getMemoryMB() >= MIN_GPU_MEMORY_MB)
            .filter(gpu -> gpu.getComputeCapability() >= MIN_COMPUTE_CAPABILITY)
            .collect(Collectors.toList());
    }
}
```

## 워크플로우 오케스트레이션 패턴

### 패턴 1: 순차적 CPU-GPU 파이프라인

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
        // CPU 바운드 전처리
        TextProcessingResult textResult = cpuActivities.preprocessText(
            TextInput.from(input)
        );
        
        // GPU 바운드 추론
        EmbeddingResult embeddings = gpuActivities.generateEmbeddings(
            TextChunks.from(textResult.getProcessedChunks())
        );
        
        // CPU 바운드 후처리
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

### 패턴 2: 병렬 CPU-GPU 처리

```java
@WorkflowImpl
public class ParallelProcessingWorkflowImpl implements ParallelProcessingWorkflow {
    
    @Override
    public ProcessingResult processDocumentParallel(DocumentInput input) {
        // CPU와 GPU 작업을 병렬로 시작
        Promise<TextProcessingResult> cpuWork = Async.function(cpuActivities::preprocessText, 
            TextInput.from(input));
        
        Promise<OCRResult> gpuWork = Async.function(gpuActivities::performOCR,
            ImageData.from(input));
        
        // 둘 다 완료 대기
        TextProcessingResult textResult = cpuWork.get();
        OCRResult ocrResult = gpuWork.get();
        
        // 최종 GPU 처리를 위해 결과 결합
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

## 성능 최적화 전략

### CPU 바운드 최적화

```java
// CPU 바운드 워크로드 최적화
@Component
public class CPUOptimizationConfig {
    
    // 더 나은 CPU 활용을 위한 work-stealing 사용
    @Bean
    public ForkJoinPool cpuWorkStealingPool() {
        return ForkJoinPool.commonPool();
    }
    
    // CPU 바운드 작업을 위한 JVM 구성
    @PostConstruct
    public void optimizeJVMForCPU() {
        // CPU 최적화를 위한 JVM 플래그:
        // -XX:+UseG1GC (저지연 GC)
        // -XX:MaxGCPauseMillis=100
        // -XX:+UseStringDeduplication
        // -XX:+OptimizeStringConcat
    }
}
```

### GPU 바운드 최적화

```java
// GPU 바운드 워크로드 최적화
@Component  
public class GPUOptimizationConfig {
    
    // GPU 작업을 위한 메모리 풀
    @Bean
    public MemoryPool gpuMemoryPool() {
        return MemoryPool.builder()
            .withDirectMemory(true) // 오프힙 메모리 사용
            .withPreallocation(true) // 버퍼 사전 할당
            .withPoolSize(GPU_MEMORY_POOL_SIZE)
            .build();
    }
    
    // 비동기 GPU 작업 실행자
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

## 모니터링 및 관찰성

### CPU 바운드 메트릭

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
        
        // CPU 사용률 추적
        meterRegistry.gauge("cpu.utilization.percent",
            getCurrentCPUUtilization());
    }
    
    @EventListener
    public void onCPUActivityComplete(ActivityCompleteEvent event) {
        meterRegistry.counter("cpu.activity.completed",
            "activity", event.getActivityType(),
            "result", event.getResult().getStatus()
        ).increment();
        
        // 처리 속도 추적
        meterRegistry.gauge("cpu.processing.rate",
            calculateProcessingRate(event));
    }
}
```

### GPU 바운드 메트릭

```java
@Component
public class GPUMetricsCollector {
    
    @EventListener
    public void onGPUActivityStart(ActivityStartEvent event) {
        meterRegistry.counter("gpu.activity.started",
            "activity", event.getActivityType(),
            "gpu_device", event.getGPUDeviceId()
        ).increment();
        
        // GPU 메모리 사용량 추적
        meterRegistry.gauge("gpu.memory.used.mb",
            Tags.of("device", event.getGPUDeviceId()),
            getGPUMemoryUsage(event.getGPUDeviceId()));
        
        // GPU 온도 추적
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
        
        // 처리량 추적 (초당 처리 항목)
        meterRegistry.gauge("gpu.throughput.items_per_second",
            calculateGPUThroughput(event));
    }
}
```

## 배포 고려사항

### CPU 최적화 배포

```yaml
# CPU 최적화 Kubernetes 배포
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

### GPU 최적화 배포

```yaml
# GPU 최적화 Kubernetes 배포
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

## 모범 사례 요약

### CPU 바운드 작업
✅ **코어 활용 최대화**: 병렬 처리와 work-stealing 풀 사용
✅ **메모리 접근 최적화**: 메모리 효율적인 데이터 구조 사용
✅ **CPU 사용률 모니터링**: 코어별 사용량과 스레드 경합 추적
✅ **수평 확장**: 처리량 증가를 위해 더 많은 CPU 코어/노드 추가

### GPU 바운드 작업
✅ **배치 처리**: GPU 메모리와 처리량을 위한 배치 크기 최적화
✅ **메모리 관리**: GPU 메모리 사전 할당 및 메모리 풀 사용
✅ **리소스 격리**: 전용 GPU 워커와 리소스 스케줄링 사용
✅ **GPU 상태 모니터링**: 온도, 메모리 사용량, 활용도 추적

### 일반 원칙
✅ **작업 큐 분리**: CPU와 GPU 워크로드에 다른 큐 사용
✅ **리소스 인식 확장**: 처리량뿐만 아니라 리소스 활용도에 기반한 확장
✅ **장애 처리**: 리소스 제약 장애에 대한 다른 재시도 전략
✅ **비용 최적화**: 성능 요구사항과 인프라 비용의 균형

이 종합 가이드를 통해 팀은 최적의 성능과 비용 효율성을 위해 CPU와 GPU 리소스를 모두 적절히 활용하는 효율적이고 리소스 인식 Temporal 워크플로우를 구축할 수 있습니다.