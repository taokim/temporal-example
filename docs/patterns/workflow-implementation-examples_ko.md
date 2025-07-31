# 워크플로우 구현 예제

이 문서는 문서 처리 RAG 파이프라인을 위한 Java와 Go의 상세한 워크플로우 및 액티비티 구현을 제공합니다.

## 워크플로우 구현

### Go 구현

```go
package workflows

import (
    "fmt"
    "time"
    
    "go.temporal.io/sdk/workflow"
    "github.com/google/uuid"
    
    "github.com/yourproject/activities"
    "github.com/yourproject/models"
)

// DocumentProcessingWorkflow CSV에서 문서를 처리하고 RAG 구축
func DocumentProcessingWorkflow(ctx workflow.Context, input models.ProcessingInput) (*models.ProcessingResult, error) {
    logger := workflow.GetLogger(ctx)
    logger.Info("문서 처리 워크플로우 시작", "csvPath", input.CSVPath)
    
    // 고유 처리 ID 생성
    processingID := uuid.New().String()
    
    // 액티비티 옵션 구성
    ao := workflow.ActivityOptions{
        TaskQueue:           "document-processing",
        StartToCloseTimeout: 30 * time.Minute,
        RetryPolicy: &temporal.RetryPolicy{
            MaximumAttempts:        3,
            InitialInterval:        time.Second,
            MaximumInterval:        100 * time.Second,
            BackoffCoefficient:     2,
            NonRetryableErrorTypes: []string{"InvalidInputError"},
        },
    }
    ctx = workflow.WithActivityOptions(ctx, ao)
    
    // 단계 1: CSV 파싱
    var documents []models.Document
    err := workflow.ExecuteActivity(ctx, activities.ParseCSVActivity, input.CSVPath).Get(ctx, &documents)
    if err != nil {
        return nil, fmt.Errorf("CSV 파싱 실패: %w", err)
    }
    
    logger.Info("CSV에서 문서 파싱 완료", "count", len(documents))
    
    // 단계 2: 병렬 배치로 문서 처리
    batchSize := 100
    batches := createBatches(documents, batchSize)
    
    // 병렬 실행을 위해 workflow.Go 사용
    selector := workflow.NewSelector(ctx)
    var futures []workflow.Future
    
    for i, batch := range batches {
        batchID := fmt.Sprintf("%s-batch-%d", processingID, i)
        future := workflow.ExecuteActivity(ctx, activities.ProcessBatchActivity, 
            models.BatchInput{
                BatchID:   batchID,
                Documents: batch,
                Options:   input.Options,
            })
        futures = append(futures, future)
        
        // 모니터링을 위해 셀렉터에 추가
        selector.AddFuture(future, func(f workflow.Future) {
            logger.Info("배치 완료", "batchID", batchID)
        })
    }
    
    // 모든 배치 완료 대기
    var allResults []models.ProcessedDocument
    failedDocs := make([]models.FailedDocument, 0)
    
    for _, future := range futures {
        var batchResult models.BatchResult
        if err := future.Get(ctx, &batchResult); err != nil {
            logger.Error("배치 처리 실패", "error", err)
            // 다른 배치는 계속 처리
            continue
        }
        allResults = append(allResults, batchResult.Processed...)
        failedDocs = append(failedDocs, batchResult.Failed...)
    }
    
    // 단계 3: 벡터 데이터베이스에 저장
    var storageResult models.StorageResult
    err = workflow.ExecuteActivity(ctx, activities.StoreInVectorDBActivity, 
        models.StorageInput{
            ProcessingID: processingID,
            Documents:    allResults,
        }).Get(ctx, &storageResult)
    
    if err != nil {
        logger.Error("벡터 DB 저장 실패", "error", err)
    }
    
    // 단계 4: 요약 보고서 생성
    var summaryLocation string
    err = workflow.ExecuteActivity(ctx, activities.GenerateSummaryActivity,
        models.SummaryInput{
            ProcessingID:   processingID,
            ProcessedCount: len(allResults),
            FailedCount:    len(failedDocs),
            Documents:      allResults,
        }).Get(ctx, &summaryLocation)
    
    if err != nil {
        logger.Error("요약 생성 실패", "error", err)
    }
    
    result := &models.ProcessingResult{
        ProcessingID:     processingID,
        ProcessedCount:   len(allResults),
        FailedCount:      len(failedDocs),
        VectorDBIndexIDs: storageResult.IndexIDs,
        S3Locations:      storageResult.S3Paths,
        SummaryLocation:  summaryLocation,
        CompletedAt:      workflow.Now(ctx),
    }
    
    logger.Info("워크플로우 완료", 
        "processingID", processingID,
        "processed", result.ProcessedCount,
        "failed", result.FailedCount)
    
    return result, nil
}

// 배치 생성을 위한 헬퍼 함수
func createBatches(docs []models.Document, size int) [][]models.Document {
    var batches [][]models.Document
    for i := 0; i < len(docs); i += size {
        end := i + size
        if end > len(docs) {
            end = len(docs)
        }
        batches = append(batches, docs[i:end])
    }
    return batches
}
```

