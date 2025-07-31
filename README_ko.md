# Temporal 문서 처리 파이프라인

Temporal 워크플로우 오케스트레이션으로 구축된 프로덕션 준비 문서 처리 파이프라인입니다. 이 시스템은 문서 수집, 처리 및 RAG(검색 증강 생성) 준비를 위한 확장 가능한 AI/ML 파이프라인을 구축하는 방법을 보여줍니다.

**다중 언어 지원**: Go, Java, Python의 완전한 구현과 두 가지 워크플로우 패턴 제공: 기본 파이프라인과 리소스 최적화 실행.

## 아키텍처 개요

파이프라인은 5단계 문서 처리 워크플로우를 구현합니다:

1. **데이터 수집** - URL 또는 로컬 경로에서 문서 다운로드
2. **전처리** - 텍스트 추출, 문서 분할, 언어 감지, PII 제거
3. **모델 추론** - LLM API를 사용한 임베딩 및 요약 생성
4. **후처리** - 품질 점수 매기기, 메타데이터 보강, 검색 인덱싱
5. **저장** - 벡터 DB(ChromaDB), S3(MinIO), PostgreSQL에 저장

### 벡터 임베딩에 Temporal을 사용하는 이유

Temporal은 다음을 제공하기 때문에 임베딩 생성에 특히 적합합니다:

- **내구성**: 장시간 실행되는 임베딩 생성 중 장애로부터 자동 복구
- **확장성**: 데이터 양이 증가함에 따른 손쉬운 수평 확장
- **신뢰성**: 불안정한 API 호출 및 속도 제한에 대한 내장 재시도
- **관찰성**: 각 임베딩이 생성된 방법에 대한 완전한 감사 추적
- **장시간 실행 지원**: 시간 초과 없이 시간 또는 일 단위로 대규모 데이터셋 처리

자세한 분석은 [벡터 임베딩에 Temporal을 사용하는 이유](docs/temporal-vector-embeddings_ko.md)를 참조하세요.

## 기능

- **다중 언어 지원**: Go, Java, Python의 완전한 구현
- **두 가지 워크플로우 패턴**: 
  - **기본 파이프라인**: 모든 액티비티를 처리하는 단일 워커
  - **리소스 최적화**: CPU, GPU, IO 집약적 액티비티를 위한 별도 워커
- **확장 가능한 아키텍처**: 수평 확장이 가능한 워커 기반 처리
- **장애 허용**: 자동 재시도, 상태 지속성 및 우아한 오류 처리
- **관찰성**: 포괄적인 메트릭, 로깅 및 워크플로우 시각화
- **로컬 개발**: 모의 서비스가 포함된 완전한 Docker Compose 환경
- **프로덕션 준비**: Kubernetes 구성 및 클라우드 배포 가이드
- **인간 개입**: 품질 검토 및 수동 개입 지원
- **버전 진화**: 진행 중인 워크플로우에 영향을 주지 않는 안전한 업데이트

## 빠른 시작

### 사전 요구사항

- Docker 및 Docker Compose
- Go 1.21+ (Go 구현용)
- Java 17+ 및 Gradle (Java 구현용)
- Python 3.11+ 및 pip (Python 구현용)
- Make (선택사항, 편의 명령용)

### 1. 인프라 시작

```bash
# 모든 서비스 시작
docker-compose up -d

# 서비스가 정상 상태가 될 때까지 대기
docker-compose ps

# S3 버킷 생성
make -C go setup-minio

# 데이터베이스 초기화
make -C go setup-db
```

### 2. Go 구현 실행

```bash
cd go

# 의존성 설치
make deps

# 워커 실행
make run-local-worker

# 다른 터미널에서 워크플로우 시작
make run-local-starter
```

### 3. Java 구현 실행

```bash
cd java

# 워커 실행
./gradlew runWorker

# 다른 터미널에서 워크플로우 시작
./gradlew runStarter
```

### 4. Python 구현 실행

```bash
cd python

# 의존성 설치
pip install -r requirements.txt

# 워커 실행
make run-worker

# 다른 터미널에서 워크플로우 시작
make run-starter
```

## 프로젝트 구조

