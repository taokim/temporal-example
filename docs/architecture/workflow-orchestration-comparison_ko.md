# 워크플로우 오케스트레이션 비교

이 문서는 전통적인 분산 시스템부터 현대적인 AI 네이티브 솔루션까지 AI 파이프라인을 위한 다양한 워크플로우 오케스트레이션 접근 방식을 비교합니다.

## 전통적인 분산 시스템 워크플로우

### Temporal

#### Temporal이 AI/ML 및 임베딩 워크로드에 탁월한 이유

**1. 리소스 집약적 작업을 위한 내구성**
- 임베딩 생성은 실패하기 쉬움 (API 제한, 타임아웃, 모델 오류)
- Temporal은 정확한 워크플로우 상태를 보존하고 실패 지점에서 재개
- 대규모 임베딩 배치에서 진행 상황 손실 없음 - 비용이 많이 드는 작업에 중요
- 지수 백오프를 통한 자동 재시도로 일시적 문제 처리

**2. 외부 종속성의 자연스러운 처리**
- API 속도 제한을 위한 내장 패턴 (OpenAI, Cohere, Anthropic)
- 실패하는 모델 서비스를 위한 서킷 브레이커
- 클라우드 API와 자체 호스팅 모델 모두와의 원활한 통합
- 불안정한 서드파티 서비스에 완벽한 액티비티 재시도

**3. 관찰 가능한 AI 파이프라인 실행**
- 임베딩 생성 프로세스의 완전한 감사 추적
- 각 임베딩을 생성한 모델 버전 추적
- 재시도 패턴과 실패 이유 모니터링
- 규정 준수 및 디버깅을 위한 데이터 계보
- 워크플로우 실행별 비용 추적

**4. Human-in-the-Loop 기능**
- 임베딩 품질 검토를 위한 워크플로우 일시 중지
- 처리 전 임베딩 전략 승인
- 엣지 케이스에 대한 수동 개입
- 외부 상호작용을 위한 시그널과 쿼리

### Temporal

**아키텍처 개요:**
- **분리된 설계**: 워크플로우 정의가 Temporal이 아닌 애플리케이션 코드에 존재
- **워커 모델**: 워커가 Temporal 작업 큐에서 작업을 폴링 (풀 기반, 푸시 아님)
- **수평적 확장성**: 부하 증가 시 더 많은 워커 추가
- **멀티 테넌트**: 단일 Temporal 클러스터가 여러 애플리케이션/팀 서비스 가능

**Temporal 작동 방식:**
```go
// 1. 워크플로우 정의 (애플리케이션에서)
func DocumentProcessingWorkflow(ctx workflow.Context, input Input) error {
    // 비즈니스 로직
    return workflow.ExecuteActivity(ctx, ProcessDocument, input).Get()
}

// 2. 워커 프로세스 (역시 애플리케이션)
func main() {
    w := worker.New(temporalClient, "document-queue", worker.Options{})
    w.RegisterWorkflow(DocumentProcessingWorkflow)
    w.RegisterActivity(ProcessDocument)
    w.Run(err => log.Fatal(err)) // Temporal에서 작업 폴링
}

// 3. 워크플로우 시작 (모든 서비스에서)
workflowRun, err := temporalClient.ExecuteWorkflow(
    ctx, options, DocumentProcessingWorkflow, input
)
```

**워커 확장 및 프로비저닝:**
- **폴 기반 아키텍처**: 워커가 큐에서 작업을 풀링, 푸시 오버헤드 없음
- **동적 확장**: 큐 깊이와 처리 시간에 따라 워커 확장
- **중앙 병목 현상 없음**: 각 워커가 독립적으로 작업 폴링
- **우아한 성능 저하**: 일부 워커가 실패해도 시스템 계속 작동

**확장 결정을 위한 메트릭:**
```yaml
주요 Temporal 메트릭:
  - workflow_task_schedule_to_start_latency: 작업이 큐에서 대기하는 시간
  - activity_schedule_to_start_latency: 액티비티가 워커를 기다리는 시간
  - workflow_task_execution_latency: 실제 처리 시간
  - sticky_cache_hit: 워커 캐시 효율성
  - task_queue_depth: 큐의 대기 중인 작업
```