### Java 구현

```java
package com.example.workflows;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.*;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.example.activities.*;
import com.example.models.*;

@WorkflowInterface
public interface DocumentProcessingWorkflow {
    @WorkflowMethod
    ProcessingResult processDocuments(ProcessingInput input);
    
    @SignalMethod
    void pauseProcessing();
    
    @SignalMethod
    void resumeProcessing();
    
    @QueryMethod
    ProcessingStatus getStatus();
}

public class DocumentProcessingWorkflowImpl implements DocumentProcessingWorkflow {
    
    private static final Logger logger = Workflow.getLogger(DocumentProcessingWorkflowImpl.class);
    
    private final DocumentActivities activities;
    private boolean isPaused = false;
    private ProcessingStatus currentStatus;
    private String processingId;
    
    public DocumentProcessingWorkflowImpl() {
        // 액티비티 옵션 구성
        ActivityOptions options = ActivityOptions.newBuilder()
                .setTaskQueue("document-processing")
                .setStartToCloseTimeout(Duration.ofMinutes(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(3)
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofSeconds(100))
                        .setBackoffCoefficient(2.0)
                        .setDoNotRetry("InvalidInputError")
                        .build())
                .build();
        
        this.activities = Workflow.newActivityStub(DocumentActivities.class, options);
        this.processingId = UUID.randomUUID().toString();
    }
    
    @Override
    public ProcessingResult processDocuments(ProcessingInput input) {
        logger.info("CSV를 위한 문서 처리 워크플로우 시작: {}", input.getCsvPath());
        
        updateStatus("PARSING_CSV", 0, 0);
        
        // 단계 1: CSV 파싱
        List<Document> documents = activities.parseCSV(input.getCsvPath());
        logger.info("CSV에서 {} 문서 파싱 완료", documents.size());
        
        updateStatus("PROCESSING_DOCUMENTS", documents.size(), 0);
        
        // 단계 2: 병렬 배치로 문서 처리
        int batchSize = 100;
        List<List<Document>> batches = createBatches(documents, batchSize);
        
        List<Promise<BatchResult>> promises = new ArrayList<>();
        
        for (int i = 0; i < batches.size(); i++) {
            // 일시 정지 상태 확인
            Workflow.await(() -> !isPaused);
            
            String batchId = String.format("%s-batch-%d", processingId, i);
            BatchInput batchInput = BatchInput.builder()
                    .batchId(batchId)
                    .documents(batches.get(i))
                    .options(input.getOptions())
                    .build();
            
            // 비동기로 액티비티 실행
            Promise<BatchResult> promise = Async.function(activities::processBatch, batchInput);
            promises.add(promise);
        }
        
        // 결과 수집
        List<ProcessedDocument> allProcessed = new ArrayList<>();
        List<FailedDocument> allFailed = new ArrayList<>();
        
        for (Promise<BatchResult> promise : promises) {
            try {
                BatchResult result = promise.get();
                allProcessed.addAll(result.getProcessed());
                allFailed.addAll(result.getFailed());
                
                updateStatus("PROCESSING_DOCUMENTS", 
                    documents.size(), 
                    allProcessed.size() + allFailed.size());
                    
            } catch (Exception e) {
                logger.error("배치 처리 실패", e);
                // 다른 배치는 계속 처리
            }
        }
        
        updateStatus("STORING_RESULTS", documents.size(), documents.size());
        
        // 단계 3: 벡터 데이터베이스에 저장
        StorageResult storageResult = null;
        try {
            StorageInput storageInput = StorageInput.builder()
                    .processingId(processingId)
                    .documents(allProcessed)
                    .build();
            
            storageResult = activities.storeInVectorDB(storageInput);
        } catch (Exception e) {
            logger.error("벡터 DB 저장 실패", e);
        }
        
        // 단계 4: 요약 보고서 생성
        String summaryLocation = null;
        try {
            SummaryInput summaryInput = SummaryInput.builder()
                    .processingId(processingId)
                    .processedCount(allProcessed.size())
                    .failedCount(allFailed.size())
                    .documents(allProcessed)
                    .build();
            
            summaryLocation = activities.generateSummary(summaryInput);
        } catch (Exception e) {
            logger.error("요약 생성 실패", e);
        }
        
        ProcessingResult result = ProcessingResult.builder()
                .processingId(processingId)
                .processedCount(allProcessed.size())
                .failedCount(allFailed.size())
                .vectorDBIndexIds(storageResult != null ? storageResult.getIndexIds() : Collections.emptyList())
                .s3Locations(storageResult != null ? storageResult.getS3Paths() : Collections.emptyMap())
                .summaryLocation(summaryLocation)
                .completedAt(Instant.now())
                .build();
        
        updateStatus("COMPLETED", documents.size(), documents.size());
        
        logger.info("워크플로우 완료 - ProcessingID: {}, 처리됨: {}, 실패: {}", 
                processingId, result.getProcessedCount(), result.getFailedCount());
        
        return result;
    }
    
    @Override
    public void pauseProcessing() {
        logger.info("처리 일시 정지됨");
        isPaused = true;
    }
    
    @Override
    public void resumeProcessing() {
        logger.info("처리 재개됨");
        isPaused = false;
    }
    
    @Override
    public ProcessingStatus getStatus() {
        return currentStatus;
    }
    
    private void updateStatus(String phase, int total, int processed) {
        currentStatus = ProcessingStatus.builder()
                .processingId(processingId)
                .phase(phase)
                .totalDocuments(total)
                .processedDocuments(processed)
                .isPaused(isPaused)
                .build();
    }
    
    private List<List<Document>> createBatches(List<Document> documents, int batchSize) {
        List<List<Document>> batches = new ArrayList<>();
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            batches.add(documents.subList(i, end));
        }
        return batches;
    }
}
```