```
temporal-example/
├── go/                      # Go 구현
│   ├── cmd/                 # 메인 애플리케이션
│   │   ├── worker/         # 기본 워커 프로세스
│   │   ├── starter/        # 기본 워크플로우 스타터
│   │   ├── cpu-worker/     # CPU 최적화 워커
│   │   ├── gpu-worker/     # GPU 최적화 워커
│   │   ├── io-worker/      # IO 최적화 워커
│   │   ├── workflow-worker/ # 워크플로우 전용 워커
│   │   └── resource-optimized-starter/ # 리소스 최적화 스타터
│   ├── activities/         # 액티비티 구현
│   │   ├── cpu/           # CPU 집약적 액티비티
│   │   ├── gpu/           # GPU 집약적 액티비티
│   │   └── io/            # IO 집약적 액티비티
│   ├── workflows/          # 워크플로우 정의
│   ├── internal/models/    # 데이터 모델
│   └── Makefile           # 빌드 및 실행 명령
├── java/                    # Java 구현
│   ├── src/main/java/com/example/
│   │   ├── activities/     # 액티비티 구현
│   │   │   ├── cpu/       # CPU 집약적 액티비티
│   │   │   ├── gpu/       # GPU 집약적 액티비티
│   │   │   └── io/        # IO 집약적 액티비티
│   │   ├── workflows/      # 워크플로우 정의
│   │   ├── workers/        # 워커 구성
│   │   └── models/         # 데이터 모델
│   └── build.gradle        # Gradle 빌드 구성
├── python/                  # Python 구현
│   ├── activities/         # 액티비티 구현
│   │   ├── cpu/           # CPU 집약적 액티비티
│   │   ├── gpu/           # GPU 집약적 액티비티
│   │   └── io/            # IO 집약적 액티비티
│   ├── workflows/          # 워크플로우 정의
│   ├── workers/            # 워커 프로세스
│   ├── requirements.txt    # Python 의존성
│   └── Makefile           # 빌드 및 실행 명령
├── docker-compose.yml       # 로컬 개발 환경
├── testdata/               # 샘플 테스트 데이터
├── init-scripts/           # 데이터베이스 초기화
└── docs/                   # 추가 문서
```

## 워크플로우 예제

이 프로젝트는 두 가지 다른 워크플로우 패턴을 보여줍니다:

### 1. 기본 문서 파이프라인

하나의 워커가 모든 액티비티 유형을 처리하는 전통적인 단일 워커 접근 방식:

- **단일 태스크 큐**: `document-processing`
- **워커 구성**: 모든 액티비티 처리 (CPU, GPU, IO 집약적)
- **사용 사례**: 간단한 설정, 소규모에서 중규모 워크로드
- **파일**: 
  - Go: `workflows/document_pipeline.go`
  - Java: `workflows/DocumentPipelineWorkflow.java`
  - Python: `workflows/document_pipeline.py`

### 2. 리소스 최적화 파이프라인

리소스별 태스크 큐를 사용하는 고급 다중 워커 접근 방식:

- **태스크 큐**:
  - `document-pipeline-queue`: 워크플로우 오케스트레이션만
  - `cpu-bound-queue`: 텍스트 처리, 검증, 압축
  - `gpu-bound-queue`: ML 추론, 임베딩, OCR
  - `io-bound-queue`: 다운로드, 업로드, 데이터베이스 작업
- **워커 전문화**: 각 워커가 특정 리소스 유형에 최적화
- **사용 사례**: 높은 처리량, 프로덕션 워크로드, 리소스 최적화
- **파일**:
  - Go: `workflows/resource_optimized.go`
  - Java: `workflows/ResourceOptimizedWorkflow.java`
  - Python: `workflows/resource_optimized.py`

### 올바른 패턴 선택

| 요소 | 기본 파이프라인 | 리소스 최적화 |
|--------|---------------|-------------------|
| 설정 복잡도 | 간단 (1개 워커) | 복잡 (4개 워커) |
| 리소스 효율성 | 혼합 워크로드 | 유형별 최적화 |
| 확장성 | 수직 확장 | 수평 확장 |
| 적합한 경우 | 개발, 소규모 | 프로덕션, 대규모 |

## 구현 가이드

각 언어 구현에는 자체적인 상세한 설정 가이드가 있습니다:

