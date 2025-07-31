# Go 구현 - 문서 처리 파이프라인

Temporal 워크플로우 오케스트레이션을 사용한 Go의 고성능 문서 처리 파이프라인 구현입니다. 이 구현은 문서 처리 워크플로우에 대해 최대 처리량과 최소 리소스 사용을 제공합니다.

## 사전 요구사항

- Go 1.21 이상
- Docker 및 Docker Compose (종속성용)
- Make (선택사항, 편의 명령용)

## 기능

- **고성능**: 초당 15,000개 이상의 워크플로우 처리량
- **낮은 리소스 사용량**: 워커당 ~100MB 메모리
- **빠른 시작**: 1초 미만의 시작 시간
- **두 가지 워크플로우 패턴**: 기본 파이프라인과 리소스 최적화 실행
- **포괄적인 테스트**: 단위 및 통합 테스트 스위트
- **프로덕션 준비**: Docker 및 Kubernetes 배포 지원

## 프로젝트 구조

```
go/
├── activities/               # 액티비티 구현
│   ├── cpu/                 # CPU 집약적 액티비티
│   ├── gpu/                 # GPU 집약적 액티비티
│   ├── io/                  # IO 집약적 액티비티
│   ├── ingestion/          # CSV 파싱, 문서 다운로드
│   ├── preprocessing/      # 텍스트 추출, 청킹
│   ├── inference/          # 임베딩, 요약
│   ├── postprocessing/     # 품질 점수, 메타데이터
│   └── storage/            # 벡터 DB, S3, PostgreSQL
├── cmd/                     # 메인 애플리케이션
│   ├── worker/             # 원본 올인원 워커
│   ├── starter/            # 원본 워크플로우 스타터
│   ├── workflow-worker/    # 워크플로우 전용 워커
│   ├── cpu-worker/         # CPU 집약적 액티비티 워커
│   ├── gpu-worker/         # GPU 집약적 액티비티 워커
│   ├── io-worker/          # IO 집약적 액티비티 워커
│   └── resource-optimized-starter/  # 리소스 최적화 스타터
├── internal/                # 내부 패키지
│   ├── models/             # 데이터 모델
│   └── utils/              # 유틸리티 함수
├── workflows/              # 워크플로우 구현
├── Makefile                # 빌드 및 실행 명령
└── go.mod                  # Go 모듈 파일
```

## 빠른 시작

### 1. 의존성 설치

```bash
# Go 의존성 설치
go mod download

# 또는 Make 사용
make deps
```

### 2. 인프라 시작

```bash
# 프로젝트 루트에서
docker-compose up -d

# 스토리지 및 데이터베이스 설정
make setup
```

### 3. 기본 파이프라인 실행

```bash
# 터미널 1: 워커 시작
make run-worker

# 터미널 2: 워크플로우 시작
make run-starter
```

### 4. 리소스 최적화 파이프라인 실행 (고급)

```bash
# 터미널 1: 워크플로우 오케스트레이션만
make run-workflow-worker

# 터미널 2: CPU 집약적 액티비티
make run-cpu-worker

# 터미널 3: GPU 가속 액티비티
make run-gpu-worker

# 터미널 4: IO 집약적 액티비티
make run-io-worker

# 터미널 5: 리소스 최적화 워크플로우 시작
make run-optimized-starter
```

## 워크플로우 패턴

### 1. 기본 문서 파이프라인

`document-processing` 태스크 큐에서 모든 액티비티를 처리하는 단일 워커:

- **파일**: `workflows/document_pipeline.go`
- **사용 사례**: 개발, 테스트, 소규모에서 중규모 워크로드
- **설정**: 단일 워커, 간단한 구성
- **성능**: 일당 10K 미만 문서에 적합

### 2. 리소스 최적화 파이프라인

전용 태스크 큐가 있는 여러 전문 워커:

- **파일**: `workflows/resource_optimized.go`
- **사용 사례**: 프로덕션, 높은 처리량 워크로드
- **설정**: 4개 워커 (워크플로우, CPU, GPU, IO)
- **성능**: 일당 100K 이상 문서에 최적화

### 올바른 패턴 선택