## 액티비티 구현

### Go 액티비티

```go
package activities

import (
    "context"
    "encoding/csv"
    "fmt"
    "os"
    "sync"
    "time"
    
    "go.temporal.io/sdk/activity"
    "github.com/aws/aws-sdk-go/aws"
    "github.com/aws/aws-sdk-go/service/s3"
    
    "github.com/yourproject/models"
    "github.com/yourproject/services"
)

type Activities struct {
    s3Client        *s3.S3
    vectorDB        services.VectorDB
    openAIClient    services.OpenAIClient
    documentParser  services.DocumentParser
}

// ParseCSVActivity CSV 파일 읽기 및 파싱
func (a *Activities) ParseCSVActivity(ctx context.Context, csvPath string) ([]models.Document, error) {
    logger := activity.GetLogger(ctx)
    logger.Info("CSV 파일 파싱", "path", csvPath)
    
    file, err := os.Open(csvPath)
    if err != nil {
        return nil, fmt.Errorf("CSV 열기 실패: %w", err)
    }
    defer file.Close()
    
    reader := csv.NewReader(file)
    records, err := reader.ReadAll()
    if err != nil {
        return nil, fmt.Errorf("CSV 읽기 실패: %w", err)
    }
    
    documents := make([]models.Document, 0, len(records)-1)
    for i, record := range records[1:] { // 헤더 건너뛰기
        if len(record) < 4 {
            logger.Warn("유효하지 않은 레코드 건너뛰기", "line", i+2)
            continue
        }
        
        doc := models.Document{
            ID:          fmt.Sprintf("doc-%d", i),
            URL:         record[0],
            Name:        record[1],
            Type:        record[2],
            MetadataRaw: record[3],
        }
        documents = append(documents, doc)
        
        // 진행 상황 보고
        if i%100 == 0 {
            activity.RecordHeartbeat(ctx, fmt.Sprintf("%d 문서 파싱 완료", i))
        }
    }
    
    logger.Info("CSV 파싱 완료", "documentCount", len(documents))
    return documents, nil
}

// ProcessBatchActivity 문서 배치 처리
func (a *Activities) ProcessBatchActivity(ctx context.Context, input models.BatchInput) (*models.BatchResult, error) {
    logger := activity.GetLogger(ctx)
    logger.Info("배치 처리", "batchId", input.BatchID, "size", len(input.Documents))
    
    processed := make([]models.ProcessedDocument, 0, len(input.Documents))
    failed := make([]models.FailedDocument, 0)
    
    // 병렬 처리를 위한 goroutines 사용
    var wg sync.WaitGroup
    resultChan := make(chan models.ProcessResult, len(input.Documents))
    
    // 워커 풀
    workerCount := 10
    semaphore := make(chan struct{}, workerCount)
    
    for _, doc := range input.Documents {
        wg.Add(1)
        go func(document models.Document) {
            defer wg.Done()
            
            // 세마포어 획득
            semaphore <- struct{}{}
            defer func() { <-semaphore }()
            
            result := a.processDocument(ctx, document, input.Options)
            resultChan <- result
        }(doc)
    }
    
    // 모든 goroutines 완료 시 채널 닫기
    go func() {
        wg.Wait()
        close(resultChan)
    }()
    
    // 결과 수집
    for result := range resultChan {
        if result.Error != nil {
            failed = append(failed, models.FailedDocument{
                Document: result.Document,
                Error:    result.Error.Error(),
                FailedAt: time.Now(),
            })
        } else {
            processed = append(processed, result.Processed)
        }
        
        // 진행 상황 보고
        activity.RecordHeartbeat(ctx, 
            fmt.Sprintf("%d/%d 문서 처리 완료", 
                len(processed)+len(failed), len(input.Documents)))
    }
    
    logger.Info("배치 처리 완료", 
        "batchId", input.BatchID, 
        "processed", len(processed), 
        "failed", len(failed))
    
    return &models.BatchResult{
        BatchID:   input.BatchID,
        Processed: processed,
        Failed:    failed,
    }, nil
}

func (a *Activities) processDocument(ctx context.Context, doc models.Document, options models.ProcessingOptions) models.ProcessResult {
    logger := activity.GetLogger(ctx)
    
    // 문서 다운로드
    content, err := a.downloadDocument(ctx, doc)
    if err != nil {
        logger.Error("문서 다운로드 실패", "docId", doc.ID, "error", err)
        return models.ProcessResult{Document: doc, Error: err}
    }
    
    // 문서 유형에 따른 텍스트 추출
    text, err := a.documentParser.ExtractText(ctx, content, doc.Type)
    if err != nil {
        logger.Error("텍스트 추출 실패", "docId", doc.ID, "error", err)
        return models.ProcessResult{Document: doc, Error: err}
    }
    
    // 임베딩 생성
    embedding, err := a.openAIClient.CreateEmbedding(ctx, text)
    if err != nil {
        logger.Error("임베딩 생성 실패", "docId", doc.ID, "error", err)
        return models.ProcessResult{Document: doc, Error: err}
    }
    
    // 요약 생성
    summary, err := a.openAIClient.Summarize(ctx, text, options.SummaryLength)
    if err != nil {
        logger.Error("요약 생성 실패", "docId", doc.ID, "error", err)
        return models.ProcessResult{Document: doc, Error: err}
    }
    
    return models.ProcessResult{
        Processed: models.ProcessedDocument{
            Document:    doc,
            Text:        text,
            Embedding:   embedding,
            Summary:     summary,
            ProcessedAt: time.Now(),
        },
    }
}
```