**Kubernetes 통합:**
```yaml
# Temporal 워커를 위한 HPA
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: temporal-worker-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: temporal-worker
  minReplicas: 2
  maxReplicas: 100
  metrics:
  - type: External
    external:
      metric:
        name: temporal_task_queue_depth
        selector:
          matchLabels:
            task_queue: "document-processing"
      target:
        type: AverageValue
        averageValue: "30"  # 워커당 작업이 30개 이상이면 확장
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

**워커 프로비저닝 전략:**
1. **큐 기반 확장**: 작업 큐 깊이 모니터링, 백로그 증가 시 확장
2. **지연 시간 기반 확장**: schedule-to-start 지연 시간 증가 시 확장
3. **예측 확장**: 과거 패턴을 사용하여 예상 부하에 대해 사전 확장
4. **멀티 큐 워커**: 단일 워커가 여러 작업 큐 처리 가능

**강점:**
- **진정한 수평적 확장성**: 중앙 스케줄러 병목 현상 없음
- **애플리케이션 소유 로직**: 워크플로우 코드가 애플리케이션과 함께 존재
- **클라우드 네이티브**: Kubernetes와 자동 확장을 위한 설계
- **멀티 리전**: 여러 리전에서 워커 실행 가능
- **리소스 격리**: 다른 워크플로우가 전용 워커 풀 가질 수 있음

**최적 사용 사례:**
- 대규모 분산 시스템 (수백만 개의 워크플로우)
- 장시간 실행 워크플로우 (몇 시간에서 몇 달)
- 복잡한 상태 관리 요구사항
- 감사 추적이 필요한 미션 크리티컬 프로세스
- 워크플로우 로직을 소유하고자 하는 팀
- 마이크로서비스 아키텍처
- **벡터 임베딩 생성 파이프라인**
- **외부 API 종속성이 있는 AI/ML 워크플로우**
- **Human-in-the-loop 승인이 필요한 워크플로우**

### Apache Airflow

**강점:**
- **스케줄링**: 풍부한 달력 의미론을 갖춘 Cron 기반 스케줄링
- **UI**: 모니터링 및 관리를 위한 포괄적인 웹 UI
- **생태계**: 방대한 사전 빌드 오퍼레이터 라이브러리
- **Python 네이티브**: 데이터 과학 팀에 자연스러운 적합성
- **동적 DAG**: 프로그래밍 방식으로 DAG 생성 가능

**확장성 문제:**
- **스케줄러 병목 현상**: 단일 스케줄러가 대규모에서 병목 현상이 될 수 있음
- **데이터베이스 압력**: 모든 작업 상태가 중앙 데이터베이스에 저장됨 (PostgreSQL/MySQL)
- **작업 분배**: 현대 시스템에 비해 제한된 작업 분배 메커니즘
- **리소스 관리**: 부하에 따라 워커를 동적으로 확장하기 어려움
- **성능 저하**: 대규모 DAG 수에서 UI와 스케줄러 성능 저하
- **상태 관리**: 높은 처리량에서 작업 상태 업데이트가 데이터베이스를 압도할 수 있음

**최적 사용 사례:**
- 배치 처리 및 ETL 파이프라인 (중간 규모)
- 예약된 데이터 워크플로우
- 분석 및 ML 훈련 파이프라인
- 강력한 Python 전문 지식을 가진 팀
- 전통적인 데이터 엔지니어링 워크로드 (< 1000개의 동시 작업)

### 주요 아키텍처 차이점

```yaml
Temporal:
  아키텍처: 분리된, 풀 기반 워커 모델
  워크플로우 위치: 애플리케이션 코드
  확장: 워커 풀을 통한 수평 확장
  병목 현상: 없음 (워커가 독립적으로 확장)
  상태 저장: 분산, 이벤트 소싱
  워커 모델: 폴 기반, 자동 재시도
  멀티 테넌시: 네이티브 지원
  