- **Go**: [go/README_ko.md](go/README_ko.md) - 고성능 구현
- **Java**: [java/README_ko.md](java/README_ko.md) - 엔터프라이즈 준비 구현
- **Python**: [python/README_ko.md](python/README_ko.md) - 빠른 개발 구현

## 핵심 서비스

### Temporal
- **UI**: http://localhost:8080
- **gRPC**: localhost:7233

워크플로우 실행 모니터링, 이력 보기 및 문제 디버깅.

### MinIO (S3 호환)
- **콘솔**: http://localhost:9001 (minioadmin/minioadmin)
- **API**: http://localhost:9000

처리된 문서를 위한 객체 저장소.

### ChromaDB
- **API**: http://localhost:8000

문서 임베딩을 위한 벡터 데이터베이스.

### PostgreSQL
- **메타데이터 DB**: localhost:5433 (docuser/docpass)

파이프라인 메타데이터 및 처리 이력 저장.

## 개발

### 테스트 실행

```bash
# Go 테스트
cd go && make test

# Java 테스트
cd java && ./gradlew test

# Python 테스트
cd python && make test
```

### Docker 이미지 빌드

```bash
# Go 워커
cd go && docker build -t temporal-rag-go:latest .

# Java 워커
cd java && ./gradlew buildDocker
```

### 구성

워커 구성을 위한 환경 변수:

```bash
# Temporal
TEMPORAL_HOST_URL=localhost:7233

# 저장소
S3_ENDPOINT=http://localhost:9000
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin
S3_BUCKET=documents

# 벡터 DB
VECTOR_DB_URL=http://localhost:8000

# 메타데이터 DB
METADATA_DB_HOST=localhost
METADATA_DB_PORT=5433
METADATA_DB_NAME=document_metadata
METADATA_DB_USER=docuser
METADATA_DB_PASSWORD=docpass

# LLM 서비스
LLM_SERVICE_URL=http://localhost:8081
USE_MOCK_SERVICES=true
```

## 워크플로우 옵션

워크플로우를 시작할 때 다음을 구성할 수 있습니다:

```bash
# Go 스타터
./bin/starter \
  --csv-file ../testdata/documents.csv \
  --s3-bucket documents \
  --vector-db-collection documents \
  --embedding-model text-embedding-3-small \
  --summary-model gpt-3.5-turbo \
  --remove-pii \
  --languages en,es

# Java 스타터
java -jar build/libs/temporal-rag.jar \
  --csv-file ../testdata/documents.csv \
  --s3-bucket documents \
  --vector-db-collection documents \
  --embedding-model text-embedding-3-small \
  --summary-model gpt-3.5-turbo
```

## 성능 비교

| 측면 | Go | Java | Python |
|--------|----|---------|---------|
| 처리량 | 15,000+ workflows/sec | 10,000+ workflows/sec | 5,000+ workflows/sec |
| 메모리 사용량 | ~100MB | ~500MB | ~200MB |
| 시작 시간 | 1초 미만 | 5-10초 | 2-3초 |
| GC 일시정지 | 최소 | 구성 가능 | 자동 |
| 개발 속도 | 빠름 | 보통 | 매우 빠름 |
| 적합한 경우 | 높은 처리량, 마이크로서비스 | 엔터프라이즈 통합, 복잡한 로직 | 빠른 프로토타이핑, AI/ML |

## 프로덕션 배포

### Kubernetes

다음 내용은 `docs/kubernetes-deployment_ko.md`를 참조하세요:

- Helm 차트
- 리소스 구성
- 자동 확장 설정
- 모니터링 통합

### 클라우드 제공자

- **AWS**: EKS와 Aurora PostgreSQL, S3 사용
- **GCP**: GKE와 Cloud SQL, GCS 사용
- **Azure**: AKS와 Azure PostgreSQL, Blob Storage 사용

## 모니터링

### 메트릭

- 워크플로우 완료율
- 문서 처리 처리량
- 단계별 오류율
- 모델 추론 지연 시간
- 스토리지 사용 추세

### 권장 스택

- 메트릭을 위한 Prometheus + Grafana
- 로그를 위한 Elasticsearch + Kibana
- 분산 추적을 위한 Jaeger

## 문제 해결

### 일반적인 문제

