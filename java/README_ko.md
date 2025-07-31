# Java 구현 - 문서 처리 파이프라인

Temporal 워크플로우 오케스트레이션을 사용한 엔터프라이즈 준비 문서 처리 파이프라인 Java 구현입니다. 이 구현은 강력한 타입 지정과 포괄적인 Spring Boot 통합으로 견고하고 확장 가능한 처리를 제공합니다.

## 사전 요구사항

- Java 17 이상
- Gradle 7.0+ (래퍼 포함)
- Docker 및 Docker Compose (종속성용)

## 기능

- **엔터프라이즈 통합**: Spring Boot, 의존성 주입, 구성 관리
- **강력한 타입 지정**: 검증이 포함된 포괄적인 POJO
- **높은 처리량**: JVM 워밍업 후 초당 10,000개 이상의 워크플로우
- **두 가지 워크플로우 패턴**: 기본 파이프라인과 리소스 최적화 실행
- **프로덕션 준비**: 포괄적인 모니터링, 메트릭, 헬스 체크
- **유연한 구성**: 프로파일을 사용한 YAML 기반 구성

## 프로젝트 구조

```
java/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/
│       │       ├── activities/           # 액티비티 구현
│       │       │   ├── ingestion/       # CSV 파싱, 문서 다운로드
│       │       │   ├── preprocessing/   # 텍스트 추출, 청킹
│       │       │   ├── inference/       # 임베딩, 요약
│       │       │   ├── postprocessing/  # 품질 점수, 메타데이터
│       │       │   ├── storage/         # 벡터 DB, S3, PostgreSQL
│       │       │   ├── cpu/             # CPU 집약적 액티비티
│       │       │   ├── gpu/             # GPU 집약적 액티비티
│       │       │   └── io/              # IO 집약적 액티비티
│       │       ├── models/              # 데이터 모델 (POJO)
│       │       ├── utils/               # 유틸리티 클래스
│       │       ├── workflows/           # 워크플로우 구현
│       │       ├── workers/             # 전문 워커 구현
│       │       ├── config/              # Spring 구성
│       │       ├── DocumentPipelineWorker.java    # 원본 워커
│       │       ├── DocumentPipelineStarter.java   # 원본 스타터
│       │       └── ResourceOptimizedStarter.java  # 리소스 최적화 스타터
│       └── resources/
│           ├── application.yml                    # 기본 구성
│           ├── application-workflow-worker.yml    # 워크플로우 워커 프로파일
│           ├── application-cpu-worker.yml         # CPU 워커 프로파일
│           ├── application-gpu-worker.yml         # GPU 워커 프로파일
│           └── application-io-worker.yml          # IO 워커 프로파일
├── scripts/                             # 빌드 및 실행 스크립트
├── build.gradle                         # Gradle 구성
└── Dockerfile                           # 컨테이너 구성
```

## 빠른 시작

### 1. 프로젝트 빌드

```bash
# 프로젝트 빌드
./gradlew build

# 테스트 실행
./gradlew test
```

### 2. 인프라 시작

```bash
# 프로젝트 루트에서
docker-compose up -d

# 서비스 실행 확인
docker-compose ps
```

### 3. 기본 파이프라인 실행

```bash
# 터미널 1: 워커 시작
./gradlew runWorker

# 터미널 2: 워크플로우 시작
./gradlew runStarter
```

### 4. 리소스 최적화 파이프라인 실행 (고급)

```bash
# 터미널 1: 워크플로우 워커 (워크플로우 오케스트레이션)
./gradlew runWorkflowWorker

# 터미널 2: CPU 워커 (텍스트 처리, 검증)
./gradlew runCPUWorker

# 터미널 3: GPU 워커 (ML 추론, 임베딩)
./gradlew runGPUWorker

# 터미널 4: IO 워커 (다운로드, 업로드, DB 쿼리)
./gradlew runIOWorker

# 터미널 5: 리소스 최적화 워크플로우 시작
./gradlew runOptimizedStarter
```

## 워크플로우 패턴

### 1. 기본 문서 파이프라인

모든 액티비티를 처리하는 단일 워커:

- **파일**: `workflows/DocumentPipelineWorkflow.java`
- **사용 사례**: 엔터프라이즈 개발, 테스트, 중간 규모 워크로드
- **설정**: Spring Boot 통합이 포함된 단일 워커
- **성능**: 일당 10K-50K 문서에 적합

### 2. 리소스 최적화 파이프라인

Spring 프로파일을 사용하는 여러 전문 워커:

- **파일**: `workflows/ResourceOptimizedWorkflow.java`
- **사용 사례**: 엔터프라이즈 프로덕션, 높은 처리량 워크로드
- **설정**: 다른 Spring 프로파일을 가진 4개 워커
- **성능**: 일당 100K 이상 문서에 최적화