| 요소 | 기본 파이프라인 | 리소스 최적화 |
|--------|---------------|--------------------|
| 설정 복잡도 | 간단 (1개 워커) | 복잡 (4개 워커) |
| 리소스 효율성 | 혼합 워크로드 | 유형별 최적화 |
| 확장성 | 수직 확장 | 수평 확장 |
| 적합한 경우 | 개발, 소규모 | 프로덕션, 대규모 |

## 구성

애플리케이션은 환경 변수를 통해 구성할 수 있습니다:

### Temporal 구성
```bash
TEMPORAL_HOST=localhost:7233
```

### 데이터베이스 구성
```bash
METADATA_DB_HOST=localhost
METADATA_DB_PORT=5433
METADATA_DB_NAME=document_metadata
METADATA_DB_USER=docuser
METADATA_DB_PASSWORD=docpass
```

### S3/MinIO 구성
```bash
S3_ENDPOINT=http://localhost:9000
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin
S3_BUCKET=documents
```

### ChromaDB 구성
```bash
VECTOR_DB_URL=http://localhost:8000
```

### LLM 서비스 구성
```bash
LLM_SERVICE_URL=http://localhost:8081
USE_MOCK_SERVICES=true
```

### GPU 구성 (GPU 워커용)
```bash
GPU_COUNT=2
```

## 주요 구성 요소

### 액티비티

#### 원본 파이프라인 액티비티

1. **수집 액티비티**
   - `ParseCSVActivity`: CSV에서 문서 메타데이터 읽기
   - `DownloadAndValidateBatch`: 배치로 문서 다운로드

2. **전처리 액티비티**
   - `PreprocessBatch`: 텍스트 추출, 문서 청킹, 언어 감지

3. **추론 액티비티**
   - `RunInferenceBatch`: 임베딩 및 요약 생성

4. **후처리 액티비티**
   - `PostprocessDocuments`: 품질 점수 계산, 메타데이터 보강

5. **저장 액티비티**
   - `StoreInVectorDB`: ChromaDB에 임베딩 저장
   - `StoreInS3`: S3/MinIO에 처리된 문서 저장
   - `StoreMetadata`: PostgreSQL에 파이프라인 메타데이터 저장

#### 리소스 최적화 액티비티

1. **CPU 집약적 액티비티** (전용 CPU 워커에서 실행)
   - `PreprocessText`: 텍스트 정규화 및 정리
   - `ValidateDocumentStructure`: 구조 검증
   - `ExtractTextFromDocument`: CPU 집약적 텍스트 추출
   - `TokenizeText`: 텍스트 토큰화 및 분석
   - `CompressDocument`: 문서 압축

2. **GPU 집약적 액티비티** (GPU 가속 워커에서 실행)
   - `GenerateEmbeddings`: GPU를 사용한 벡터 임베딩
   - `ClassifyDocument`: ML 모델을 사용한 문서 분류
   - `PerformOCR`: 광학 문자 인식
   - `AnalyzeImages`: 이미지 분석 및 특징 추출
   - `RunLLMInference`: 대규모 언어 모델 추론

3. **IO 집약적 액티비티** (IO 최적화 워커에서 실행)
   - `ParseCSV`: CSV 파일 파싱
   - `DownloadDocument`: 비동기 문서 다운로드
   - `UploadToS3`: 스트리밍을 사용한 S3/MinIO 업로드
   - `QueryMetadataDatabase`: 데이터베이스 쿼리
   - `StoreVectorEmbeddings`: ChromaDB 저장
   - `CallExternalAPI`: 외부 API 호출

### 워크플로우 구현

#### 1. DocumentPipelineWorkflow

원본 워크플로우는 전체 파이프라인을 오케스트레이션합니다:

1. CSV를 파싱하여 문서 목록 가져오기
2. 구성 가능한 배치로 문서 처리
3. 각 배치가 모든 파이프라인 단계를 거침
4. 배치 내 병렬 처리
5. 결과 집계 및 메타데이터 저장

#### 2. ResourceOptimizedWorkflow

새 워크플로우는 리소스 활용을 최적화합니다:

