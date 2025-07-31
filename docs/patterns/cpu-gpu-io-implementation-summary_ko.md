# CPU-GPU-IO 리소스 최적화 구현 요약

## 개요

이 문서는 최적의 리소스 활용을 위해 CPU 바운드, GPU 바운드, IO 바운드 액티비티를 서로 다른 작업 큐로 분리하는 리소스 최적화 Temporal 워크플로우의 구현을 요약합니다.

## 구현 세부사항

### 1. 액티비티 인터페이스

#### CPU 바운드 액티비티 (`CPUBoundActivities.java`)
```java
@ActivityInterface
public interface CPUBoundActivities {
    TextProcessingResult preprocessText(PreprocessingInput input);
    ValidationResult validateDocumentStructure(Document document);
    TextExtractionResult extractTextFromDocument(Document document);
    TokenizationResult tokenizeText(String text, String documentId);
    CompressionResult compressDocument(Document document);
}
```

#### GPU 바운드 액티비티 (`GPUBoundActivities.java`)
```java
@ActivityInterface
public interface GPUBoundActivities {
    EmbeddingResult generateEmbeddings(List<TextChunk> chunks);
    ClassificationResult classifyDocument(Document document);
    OCRResult performOCR(Document document);
    ImageAnalysisResult analyzeImages(List<String> imageUrls);
    InferenceResult runLLMInference(InferenceInput input);
}
```

#### IO 바운드 액티비티 (`IOBoundActivities.java`)
```java
@ActivityInterface
public interface IOBoundActivities {
    DownloadResult downloadDocument(String url);
    UploadResult uploadToS3(Document document, byte[] content);
    DatabaseResult queryMetadataDatabase(String documentId);
    ChromaDBResult storeVectorEmbeddings(List<VectorEmbedding> embeddings);
    ExternalAPIResult callExternalAPI(String endpoint, String payload);
}
```

### 2. 워커 구성

#### 워크플로우 워커
- **작업 큐**: `document-pipeline-queue`
- **목적**: 액티비티를 실행하지 않고 워크플로우 오케스트레이션
- **구성**: 높은 워크플로우 동시성 (1000), 액티비티 실행 없음

#### CPU 워커
- **작업 큐**: `cpu-bound-queue`
- **목적**: CPU 집약적인 텍스트 처리 작업 실행
- **구성**: 최대 동시 액티비티 = CPU 코어 × 2
- **최적화**: 병렬 처리를 위한 ForkJoinPool 사용

#### GPU 워커
- **작업 큐**: `gpu-bound-queue`
- **목적**: ML 추론 및 GPU 가속 작업 실행
- **구성**: 최대 동시 액티비티 = GPU 수
- **리소스 관리**: 배치 처리를 통한 GPU 획득/해제

#### IO 워커
- **작업 큐**: `io-bound-queue`
- **목적**: 네트워크 및 데이터베이스 작업 처리
- **구성**: 높은 동시성 (200), 비동기 IO 작업
- **최적화**: 논블로킹 IO를 위한 CompletableFuture

### 3. 리소스 최적화 워크플로우

`ResourceOptimizedWorkflowImpl`은 다양한 작업 큐에 걸쳐 액티비티를 오케스트레이션합니다:

```java
// CPU 바운드 액티비티
private final CPUBoundActivities cpuActivities = 
    Workflow.newActivityStub(CPUBoundActivities.class,
        ActivityOptions.newBuilder()
            .setTaskQueue("cpu-bound-queue")
            .build());

// GPU 바운드 액티비티
private final GPUBoundActivities gpuActivities = 
    Workflow.newActivityStub(GPUBoundActivities.class,
        ActivityOptions.newBuilder()
            .setTaskQueue("gpu-bound-queue")
            .build());

// IO 바운드 액티비티
private final IOBoundActivities ioActivities = 
    Workflow.newActivityStub(IOBoundActivities.class,
        ActivityOptions.newBuilder()
            .setTaskQueue("io-bound-queue")
            .build());
```

### 4. 병렬 처리 예제

워크플로우는 병렬 실행을 위해 Temporal의 Promise API를 활용합니다:

```java
// IO와 CPU 작업을 병렬로 시작
Promise<DownloadResult> downloadPromise = 
    Async.function(ioActivities::downloadDocument, doc.getUrl());

Promise<ValidationResult> validationPromise = 
    Async.function(cpuActivities::validateDocumentStructure, doc);

// 결과 대기
DownloadResult downloadResult = downloadPromise.get();
ValidationResult validation = validationPromise.get();
```

### 5. 생성된 주요 모델

- `TextProcessingResult`, `ValidationResult`, `TextExtractionResult`, `TokenizationResult`, `CompressionResult`
- `EmbeddingResult`, `ClassificationResult`, `OCRResult`, `ImageAnalysisResult`, `InferenceResult`
- `DownloadResult`, `UploadResult`, `DatabaseResult`, `ChromaDBResult`, `ExternalAPIResult`
- `PreprocessingInput`, `InferenceInput`, `VectorEmbedding`, `DocumentResult`

### 6. 구성 파일

#### Spring 프로파일
- `application-cpu-worker.yml`: CPU 워커 전용 구성
- `application-gpu-worker.yml`: GPU 설정을 포함한 GPU 워커 구성
- `application-io-worker.yml`: 연결 풀 설정을 포함한 IO 워커
- `application-workflow-worker.yml`: 워크플로우 워커 구성

#### Gradle 작업
```bash
./gradlew runWorkflowWorker   # 워크플로우 오케스트레이터 시작
./gradlew runCPUWorker        # CPU 바운드 액티비티 워커 시작
./gradlew runGPUWorker        # GPU 바운드 액티비티 워커 시작
./gradlew runIOWorker         # IO 바운드 액티비티 워커 시작
./gradlew runOptimizedStarter # 최적화된 워크플로우 실행
```

## 이점

1. **리소스 활용**: 각 워커 유형이 특정 워크로드에 최적화됨
2. **확장성**: 수요에 따라 워커를 독립적으로 확장 가능
3. **성능**: 병렬 실행 및 리소스별 최적화
4. **유연성**: 새로운 액티비티 유형 추가 또는 워커 구성 수정이 용이
5. **모니터링**: 더 나은 관찰성을 위한 리소스별 메트릭

## 시스템 실행

1. 모든 워커 시작 (별도의 터미널에서):
   ```bash
   ./gradlew runWorkflowWorker
   ./gradlew runCPUWorker
   ./gradlew runGPUWorker
   ./gradlew runIOWorker
   ```

2. 워크플로우 실행:
   ```bash
   ./gradlew runOptimizedStarter
   ```

이 구현은 Temporal의 작업 큐 분리가 문서 처리 파이프라인에서 효율적인 리소스 활용을 가능하게 하는 방법을 보여줍니다.