Airflow:
  아키텍처: 중앙 집중식 스케줄러
  워크플로우 위치: Airflow DAG 저장소
  확장: 수직 스케줄러, 수평 워커
  병목 현상: 스케줄러와 데이터베이스
  상태 저장: 중앙 집중식 데이터베이스
  워커 모델: 푸시 기반 작업 분배
  멀티 테넌시: 제한적
```

### 확장성 비교

| 측면 | Temporal | Airflow |
|--------|----------|---------|
| **최대 동시 워크플로우** | 수백만 | 수천 |
| **작업 분배** | 풀 기반 (워커 폴링) | 푸시 기반 (스케줄러 할당) |
| **확장 병목 현상** | 없음 (더 많은 워커 추가) | 스케줄러 + 데이터베이스 |
| **워커 자동 확장** | 네이티브 K8s HPA 지원 | 구현이 복잡함 |
| **멀티 리전** | 내장 지원 | 사용자 정의 설정 필요 |
| **리소스 격리** | 작업 큐별 | 글로벌 워커 풀 |

### 확장성이 중요한 경우

**Temporal 선택 시기:**
- 매일 수백만 개의 문서 처리
- 예측할 수 없는 트래픽 급증
- 멀티 리전 배포
- 진정한 수평 확장 필요
- 마이크로서비스 아키텍처
- **대규모 데이터셋을 위한 임베딩 생성**
- **RAG 파이프라인 오케스트레이션**
- **재시도가 있는 모델 추론 워크플로우**

**Airflow 선택 시기:**
- 예측 가능한 배치 워크로드
- 소규모 운영 (<1000개의 동시 작업)
- 간단한 ETL 파이프라인
- 스케줄러 병목 현상이 허용 가능한 경우

## 현대 AI 네이티브 접근법

### LangChain

**아키텍처:**
```python
# AI 인식 구성 요소를 사용한 구성 가능한 체인
chain = (
    DocumentLoader()
    | TextSplitter(chunk_size=adaptive)
    | EmbeddingGenerator(model="openai")
    | VectorStore(type="pinecone")
    | ConditionalRouter(
        condition=lambda x: x.metadata["type"],
        routes={
            "legal": legal_processing_chain,
            "technical": technical_processing_chain,
        }
    )
)
```

**강점:**
- **AI 우선 설계**: LLM 애플리케이션을 위해 특별히 구축됨
- **구성 가능성**: 플러그 앤 플레이 구성 요소
- **유연성**: 쉬운 실험과 반복
- **통합**: AI 서비스를 위한 사전 빌드 커넥터

**제한사항:**
- **신뢰성**: Temporal에 비해 제한된 내결함성
- **규모**: 대규모 병렬 처리를 위해 설계되지 않음
- **상태 관리**: 분산 시스템에 비해 기본적임

### LlamaIndex

**강점:**
- **데이터 커넥터**: 풍부한 데이터 로더 세트
- **인덱스 구조**: 다양한 인덱싱 전략
- **쿼리 엔진**: 정교한 검색 방법
- **최적화**: RAG 성능을 위해 구축됨

### Model Context Protocol (MCP)

**신흥 표준:**
```yaml
MCP 서버:
  - AI 기능을 서비스로 노출
  - 표준화된 도구 인터페이스
  - 동적 기능 검색
  - 언어 중립적 프로토콜
```

## 비교 매트릭스

| 기능 | Temporal | Airflow | LangChain | LlamaIndex |
|---------|----------|---------|-----------|------------|
| 신뢰성 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐ |
| 규모 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ |
| AI 통합 | ⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 학습 곡선 | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| 모니터링 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐ |
| 비용 | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| 장시간 실행 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐ | ⭐ |
| Human-in-Loop | ⭐⭐⭐⭐⭐ | ⭐ | ⭐ | ⭐ |
| 임베딩 워크플로우 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |

## 각각 사용 시기

자세한 선택 기준은 [AI 워크플로우 선택 가이드](./ai-workflow-selection-guide.md)를 참조하세요.