### Java 액티비티

```java
package com.example.activities;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.opencsv.CSVReader;
import java.io.FileReader;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.example.models.*;
import com.example.services.*;

@ActivityInterface
public interface DocumentActivities {
    @ActivityMethod
    List<Document> parseCSV(String csvPath);
    
    @ActivityMethod
    BatchResult processBatch(BatchInput input);
    
    @ActivityMethod
    StorageResult storeInVectorDB(StorageInput input);
    
    @ActivityMethod
    String generateSummary(SummaryInput input);
}

@Component
public class DocumentActivitiesImpl implements DocumentActivities {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentActivitiesImpl.class);
    
    private final S3Service s3Service;
    private final VectorDBService vectorDB;
    private final OpenAIService openAIService;
    private final DocumentParser documentParser;
    private final ExecutorService executorService;
    
    public DocumentActivitiesImpl(S3Service s3Service, 
                                  VectorDBService vectorDB,
                                  OpenAIService openAIService,
                                  DocumentParser documentParser) {
        this.s3Service = s3Service;
        this.vectorDB = vectorDB;
        this.openAIService = openAIService;
        this.documentParser = documentParser;
        this.executorService = Executors.newFixedThreadPool(10);
    }
    
    @Override
    public List<Document> parseCSV(String csvPath) {
        logger.info("CSV 파일 파싱: {}", csvPath);
        List<Document> documents = new ArrayList<>();
        
        try (CSVReader reader = new CSVReader(new FileReader(csvPath))) {
            String[] header = reader.readNext(); // 헤더 건너뛰기
            String[] line;
            int index = 0;
            
            while ((line = reader.readNext()) != null) {
                if (line.length < 4) {
                    logger.warn("줄 {}에서 유효하지 않은 레코드 건너뛰기", index + 2);
                    continue;
                }
                
                Document doc = Document.builder()
                        .id("doc-" + index)
                        .url(line[0])
                        .name(line[1])
                        .type(line[2])
                        .metadataRaw(line[3])
                        .build();
                
                documents.add(doc);
                
                // 진행 상황 보고
                if (index % 100 == 0) {
                    Activity.getExecutionContext()
                            .heartbeat(String.format("%d 문서 파싱 완료", index));
                }
                
                index++;
            }
        } catch (Exception e) {
            throw new RuntimeException("CSV 파싱 실패", e);
        }
        
        logger.info("CSV 파싱 완료. 문서 수: {}", documents.size());
        return documents;
    }
    
    @Override
    public BatchResult processBatch(BatchInput input) {
        logger.info("{} 배치를 {} 문서로 처리 중", 
                input.getBatchId(), input.getDocuments().size());
        
        List<ProcessedDocument> processed = Collections.synchronizedList(new ArrayList<>());
        List<FailedDocument> failed = Collections.synchronizedList(new ArrayList<>());
        
        // 병렬로 문서 처리
        List<CompletableFuture<ProcessResult>> futures = input.getDocuments().stream()
                .map(doc -> CompletableFuture.supplyAsync(() -> 
                        processDocument(doc, input.getOptions()), executorService))
                .collect(Collectors.toList());
        
        // 결과 수집
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        for (CompletableFuture<ProcessResult> future : futures) {
            try {
                ProcessResult result = future.get();
                if (result.getError() != null) {
                    failed.add(FailedDocument.builder()
                            .document(result.getDocument())
                            .error(result.getError().getMessage())
                            .failedAt(Instant.now())
                            .build());
                } else {
                    processed.add(result.getProcessed());
                }
                
                // 진행 상황 보고
                Activity.getExecutionContext().heartbeat(
                        String.format("%d/%d 문서 처리 완료", 
                                processed.size() + failed.size(), 
                                input.getDocuments().size()));
                
            } catch (Exception e) {
                logger.error("문서 처리 실패", e);
            }
        }
        
        logger.info("배치 {} 완료. 처리됨: {}, 실패: {}", 
                input.getBatchId(), processed.size(), failed.size());
        
        return BatchResult.builder()
                .batchId(input.getBatchId())
                .processed(processed)
                .failed(failed)
                .build();
    }
    
    private ProcessResult processDocument(Document doc, ProcessingOptions options) {
        try {
            // 문서 다운로드
            byte[] content = downloadDocument(doc);
            
            // 문서 유형에 따른 텍스트 추출
            String text = documentParser.extractText(content, doc.getType());
            
            // 임베딩 생성
            float[] embedding = openAIService.createEmbedding(text);
            
            // 요약 생성
            String summary = openAIService.summarize(text, options.getSummaryLength());
            
            return ProcessResult.builder()
                    .processed(ProcessedDocument.builder()
                            .document(doc)
                            .text(text)
                            .embedding(embedding)
                            .summary(summary)
                            .processedAt(Instant.now())
                            .build())
                    .build();
                    
        } catch (Exception e) {
            logger.error("문서 {} 처리 실패", doc.getId(), e);
            return ProcessResult.builder()
                    .document(doc)
                    .error(e)
                    .build();
        }
    }
    
    @Override
    public StorageResult storeInVectorDB(StorageInput input) {
        logger.info("벡터 DB에 {} 문서 저장 중", input.getDocuments().size());
        
        List<String> indexIds = new ArrayList<>();
        Map<String, String> s3Paths = new HashMap<>();
        
        for (ProcessedDocument doc : input.getDocuments()) {
            try {
                // 벡터 DB에 저장
                String indexId = vectorDB.upsert(
                        doc.getDocument().getId(),
                        doc.getEmbedding(),
                        Map.of(
                                "processingId", input.getProcessingId(),
                                "documentName", doc.getDocument().getName(),
                                "summary", doc.getSummary()
                        )
                );
                indexIds.add(indexId);
                
                // S3에 문서 콘텐츠 저장
                String s3Path = String.format("documents/%s/%s/content.json", 
                        input.getProcessingId(), doc.getDocument().getId());
                
                s3Service.putObject(s3Path, doc);
                s3Paths.put(doc.getDocument().getId(), s3Path);
                
            } catch (Exception e) {
                logger.error("문서 {} 저장 실패", doc.getDocument().getId(), e);
            }
        }
        
        logger.info("저장 완료. 인덱싱됨: {}, S3에 저장됨: {}", 
                indexIds.size(), s3Paths.size());
        
        return StorageResult.builder()
                .indexIds(indexIds)
                .s3Paths(s3Paths)
                .build();
    }
    
    @Override
    public String generateSummary(SummaryInput input) {
        logger.info("처리 {}를 위한 요약 생성 중", input.getProcessingId());
        
        Map<String, Object> summary = Map.of(
                "processingId", input.getProcessingId(),
                "processedCount", input.getProcessedCount(),
                "failedCount", input.getFailedCount(),
                "timestamp", Instant.now(),
                "topSummaries", input.getDocuments().stream()
                        .limit(10)
                        .map(ProcessedDocument::getSummary)
                        .collect(Collectors.toList())
        );
        
        String s3Path = String.format("processing-runs/%s/summary.json", 
                input.getProcessingId());
        
        s3Service.putObject(s3Path, summary);
        
        logger.info("요약이 생성되어 {}에 저장됨", s3Path);
        return s3Path;
    }
    
    private byte[] downloadDocument(Document doc) throws Exception {
        if (doc.getUrl().startsWith("s3://")) {
            return s3Service.getObject(doc.getUrl());
        } else if (doc.getUrl().startsWith("http")) {
            // HTTP 다운로드 구현
            throw new UnsupportedOperationException("HTTP 다운로드가 구현되지 않음");
        } else {
            // 로컬 파일
            return java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(doc.getUrl()));
        }
    }
}
```

