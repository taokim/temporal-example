# Python 구현 - 문서 처리 파이프라인

Temporal 워크플로우 오케스트레이션을 사용한 Python의 빠른 개발 문서 처리 파이프라인 구현입니다. 이 구현은 우수한 AI/ML 라이브러리 통합과 함께 빠른 프로토타이핑 기능을 제공합니다.

## 사전 요구사항

- Python 3.11 이상
- pip (Python 패키지 관리자)
- Docker 및 Docker Compose (종속성용)

## 기능

- **빠른 개발**: 빠른 반복과 프로토타이핑
- **AI/ML 통합**: 인기 있는 ML 라이브러리에 대한 네이티브 지원
- **비동기 작업**: 높은 동시성을 위한 asyncio 기반
- **리소스 최적화 실행**: CPU, GPU, IO 작업을 위한 전문 워커
- **쉬운 설정**: 간단한 pip 설치와 최소한의 구성
- **개발 친화적**: 포괄적인 테스트와 함께 대화형 개발

## 프로젝트 구조

```
python/
├── activities/              # 액티비티 구현
│   ├── cpu/                # CPU 집약적 액티비티
│   ├── gpu/                # GPU 집약적 액티비티
│   ├── io/                 # IO 집약적 액티비티
│   ├── ingestion/         # CSV 파싱, 문서 다운로드
│   ├── preprocessing/     # 텍스트 추출, 청킹
│   ├── inference/         # 임베딩, 요약
│   ├── postprocessing/    # 품질 점수, 메타데이터
│   └── storage/           # 벡터 DB, S3, PostgreSQL
├── workflows/             # 워크플로우 구현
├── workers/               # 워커 구현
├── models/                # 데이터 모델
├── utils/                 # 유틸리티 함수
├── tests/                 # 테스트 파일
├── requirements.txt       # Python 의존성
├── Makefile              # 빌드 및 실행 명령
├── start_resource_optimized.py  # 리소스 최적화 스타터
└── README.md             # 이 파일
```

## 빠른 시작

### 1. 의존성 설치

```bash
# Python 의존성 설치
pip install -r requirements.txt

# 또는 가상 환경 사용 (권장)
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt

# 또는 Make 사용
make install
```

### 2. 인프라 시작

```bash
# 프로젝트 루트에서
docker-compose up -d

# 서비스 실행 확인
docker-compose ps
```

### 3. 리소스 최적화 파이프라인 실행

```bash
# 터미널 1: 워크플로우 오케스트레이션만
make run-workflow-worker
# 또는: python workers/workflow_worker.py

# 터미널 2: CPU 집약적 액티비티
make run-cpu-worker
# 또는: python workers/cpu_worker.py

# 터미널 3: GPU 가속 액티비티  
make run-gpu-worker
# 또는: python workers/gpu_worker.py

# 터미널 4: IO 집약적 액티비티
make run-io-worker
# 또는: python workers/io_worker.py

# 터미널 5: 리소스 최적화 워크플로우 시작
make run-optimized
# 또는: python test_resource_optimized.py
```

## 워크플로우 패턴

### 리소스 최적화 파이프라인

Python 구현은 전문 워커가 있는 리소스 최적화 패턴에 중점을 둡니다:

- **파일**: `workflows/resource_optimized.py`
- **사용 사례**: AI/ML 워크로드, 높은 동시성 처리
- **설정**: 다른 리소스 유형에 최적화된 4개 워커
- **성능**: 머신러닝 파이프라인에 최적화

### 워커 전문화

각 워커 유형은 특정 작업에 최적화되어 있습니다:

- **워크플로우 워커**: 순수 오케스트레이션, 액티비티 실행 없음
- **CPU 워커**: 텍스트 처리, 검증 (멀티프로세싱 지원)
- **GPU 워커**: ML 추론, 임베딩 (GPU 스케줄링)
- **IO 워커**: 다운로드, 업로드, 데이터베이스 작업 (높은 비동기 동시성)

## 주요 구성 요소

### 리소스 최적화 액티비티

#### CPU 집약적 액티비티 (cpu-bound-queue)
- `preprocess_text`: 텍스트 정규화 및 정리
- `validate_document_structure`: 구조 검증
- `extract_text_from_document`: CPU 집약적 텍스트 추출
- `tokenize_text`: 텍스트 토큰화 및 분석
- `compress_document`: 문서 압축

#### GPU 집약적 액티비티 (gpu-bound-queue)
- `generate_embeddings`: GPU를 사용한 벡터 임베딩
- `classify_document`: ML 모델을 사용한 문서 분류
- `perform_ocr`: 광학 문자 인식
- `analyze_images`: 이미지 분석 및 특징 추출
- `run_llm_inference`: 대규모 언어 모델 추론

#### IO 집약적 액티비티 (io-bound-queue)
- `parse_csv`: CSV 파일 파싱
- `download_document`: 비동기 문서 다운로드
- `upload_to_s3`: 스트리밍을 사용한 S3/MinIO 업로드
- `query_metadata_database`: 데이터베이스 쿼리
- `store_vector_embeddings`: ChromaDB 저장
- `call_external_api`: 외부 API 호출

### 워크플로우 구현

`ResourceOptimizedWorkflow`는 문서 처리 파이프라인을 오케스트레이션합니다:

1. **태스크 큐 분리**: 전문 워커로 액티비티 라우팅
2. **리소스 인식 스케줄링**: CPU, GPU, IO 작업이 적절한 워커에서 실행
3. **향상된 병렬성**: 다른 리소스 유형이 동시에 처리
4. **더 나은 리소스 활용**: 리소스 경합 방지