1. **워커가 연결되지 않음**
   - Temporal이 실행 중인지 확인: `docker-compose ps`
   - 네트워크 연결 확인
   - 로그 확인: `docker-compose logs temporal`

2. **스토리지 오류**
   - MinIO 버킷이 존재하는지 확인
   - 환경에서 자격 증명 확인
   - PostgreSQL 스키마가 생성되었는지 확인

3. **임베딩 실패**
   - LLM 서비스가 실행 중인지 확인
   - API 키 확인 (실제 서비스 사용 시)
   - 속도 제한 확인

### 디버그 명령

```bash
# 워커 로그 보기
docker-compose logs -f go-worker

# Temporal 워크플로우 확인
temporal workflow list

# PostgreSQL 검사
psql -h localhost -p 5433 -U docuser -d document_metadata

# MinIO 연결 테스트
mc alias set local http://localhost:9000 minioadmin minioadmin
mc ls local/
```

## 기여하기

1. 저장소 포크
2. 기능 브랜치 생성
3. 변경사항 작성
4. 테스트 추가
5. 풀 리퀘스트 제출

## 라이선스

MIT 라이선스 - 자세한 내용은 LICENSE 파일 참조

## 감사의 말

- 워크플로우 오케스트레이션을 위한 Temporal.io
- 벡터 저장을 위한 ChromaDB
- S3 호환 저장소를 위한 MinIO
- 임베딩 모델을 위한 OpenAI

## 리소스 최적화 실행

세 가지 구현 모두 리소스 최적화 실행을 지원합니다. 설정 지침은 상세한 구현 가이드를 참조하세요:

- **Go**: [go/README_ko.md#리소스-최적화-실행](go/README_ko.md#리소스-최적화-실행)
- **Java**: [java/README_ko.md#리소스-최적화-실행](java/README_ko.md#리소스-최적화-실행)  
- **Python**: [python/README_ko.md#리소스-최적화-실행](python/README_ko.md#리소스-최적화-실행)

### 리소스 최적화의 이점

1. **더 나은 리소스 활용**: 각 워커 유형이 워크로드에 최적화됨
2. **향상된 확장성**: 병목 현상에 따라 워커를 독립적으로 확장
3. **경합 감소**: 다른 리소스 유형이 경쟁하지 않음
4. **유연한 배포**: 적절한 하드웨어에 워커 배포

### 태스크 큐 아키텍처

- **document-pipeline-queue**: 워크플로우 오케스트레이션만
- **cpu-bound-queue**: CPU 집약적 액티비티 (텍스트 처리, 검증)
- **gpu-bound-queue**: GPU 가속 액티비티 (ML 추론, 임베딩)
- **io-bound-queue**: IO 집약적 액티비티 (다운로드, 업로드, DB 작업)

## 문서

### 구현 가이드

- **[Go 구현 가이드](go/README_ko.md)** - 상세한 설정 지침이 포함된 고성능 구현
- **[Java 구현 가이드](java/README_ko.md)** - Spring Boot 통합이 포함된 엔터프라이즈 준비 구현
- **[Python 구현 가이드](python/README_ko.md)** - AI/ML에 중점을 둔 빠른 개발 구현

### 아키텍처 문서

- **[리소스 최적화 실행 가이드](docs/resource-optimized-execution-guide_ko.md)** - 리소스별 태스크 큐에 대한 완전한 가이드
- **[워크플로우 오케스트레이션 비교](docs/workflow-orchestration-comparison_ko.md)** - Temporal과 다른 오케스트레이션 접근 방식 비교
- **[AI 파이프라인 구현](docs/ai-workflow-implementation_ko.md)** - AI/ML 파이프라인 패턴 및 모범 사례

### 설정 및 운영

- **[CPU 대 GPU 구현 요약](docs/cpu-gpu-io-implementation-summary_ko.md)** - 리소스 최적화 전략
- **[Python 워크플로우 구현](docs/python-workflow-implementation_ko.md)** - Python 특화 패턴 및 최적화

### 외부 리소스

- [Temporal 문서](https://docs.temporal.io) - 공식 Temporal 문서
- [ChromaDB 문서](https://docs.trychroma.com) - 벡터 데이터베이스 문서
- [MinIO 문서](https://docs.min.io) - S3 호환 저장소 문서