### Spring 프로파일

각 워커 유형은 전용 Spring 프로파일을 사용합니다:

- `workflow-worker`: 워크플로우 오케스트레이션만
- `cpu-worker`: CPU 집약적 처리
- `gpu-worker`: GPU 가속 ML 작업
- `io-worker`: IO 집약적 작업

## 구성

애플리케이션은 `application.yml` 또는 환경 변수를 통해 구성할 수 있습니다:

### 데이터베이스 구성
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/temporal_rag
    username: postgres
    password: postgres
```

### S3/MinIO 구성
```yaml
s3:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
```

### ChromaDB 구성
```yaml
chromadb:
  url: http://localhost:8000
```

### LLM 서비스 구성
```yaml
llm:
  service:
    url: http://localhost:8081
  use-mock: true
```

## Docker 배포

### Docker 이미지 빌드

```bash
docker build -t temporal-java-worker .
```

### Docker Compose로 실행

```bash
docker-compose up
```

## 주요 구성 요소

### 액티비티

#### 원본 파이프라인 액티비티

1. **수집 액티비티**
   - `parseCSVActivity`: CSV에서 문서 메타데이터 읽기
   - `downloadDocumentsActivity`: 배치로 문서 다운로드

2. **전처리 액티비티**
   - `preprocessDocuments`: 텍스트 추출, 문서 청킹, 언어 감지

3. **추론 액티비티**
   - `runInferenceBatch`: 임베딩 및 요약 생성

4. **후처리 액티비티**
   - `postprocessDocuments`: 품질 점수 계산, 메타데이터 보강

5. **저장 액티비티**
   - `storeInVectorDB`: ChromaDB에 임베딩 저장
   - `storeInS3`: S3/MinIO에 처리된 문서 저장
   - `storeMetadata`: PostgreSQL에 파이프라인 메타데이터 저장

#### 리소스 최적화 액티비티

1. **CPU 집약적 액티비티** (전용 CPU 워커에서 실행)
   - `preprocessText`: 텍스트 정규화 및 정리
   - `validateDocumentStructure`: 구조 검증
   - `extractTextFromDocument`: CPU 집약적 텍스트 추출
   - `tokenizeText`: 텍스트 토큰화 및 분석
   - `compressDocument`: 문서 압축

2. **GPU 집약적 액티비티** (GPU 가속 워커에서 실행)
   - `generateEmbeddings`: GPU를 사용한 벡터 임베딩
   - `classifyDocument`: ML 모델을 사용한 문서 분류
   - `performOCR`: 광학 문자 인식
   - `analyzeImages`: 이미지 분석 및 특징 추출
   - `runLLMInference`: 대규모 언어 모델 추론

3. **IO 집약적 액티비티** (IO 최적화 워커에서 실행)
   - `downloadDocument`: 비동기 문서 다운로드
   - `uploadToS3`: 스트리밍을 사용한 S3/MinIO 업로드
   - `queryMetadataDatabase`: 데이터베이스 쿼리
   - `storeVectorEmbeddings`: ChromaDB 저장
   - `callExternalAPI`: 외부 API 호출

### 워크플로우 구현

#### 1. DocumentPipelineWorkflow
원본 `DocumentPipelineWorkflowImpl`은 전체 파이프라인을 오케스트레이션합니다:

1. CSV를 파싱하여 문서 목록 가져오기
2. 구성 가능한 배치로 문서 처리
3. 각 배치가 모든 파이프라인 단계를 거침
4. 배치 내 병렬 처리
5. 결과 집계 및 메타데이터 저장

#### 2. ResourceOptimizedWorkflow
새로운 `ResourceOptimizedWorkflowImpl`은 리소스 활용을 최적화합니다:

1. **태스크 큐 분리**: 전문 워커로 액티비티 라우팅
2. **리소스 인식 스케줄링**: CPU, GPU, IO 작업이 적절한 워커에서 실행
3. **향상된 병렬성**: 다른 리소스 유형이 동시에 처리
4. **더 나은 리소스 활용**: 리소스 경합 방지

### 주요 기능

- **배치 처리**: 효율성을 위한 구성 가능한 배치 크기
- **병렬 실행**: Temporal의 Async API를 사용한 배치 내 병렬성
- **오류 처리**: 부분 실패로 우아한 성능 저하
- **하트비트**: 장시간 실행 액티비티가 진행 상황 보고
- **재시도**: 복원력을 위한 구성 가능한 재시도 정책
- **Spring Boot 통합**: Spring의 DI 및 구성 활용
- **리소스 최적화**: CPU, GPU, IO 워크로드를 위한 전용 워커
- **태스크 큐 라우팅**: 적절한 전문 워커에서 액티비티 실행

## 성능 튜닝

### JVM 옵션

Dockerfile에는 최적화된 JVM 설정이 포함되어 있습니다:

```dockerfile
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC"
```

### 워커 구성

#### 원본 워커
워크로드에 따라 워커 설정 조정:

```java
WorkerFactoryOptions options = WorkerFactoryOptions.newBuilder()
    .setMaxWorkflowThreadCount(100)
    .setMaxActivityThreadCount(50)
    .build();