### 워커 구성

**워크플로우 워커**:
- 워크플로우 오케스트레이션만 처리
- 액티비티 실행 없음

**CPU 워커**:
- CPU 집약적 작업을 위해 멀티프로세싱 사용
- `cpu_count * 2` 동시 액티비티로 구성

**GPU 워커**:
- 라운드로빈 GPU 스케줄링
- `gpu_count * 4` 동시 액티비티로 구성

**IO 워커**:
- 높은 동시성 (200개 동시 액티비티)
- 비동기 I/O 작업에 최적화

## 개발

### 코드 포맷팅

```bash
make format
```

### 린팅

```bash
make lint
```

### 테스트 실행

```bash
make test
```

### 빌드 아티팩트 정리

```bash
make clean
```

## 구성

모든 구성은 환경 변수를 통해 수행됩니다:

| 변수 | 설명 | 기본값 |
|----------|-------------|---------|
| TEMPORAL_HOST | Temporal 서버 주소 | localhost:7233 |
| S3_ENDPOINT | S3/MinIO 엔드포인트 | http://localhost:9000 |
| S3_ACCESS_KEY | S3 액세스 키 | minioadmin |
| S3_SECRET_KEY | S3 시크릿 키 | minioadmin |
| S3_BUCKET | S3 버킷 이름 | documents |
| VECTOR_DB_URL | ChromaDB URL | http://localhost:8000 |
| METADATA_DB_HOST | PostgreSQL 호스트 | localhost |
| METADATA_DB_PORT | PostgreSQL 포트 | 5433 |
| METADATA_DB_NAME | 데이터베이스 이름 | document_metadata |
| METADATA_DB_USER | 데이터베이스 사용자 | docuser |
| METADATA_DB_PASSWORD | 데이터베이스 비밀번호 | docpass |
| GPU_COUNT | GPU 수 | 2 |

## 성능 튜닝

### 워커 동시성

각 워커에서 `max_concurrent_activities` 매개변수를 조정하세요:

```python
# CPU 워커
max_concurrent_activities=multiprocessing.cpu_count() * 2

# GPU 워커
max_concurrent_activities=gpu_count * 4

# IO 워커
max_concurrent_activities=200
```

### 배치 크기

워크로드에 따라 배치 크기를 최적화하세요:

```bash
python start_resource_optimized.py --batch-size 20
```

### 액티비티 타임아웃

문서 크기와 처리 요구사항에 따라 워크플로우에서 타임아웃을 조정하세요.

## 모니터링

### Temporal 웹 UI

워크플로우 모니터링: `http://localhost:8088`

### 애플리케이션 로그

각 워커는 디버깅 및 모니터링에 도움이 되는 상세한 로그를 출력합니다.

## 다른 구현과의 비교

| 측면 | Python | Go | Java |
|--------|--------|-----|------|
| 비동기 지원 | 네이티브 (asyncio) | 고루틴 | CompletableFuture |
| 성능 | 양호 | 우수 | 워밍업 후 양호 |
| 메모리 사용량 | 중간 | 낮음 | 높음 |
| 타입 안전성 | 타입 힌트 | 강함 | 강함 |
| 시작 시간 | 빠름 | 빠름 | 느림 |
| 생태계 | 풍부한 ML/AI 라이브러리 | 성장 중 | 성숙함 |

## 문제 해결

### 일반적인 문제

1. **ImportError**: 가상 환경이 활성화되었는지 확인
   ```bash
   source venv/bin/activate
   ```

2. **Connection Refused**: 모든 서비스가 실행 중인지 확인
   ```bash
   docker-compose ps
   ```

3. **Activity Timeout**: 워크플로우 액티비티 옵션에서 타임아웃 증가

4. **메모리 문제**: 배치 크기 또는 워커 동시성 감소

## 다음 단계

1. 포괄적인 단위 테스트 추가
2. 통합 테스트 구현
3. Prometheus로 메트릭 수집 추가
4. 임베딩을 위한 캐싱 구현
5. 실제 GPU 지원 추가 (현재 시뮬레이션됨)
6. 오류 복구 전략 강화

## 리소스 최적화 실행

Python 구현은 AI/ML 워크로드를 위한 리소스 최적화 실행을 전문으로 합니다:

### 성능 이점
- **CPU 작업**: 병렬 텍스트 처리를 위한 멀티프로세싱 지원
- **GPU 작업**: 시뮬레이션된 GPU 스케줄링 (실제 GPU 통합 준비)
- **IO 작업**: 높은 동시성 I/O 작업을 위한 네이티브 async/await

### AI/ML 통합
- **인기 라이브러리**: TensorFlow, PyTorch, Transformers와의 쉬운 통합
- **LangChain 지원**: LangChain 워크플로우에 대한 네이티브 지원
- **벡터 데이터베이스**: ChromaDB, Pinecone, Weaviate와의 직접 통합
- **모델 서빙**: ONNX, TorchServe, TensorFlow Serving 지원

### 개발 이점
- **빠른 프로토타이핑**: 대화형 개발로 빠른 반복
- **타입 힌트**: 전체 타입 주석이 있는 현대적인 Python
- **비동기 네이티브**: 최대 동시성을 위한 asyncio 기반
- **테스트**: pytest를 사용한 포괄적인 테스트 프레임워크

### 배포 유연성
- **컨테이너화**: 경량 Docker 이미지
- **클라우드 함수**: AWS Lambda, Google Cloud Functions와 호환
- **Kubernetes**: 최소 리소스 요구사항으로 쉬운 배포
- **서버리스**: 서버리스 Temporal 배포와 작동