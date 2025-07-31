# 리소스 최적화 Temporal 실행 가이드

## 개요

이 가이드는 최적의 리소스 활용을 위해 CPU 집약적, GPU 집약적, IO 집약적 액티비티를 서로 다른 태스크 큐로 분리하는 리소스 최적화 Temporal 워크플로우를 실행하는 방법을 설명합니다.

## 아키텍처

### 태스크 큐 분리

리소스 최적화 구현은 네 개의 별도 태스크 큐를 사용합니다:

1. **`document-pipeline-queue`** - 워크플로우 오케스트레이션만
2. **`cpu-bound-queue`** - CPU 집약적 액티비티 (텍스트 처리, 검증, 압축)
3. **`gpu-bound-queue`** - GPU 가속 액티비티 (ML 추론, 임베딩, OCR)
4. **`io-bound-queue`** - IO 집약적 액티비티 (다운로드, 업로드, 데이터베이스 쿼리)

### 워커 유형

#### 1. 워크플로우 워커
- **목적**: 액티비티를 실행하지 않고 워크플로우 오케스트레이션
- **리소스 프로필**: 낮은 메모리, 높은 동시성
- **구성**: 
  - 최대 동시 워크플로우: 1000
  - 액티비티 실행 없음
  - 최소 리소스 요구사항

#### 2. CPU 워커
- **목적**: CPU 집약적 텍스트 처리 작업 실행
- **리소스 프로필**: 높은 CPU, 보통 메모리
- **구성**:
  - 최대 동시 액티비티: CPU 코어 × 2
  - 병렬 처리를 위한 ForkJoinPool 사용
  - 컴퓨팅 집약적 작업에 최적화

#### 3. GPU 워커
- **목적**: ML 추론 및 GPU 가속 작업 실행
- **리소스 프로필**: GPU 메모리, CUDA 코어
- **구성**:
  - 최대 동시 액티비티: GPU 수
  - 효율성을 위한 배치 처리
  - GPU 리소스 관리

#### 4. IO 워커
- **목적**: 네트워크 및 데이터베이스 작업 처리
- **리소스 프로필**: 높은 동시성, 낮은 CPU
- **구성**:
  - 최대 동시 액티비티: 200
  - 비동기 IO 작업
  - 연결 풀링

## 리소스 최적화 파이프라인 실행

### 1단계: Docker 서비스 시작

```bash
cd /Users/musinsa/ws/temporal-example
docker-compose up -d
```

### 2단계: Java 프로젝트 빌드

```bash
cd java
./gradlew clean build
```

### 3단계: 워커 시작 (별도 터미널에서)

#### 터미널 1: 워크플로우 워커
```bash
./gradlew runWorkflowWorker
```

예상 출력:
```
[WORKFLOW-WORKER] - Workflow Worker started on task queue: document-pipeline-queue
[WORKFLOW-WORKER] - This worker handles workflow orchestration only
[WORKFLOW-WORKER] - Activities are executed on specialized workers:
[WORKFLOW-WORKER] -   - CPU-bound activities: cpu-bound-queue
[WORKFLOW-WORKER] -   - GPU-bound activities: gpu-bound-queue
[WORKFLOW-WORKER] -   - IO-bound activities: io-bound-queue
```

#### 터미널 2: CPU 워커
```bash
./gradlew runCPUWorker
```

예상 출력:
```
[CPU-WORKER] - CPU Worker started on task queue: cpu-bound-queue with 8 cores
[CPU-WORKER] - Optimized for CPU-intensive operations
```

#### 터미널 3: GPU 워커
```bash
# 기본값과 다른 경우 GPU 수 설정
export GPU_COUNT=2
./gradlew runGPUWorker
```

예상 출력:
```
[GPU-WORKER] - GPU Worker started on task queue: gpu-bound-queue with 2 GPUs
[GPU-WORKER] - Optimized for GPU-accelerated ML inference and image processing
```

#### 터미널 4: IO 워커
```bash
./gradlew runIOWorker
```

예상 출력:
```
[IO-WORKER] - IO Worker started on task queue: io-bound-queue
[IO-WORKER] - Optimized for network IO, database queries, and external API calls
[IO-WORKER] - Configured for high concurrency with async IO operations
```

### 4단계: 최적화된 워크플로우 실행

새 터미널에서:
```bash
./gradlew runOptimizedStarter
```

또는 사용자 정의 매개변수로:
```bash
java -cp build/libs/temporal-rag-pipeline-spring-1.0.0.jar \
  com.example.ResourceOptimizedStarter \
  --csv-file ../testdata/documents.csv \
  --batch-size 10 \
  --timeout 120
```

## 액티비티 분배

### CPU 집약적 액티비티
- **텍스트 전처리**: 정규화, 토큰화, 언어 감지
- **문서 검증**: 구조 검증, 형식 확인
- **텍스트 추출**: 문서 파싱, 내용 추출
- **압축**: 문서의 GZIP 압축
- **단어 빈도 분석**: 통계적 텍스트 분석