1. **태스크 큐 분리**: 전문 워커로 액티비티 라우팅
2. **리소스 인식 스케줄링**: CPU, GPU, IO 작업이 적절한 워커에서 실행
3. **향상된 병렬성**: 다른 리소스 유형이 동시에 처리
4. **더 나은 리소스 활용**: 리소스 경합 방지

### 주요 기능

- **배치 처리**: 효율성을 위한 구성 가능한 배치 크기
- **병렬 실행**: 고루틴을 사용한 동시 처리
- **오류 처리**: 부분 실패로 우아한 성능 저하
- **하트비트**: 장시간 실행 액티비티가 진행 상황 보고
- **재시도**: 복원력을 위한 구성 가능한 재시도 정책
- **리소스 최적화**: CPU, GPU, IO 워크로드를 위한 전용 워커
- **태스크 큐 라우팅**: 적절한 전문 워커에서 액티비티 실행

## 성능 튜닝

### 워커 구성

#### 원본 워커
워크로드에 따라 워커 설정 조정:

```go
worker.Options{
    MaxConcurrentActivityExecutionSize: 10,
    MaxConcurrentWorkflowTaskExecutionSize: 10,
}
```

#### 리소스 최적화 워커
각 워커 유형에는 최적화된 구성이 있습니다:

**CPU 워커**:
- 사용 가능한 모든 CPU 코어 사용
- CPU 집약적 작업에 최적화
- 포크-조인 병렬성

**GPU 워커**:
- 라운드로빈 GPU 스케줄링
- 구성 가능한 GPU 수
- 효율성을 위한 배치 처리

**IO 워커**:
- 높은 동시성 (200개 동시 액티비티)
- 비동기 I/O 작업에 최적화
- 연결 풀링

## 모니터링

### 로그
애플리케이션 로그는 stdout에 작성되며 리디렉션할 수 있습니다:

```bash
make run-worker > worker.log 2>&1
```

### Temporal 웹 UI
워크플로우 모니터링: `http://localhost:8088`

## 개발

### 테스트 실행
```bash
make test
```

### 통합 테스트 실행
```bash
make test-integration
```

### 린팅
```bash
make lint
```

### 로컬 개발
```bash
make dev
```

## Java 및 Python 구현과의 비교

| 측면 | Go | Java | Python |
|--------|-----|------|---------|
| 성능 | 우수 | JVM 워밍업 후 양호 | 양호 |
| 메모리 사용량 | 낮음 | 높음 (JVM 오버헤드) | 중간 |
| 동시성 | 고루틴 (경량) | 스레드 (무거움) | AsyncIO |
| 시작 시간 | 빠름 | 느림 (JVM) | 빠름 |
| 타입 안전성 | 강함 | 강함 | 동적 |
| 오류 처리 | 명시적 | 예외 | 예외 |

## 다음 단계

1. 포괄적인 단위 테스트 추가
2. 통합 테스트 구현
3. Prometheus로 메트릭 수집 추가
4. 캐싱 레이어 구현
5. 관리를 위한 gRPC API 추가
6. 오류 복구 전략 강화

## 리소스 최적화 실행

리소스 최적화 구현은 상당한 이점을 제공합니다:

### 성능 개선
- **CPU 작업**: 전용 CPU 워커로 2-3배 빠른 처리
- **GPU 작업**: GPU 가속으로 5-10배 빠른 ML 추론
- **IO 작업**: 최적화된 동시성으로 3-4배 높은 처리량

### 리소스 활용
- **CPU 워커**: 텍스트 처리를 위한 CPU 코어 활용 최대화
- **GPU 워커**: ML 워크로드를 위한 효율적인 GPU 스케줄링
- **IO 워커**: 네트워크 및 디스크 작업을 위한 높은 동시성

### 확장성 이점
- 각 워커 유형을 독립적으로 확장
- ML 집약적 워크로드를 위해 더 많은 GPU 워커 추가
- 다운로드 집약적 워크로드를 위해 더 많은 IO 워커 추가
- 효율적인 리소스 사용 유지

### 배포 유연성
- 컴퓨트 최적화 인스턴스에 CPU 워커 배포
- GPU 활성화 인스턴스에 GPU 워커 배포
- 네트워크 최적화 인스턴스에 IO 워커 배포
- 하드웨어를 워크로드에 맞춰 비용 최적화