## 워커 구현

### Go 워커

```go
package main

import (
    "log"
    "os"
    
    "go.temporal.io/sdk/client"
    "go.temporal.io/sdk/worker"
    
    "github.com/yourproject/activities"
    "github.com/yourproject/workflows"
    "github.com/yourproject/config"
)

func main() {
    // Temporal 클라이언트 생성
    c, err := client.Dial(client.Options{
        HostPort: os.Getenv("TEMPORAL_HOST_PORT"),
    })
    if err != nil {
        log.Fatalln("Temporal 클라이언트 생성 실패", err)
    }
    defer c.Close()
    
    // 의존성과 함께 액티비티 생성
    activities := activities.NewActivities(
        config.NewS3Client(),
        config.NewVectorDB(),
        config.NewOpenAIClient(),
        config.NewDocumentParser(),
    )
    
    // 워커 생성
    w := worker.New(c, "document-processing", worker.Options{
        MaxConcurrentActivityExecutionSize: 100,
        MaxConcurrentWorkflowTaskExecutionSize: 50,
    })
    
    // 워크플로우 등록
    w.RegisterWorkflow(workflows.DocumentProcessingWorkflow)
    
    // 액티비티 등록
    w.RegisterActivity(activities.ParseCSVActivity)
    w.RegisterActivity(activities.ProcessBatchActivity)
    w.RegisterActivity(activities.StoreInVectorDBActivity)
    w.RegisterActivity(activities.GenerateSummaryActivity)
    
    // 워커 시작
    err = w.Run(worker.InterruptCh())
    if err != nil {
        log.Fatalln("워커 시작 실패", err)
    }
}
```