### GPU 집약적 액티비티
- **임베딩 생성**: 트랜스포머 모델을 사용한 벡터 임베딩
- **문서 분류**: ML 기반 분류
- **OCR 처리**: 광학 문자 인식
- **이미지 분석**: 객체 감지, 장면 이해
- **LLM 추론**: 대규모 언어 모델 추론

### IO 집약적 액티비티
- **문서 다운로드**: HTTP/HTTPS 파일 다운로드
- **S3 업로드**: MinIO/S3 객체 저장소
- **데이터베이스 쿼리**: PostgreSQL 메타데이터 쿼리
- **벡터 저장**: ChromaDB 임베딩 저장
- **외부 API 호출**: REST API 통합

## 성능 최적화

### CPU 워커 최적화
```java
// 모든 코어를 사용한 병렬 처리
ForkJoinPool customThreadPool = new ForkJoinPool(processors);
customThreadPool.submit(() -> 
    input.getChunks().parallelStream()
        .map(this::processChunk)
        .collect(Collectors.toList())
).get();
```

### GPU 워커 최적화
```java
// GPU 효율성을 위한 배치 처리
int batchSize = calculateOptimalBatchSize(chunks.size());
for (int i = 0; i < chunks.size(); i += batchSize) {
    List<TextChunk> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
    float[][] batchEmbeddings = processBatchOnGPU(batch, gpuDevice);
}
```

### IO 워커 최적화
```java
// 비동기 IO 작업
CompletableFuture<byte[]> downloadFuture = CompletableFuture.supplyAsync(() -> {
    // 논블로킹 다운로드
}, ioExecutor);
```

## 모니터링 및 확장

### 워커 메트릭

각 워커 유형은 특정 메트릭을 노출합니다:

#### CPU 워커 메트릭
- CPU 사용률
- 처리율 (문서/초)
- 스레드 풀 통계
- 메모리 사용량

#### GPU 워커 메트릭
- GPU 메모리 사용량
- GPU 온도
- 추론 처리량
- 배치 처리 효율성

#### IO 워커 메트릭
- 활성 연결
- 요청 지연 시간
- 처리량 (MB/초)
- 연결 풀 통계

### 확장 전략

#### 수평 확장
```bash
# 부하에 따라 CPU 워커 확장
for i in {1..4}; do
  ./gradlew runCPUWorker &
done

# 높은 처리량을 위한 IO 워커 확장
for i in {1..2}; do
  ./gradlew runIOWorker &
done
```

#### 수직 확장
```bash
# GPU 워커 메모리 증가
./gradlew runGPUWorker -Dorg.gradle.jvmargs="-Xmx8g"

# CPU 워커 스레드 증가
export WORKER_CPU_THREADS=16
./gradlew runCPUWorker
```

## Kubernetes 배포

### CPU 워커 배포
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

### GPU 워커 배포
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

## 문제 해결

### 일반적인 문제

1. **워커가 액티비티를 선택하지 않음**
   - 태스크 큐 이름이 정확히 일치하는지 확인
   - 워커가 올바른 액티비티에 등록되었는지 확인
   - Temporal 서버에 접근 가능한지 확인

2. **GPU 메모리 부족**
   - GPU 액티비티에서 배치 크기 줄이기
   - GPU 메모리 예약 설정 확인
   - 동시 GPU 액티비티 모니터링

3. **IO 워커 연결 고갈**
   - 연결 풀 크기 증가
   - 연결 누수 확인
   - 외부 서비스 가용성 모니터링

4. **CPU 워커 성능 저하**
   - 과도한 스레드 경합 확인
   - GC 활동 모니터링
   - CPU 친화성 설정 확인

### 디버그 명령

```bash
# 워커 상태 확인
temporal task-queue describe --task-queue cpu-bound-queue

# 워크플로우 실행 모니터링
temporal workflow show --workflow-id resource-optimized-pipeline-xxx

# 액티비티 실패 확인
temporal workflow list --query 'ExecutionStatus="Failed"'
```

## 모범 사례

1. **리소스 할당**
   - GPU 워커를 위한 전용 노드
   - CPU 워커를 위한 CPU 친화성 사용
   - 컴퓨팅 집약적 워크로드에서 IO 워커 격리

2. **큐 관리**
   - 큐 깊이 모니터링
   - 적절한 액티비티 타임아웃 설정
   - 실패한 액티비티를 위한 데드 레터 큐 사용

3. **오류 처리**
   - 액티비티별 재시도 정책 구현
   - 중요한 작업에 대한 보상 패턴 사용
   - 반복 실패에 대한 모니터링 및 경고

4. **성능 튜닝**
   - 병목 현상을 식별하기 위한 액티비티 프로파일링
   - GPU 작업을 위한 배치 크기 최적화
   - IO 작업을 위한 연결 풀링 사용
   - 적절한 곳에 캐싱 구현

이 리소스 최적화 접근 방식은 서로 다른 하드웨어 리소스의 효율적인 활용, 향상된 확장성 및 문서 처리 파이프라인의 더 나은 성능을 가능하게 합니다.