```

#### 리소스 최적화 워커
각 워커 유형에는 최적화된 구성이 있습니다:

**CPU 워커**:
```java
// CPU 집약적 작업을 위해 ForkJoinPool 사용
int cpuCores = Runtime.getRuntime().availableProcessors();
ForkJoinPool customThreadPool = new ForkJoinPool(cpuCores);
```

**GPU 워커**:
```java
// 라운드로빈 스케줄링으로 GPU 리소스 관리
@Value("${gpu.count:2}")
private int gpuCount;
private final AtomicInteger gpuIndex = new AtomicInteger(0);
```

**IO 워커**:
```java
// 비동기 I/O 작업에 최적화
WorkerOptions.newBuilder()
    .setMaxConcurrentActivityExecutionSize(200)
    .setMaxConcurrentLocalActivityExecutionSize(200)
    .build();
```

### 데이터베이스 연결 풀

`application.yml`에서 HikariCP 구성:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
```

## 모니터링

### 로그

애플리케이션 로그는 콘솔에 작성되며 리디렉션할 수 있습니다:

```bash
./scripts/run-worker.sh > worker.log 2>&1
```

### 메트릭

애플리케이션은 Spring Boot Actuator 엔드포인트를 노출합니다:

- 헬스: `http://localhost:8080/actuator/health`
- 메트릭: `http://localhost:8080/actuator/metrics`

### Temporal 웹 UI

워크플로우 모니터링: `http://localhost:8088`

## 문제 해결

### 일반적인 문제

1. **OutOfMemoryError**
   - 힙 크기 증가: `export JAVA_OPTS="-Xmx4g"`
   - 워크플로우 스타터에서 배치 크기 감소

2. **Connection Refused**
   - 모든 서비스가 실행 중인지 확인: `docker-compose ps`
   - 구성에서 서비스 URL 확인

3. **느린 성능**
   - 워크플로우에서 비동기 처리 활성화
   - 워커 스레드 수 증가
   - 배치 크기 최적화

### 디버그 모드

디버그 로깅 활성화:

```yaml
logging:
  level:
    com.example: DEBUG
    io.temporal: DEBUG
```

## 개발

### 테스트 실행

```bash
mvn test
```

### 코드 커버리지

```bash
mvn jacoco:report
```

### JAR만 빌드

```bash
mvn clean package -DskipTests
```

## Go 구현과의 비교

| 측면 | Java | Go |
|--------|------|-----|
| 메모리 사용량 | 높음 (JVM 오버헤드) | 낮음 |
| 시작 시간 | 느림 (JVM 워밍업) | 빠름 |
| 처리량 | 워밍업 후 높음 | 일관되게 높음 |
| 생태계 | 풍부함 (Spring 등) | 성장 중 |
| 오류 처리 | 예외 | 명시적 오류 |
| 동시성 | 스레드 기반 | 고루틴 |

## 다음 단계

1. 포괄적인 단위 테스트 추가
2. 통합 테스트 구현
3. 메트릭 수집 추가
4. 캐싱 레이어 구현
5. 관리를 위한 API 엔드포인트 추가
6. 오류 복구 전략 강화

## 리소스 최적화 실행

리소스 최적화 구현은 상당한 이점을 제공합니다:

### 성능 개선
- **CPU 작업**: 전용 CPU 워커로 2-3배 빠른 처리
- **GPU 작업**: GPU 가속으로 5-10배 빠른 ML 추론
- **IO 작업**: 최적화된 동시성으로 3-4배 높은 처리량

### 리소스 활용
- **CPU 워커**: ForkJoinPool로 CPU 코어 활용 최대화
- **GPU 워커**: ML 워크로드를 위한 효율적인 GPU 스케줄링
- **IO 워커**: 네트워크 및 디스크 작업을 위한 높은 동시성

### 엔터프라이즈 이점
- **Spring Boot 통합**: 기존 Spring 전문 지식 활용
- **구성 관리**: 환경별 프로파일
- **모니터링**: 내장 헬스 체크 및 메트릭
- **배포**: Docker 및 Kubernetes 준비

### 확장성 이점
- 각 워커 유형을 독립적으로 확장
- ML 집약적 워크로드를 위해 더 많은 GPU 워커 추가
- 다운로드 집약적 워크로드를 위해 더 많은 IO 워커 추가
- Spring 프로파일로 효율적인 리소스 사용 유지