### Java 워커

```java
package com.example.worker;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import com.example.workflows.DocumentProcessingWorkflowImpl;
import com.example.activities.DocumentActivitiesImpl;

@SpringBootApplication
public class DocumentProcessingWorker {
    
    private static final String TASK_QUEUE = "document-processing";
    
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(DocumentProcessingWorker.class, args);
        
        // Temporal 서비스 스텁 생성
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        
        // 워크플로우 클라이언트 생성
        WorkflowClient client = WorkflowClient.newInstance(service);
        
        // 워커 팩토리 생성
        WorkerFactory factory = WorkerFactory.newInstance(client);
        
        // 워커 옵션 생성
        WorkerOptions workerOptions = WorkerOptions.newBuilder()
                .setMaxConcurrentActivityExecutionSize(100)
                .setMaxConcurrentWorkflowTaskExecutionSize(50)
                .build();
        
        // 워커 생성
        Worker worker = factory.newWorker(TASK_QUEUE, workerOptions);
        
        // 워크플로우 등록
        worker.registerWorkflowImplementationTypes(DocumentProcessingWorkflowImpl.class);
        
        // Spring 의존성과 함께 액티비티 등록
        DocumentActivitiesImpl activities = context.getBean(DocumentActivitiesImpl.class);
        worker.registerActivitiesImplementations(activities);
        
        // 워커 팩토리 시작
        factory.start();
        
        System.out.println("작업 큐에서 워커 시작됨: " + TASK_QUEUE);
    }
}
```