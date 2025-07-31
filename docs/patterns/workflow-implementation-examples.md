# Workflow Implementation Examples

This document provides detailed workflow and activity implementations in both Java and Go for the document processing RAG pipeline.

## Workflow Implementations

### Go Implementation

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

// DocumentProcessingWorkflow processes documents from CSV and builds RAG
func DocumentProcessingWorkflow(ctx workflow.Context, input models.ProcessingInput) (*models.ProcessingResult, error) {
    logger := workflow.GetLogger(ctx)
    logger.Info("Starting document processing workflow", "csvPath", input.CSVPath)
    
    // Generate unique processing ID
    processingID := uuid.New().String()
    
    // Configure activity options
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
    
    // Step 1: Parse CSV
    var documents []models.Document
    err := workflow.ExecuteActivity(ctx, activities.ParseCSVActivity, input.CSVPath).Get(ctx, &documents)
    if err != nil {
        return nil, fmt.Errorf("failed to parse CSV: %w", err)
    }
    
    logger.Info("Parsed documents from CSV", "count", len(documents))
    
    // Step 2: Process documents in parallel batches
    batchSize := 100
    batches := createBatches(documents, batchSize)
    
    // Use workflow.Go for parallel execution
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
        
        // Add to selector for monitoring
        selector.AddFuture(future, func(f workflow.Future) {
            logger.Info("Batch completed", "batchID", batchID)
        })
    }
    
    // Wait for all batches to complete
    var allResults []models.ProcessedDocument
    failedDocs := make([]models.FailedDocument, 0)
    
    for _, future := range futures {
        var batchResult models.BatchResult
        if err := future.Get(ctx, &batchResult); err != nil {
            logger.Error("Batch processing failed", "error", err)
            // Continue processing other batches
            continue
        }
        allResults = append(allResults, batchResult.Processed...)
        failedDocs = append(failedDocs, batchResult.Failed...)
    }
    
    // Step 3: Store in vector database
    var storageResult models.StorageResult
    err = workflow.ExecuteActivity(ctx, activities.StoreInVectorDBActivity, 
        models.StorageInput{
            ProcessingID: processingID,
            Documents:    allResults,
        }).Get(ctx, &storageResult)
    
    if err != nil {
        logger.Error("Failed to store in vector DB", "error", err)
    }
    
    // Step 4: Generate summary report
    var summaryLocation string
    err = workflow.ExecuteActivity(ctx, activities.GenerateSummaryActivity,
        models.SummaryInput{
            ProcessingID:   processingID,
            ProcessedCount: len(allResults),
            FailedCount:    len(failedDocs),
            Documents:      allResults,
        }).Get(ctx, &summaryLocation)
    
    if err != nil {
        logger.Error("Failed to generate summary", "error", err)
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
    
    logger.Info("Workflow completed", 
        "processingID", processingID,
        "processed", result.ProcessedCount,
        "failed", result.FailedCount)
    
    return result, nil
}

// Helper function to create batches
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

### Java Implementation

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
        // Configure activity options
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
        logger.info("Starting document processing workflow for CSV: {}", input.getCsvPath());
        
        updateStatus("PARSING_CSV", 0, 0);
        
        // Step 1: Parse CSV
        List<Document> documents = activities.parseCSV(input.getCsvPath());
        logger.info("Parsed {} documents from CSV", documents.size());
        
        updateStatus("PROCESSING_DOCUMENTS", documents.size(), 0);
        
        // Step 2: Process documents in parallel batches
        int batchSize = 100;
        List<List<Document>> batches = createBatches(documents, batchSize);
        
        List<Promise<BatchResult>> promises = new ArrayList<>();
        
        for (int i = 0; i < batches.size(); i++) {
            // Check if paused
            Workflow.await(() -> !isPaused);
            
            String batchId = String.format("%s-batch-%d", processingId, i);
            BatchInput batchInput = BatchInput.builder()
                    .batchId(batchId)
                    .documents(batches.get(i))
                    .options(input.getOptions())
                    .build();
            
            // Execute activity asynchronously
            Promise<BatchResult> promise = Async.function(activities::processBatch, batchInput);
            promises.add(promise);
        }
        
        // Collect results
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
                logger.error("Batch processing failed", e);
                // Continue with other batches
            }
        }
        
        updateStatus("STORING_RESULTS", documents.size(), documents.size());
        
        // Step 3: Store in vector database
        StorageResult storageResult = null;
        try {
            StorageInput storageInput = StorageInput.builder()
                    .processingId(processingId)
                    .documents(allProcessed)
                    .build();
            
            storageResult = activities.storeInVectorDB(storageInput);
        } catch (Exception e) {
            logger.error("Failed to store in vector DB", e);
        }
        
        // Step 4: Generate summary report
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
            logger.error("Failed to generate summary", e);
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
        
        logger.info("Workflow completed - ProcessingID: {}, Processed: {}, Failed: {}", 
                processingId, result.getProcessedCount(), result.getFailedCount());
        
        return result;
    }
    
    @Override
    public void pauseProcessing() {
        logger.info("Processing paused");
        isPaused = true;
    }
    
    @Override
    public void resumeProcessing() {
        logger.info("Processing resumed");
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

## Activity Implementations

### Go Activities

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

// ParseCSVActivity reads and parses CSV file
func (a *Activities) ParseCSVActivity(ctx context.Context, csvPath string) ([]models.Document, error) {
    logger := activity.GetLogger(ctx)
    logger.Info("Parsing CSV file", "path", csvPath)
    
    file, err := os.Open(csvPath)
    if err != nil {
        return nil, fmt.Errorf("failed to open CSV: %w", err)
    }
    defer file.Close()
    
    reader := csv.NewReader(file)
    records, err := reader.ReadAll()
    if err != nil {
        return nil, fmt.Errorf("failed to read CSV: %w", err)
    }
    
    documents := make([]models.Document, 0, len(records)-1)
    for i, record := range records[1:] { // Skip header
        if len(record) < 4 {
            logger.Warn("Skipping invalid record", "line", i+2)
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
        
        // Report progress
        if i%100 == 0 {
            activity.RecordHeartbeat(ctx, fmt.Sprintf("Parsed %d documents", i))
        }
    }
    
    logger.Info("CSV parsing completed", "documentCount", len(documents))
    return documents, nil
}

// ProcessBatchActivity processes a batch of documents
func (a *Activities) ProcessBatchActivity(ctx context.Context, input models.BatchInput) (*models.BatchResult, error) {
    logger := activity.GetLogger(ctx)
    logger.Info("Processing batch", "batchId", input.BatchID, "size", len(input.Documents))
    
    processed := make([]models.ProcessedDocument, 0, len(input.Documents))
    failed := make([]models.FailedDocument, 0)
    
    // Use goroutines for parallel processing
    var wg sync.WaitGroup
    resultChan := make(chan models.ProcessResult, len(input.Documents))
    
    // Worker pool
    workerCount := 10
    semaphore := make(chan struct{}, workerCount)
    
    for _, doc := range input.Documents {
        wg.Add(1)
        go func(document models.Document) {
            defer wg.Done()
            
            // Acquire semaphore
            semaphore <- struct{}{}
            defer func() { <-semaphore }()
            
            result := a.processDocument(ctx, document, input.Options)
            resultChan <- result
        }(doc)
    }
    
    // Close channel when all goroutines complete
    go func() {
        wg.Wait()
        close(resultChan)
    }()
    
    // Collect results
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
        
        // Report progress
        activity.RecordHeartbeat(ctx, 
            fmt.Sprintf("Processed %d/%d documents", 
                len(processed)+len(failed), len(input.Documents)))
    }
    
    logger.Info("Batch processing completed", 
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
    
    // Download document
    content, err := a.downloadDocument(ctx, doc)
    if err != nil {
        logger.Error("Failed to download document", "docId", doc.ID, "error", err)
        return models.ProcessResult{Document: doc, Error: err}
    }
    
    // Extract text based on document type
    text, err := a.documentParser.ExtractText(ctx, content, doc.Type)
    if err != nil {
        logger.Error("Failed to extract text", "docId", doc.ID, "error", err)
        return models.ProcessResult{Document: doc, Error: err}
    }
    
    // Generate embedding
    embedding, err := a.openAIClient.CreateEmbedding(ctx, text)
    if err != nil {
        logger.Error("Failed to generate embedding", "docId", doc.ID, "error", err)
        return models.ProcessResult{Document: doc, Error: err}
    }
    
    // Generate summary
    summary, err := a.openAIClient.Summarize(ctx, text, options.SummaryLength)
    if err != nil {
        logger.Error("Failed to generate summary", "docId", doc.ID, "error", err)
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

### Java Activities

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
        logger.info("Parsing CSV file: {}", csvPath);
        List<Document> documents = new ArrayList<>();
        
        try (CSVReader reader = new CSVReader(new FileReader(csvPath))) {
            String[] header = reader.readNext(); // Skip header
            String[] line;
            int index = 0;
            
            while ((line = reader.readNext()) != null) {
                if (line.length < 4) {
                    logger.warn("Skipping invalid record at line {}", index + 2);
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
                
                // Report progress
                if (index % 100 == 0) {
                    Activity.getExecutionContext()
                            .heartbeat(String.format("Parsed %d documents", index));
                }
                
                index++;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CSV", e);
        }
        
        logger.info("CSV parsing completed. Document count: {}", documents.size());
        return documents;
    }
    
    @Override
    public BatchResult processBatch(BatchInput input) {
        logger.info("Processing batch {} with {} documents", 
                input.getBatchId(), input.getDocuments().size());
        
        List<ProcessedDocument> processed = Collections.synchronizedList(new ArrayList<>());
        List<FailedDocument> failed = Collections.synchronizedList(new ArrayList<>());
        
        // Process documents in parallel
        List<CompletableFuture<ProcessResult>> futures = input.getDocuments().stream()
                .map(doc -> CompletableFuture.supplyAsync(() -> 
                        processDocument(doc, input.getOptions()), executorService))
                .collect(Collectors.toList());
        
        // Collect results
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
                
                // Report progress
                Activity.getExecutionContext().heartbeat(
                        String.format("Processed %d/%d documents", 
                                processed.size() + failed.size(), 
                                input.getDocuments().size()));
                
            } catch (Exception e) {
                logger.error("Failed to process document", e);
            }
        }
        
        logger.info("Batch {} completed. Processed: {}, Failed: {}", 
                input.getBatchId(), processed.size(), failed.size());
        
        return BatchResult.builder()
                .batchId(input.getBatchId())
                .processed(processed)
                .failed(failed)
                .build();
    }
    
    private ProcessResult processDocument(Document doc, ProcessingOptions options) {
        try {
            // Download document
            byte[] content = downloadDocument(doc);
            
            // Extract text based on document type
            String text = documentParser.extractText(content, doc.getType());
            
            // Generate embedding
            float[] embedding = openAIService.createEmbedding(text);
            
            // Generate summary
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
            logger.error("Failed to process document {}", doc.getId(), e);
            return ProcessResult.builder()
                    .document(doc)
                    .error(e)
                    .build();
        }
    }
    
    @Override
    public StorageResult storeInVectorDB(StorageInput input) {
        logger.info("Storing {} documents in vector DB", input.getDocuments().size());
        
        List<String> indexIds = new ArrayList<>();
        Map<String, String> s3Paths = new HashMap<>();
        
        for (ProcessedDocument doc : input.getDocuments()) {
            try {
                // Store in vector DB
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
                
                // Store document content in S3
                String s3Path = String.format("documents/%s/%s/content.json", 
                        input.getProcessingId(), doc.getDocument().getId());
                
                s3Service.putObject(s3Path, doc);
                s3Paths.put(doc.getDocument().getId(), s3Path);
                
            } catch (Exception e) {
                logger.error("Failed to store document {}", doc.getDocument().getId(), e);
            }
        }
        
        logger.info("Storage completed. Indexed: {}, Stored in S3: {}", 
                indexIds.size(), s3Paths.size());
        
        return StorageResult.builder()
                .indexIds(indexIds)
                .s3Paths(s3Paths)
                .build();
    }
    
    @Override
    public String generateSummary(SummaryInput input) {
        logger.info("Generating summary for processing {}", input.getProcessingId());
        
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
        
        logger.info("Summary generated and stored at {}", s3Path);
        return s3Path;
    }
    
    private byte[] downloadDocument(Document doc) throws Exception {
        if (doc.getUrl().startsWith("s3://")) {
            return s3Service.getObject(doc.getUrl());
        } else if (doc.getUrl().startsWith("http")) {
            // HTTP download implementation
            throw new UnsupportedOperationException("HTTP download not implemented");
        } else {
            // Local file
            return java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(doc.getUrl()));
        }
    }
}
```

## Worker Implementation

### Go Worker

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
    // Create Temporal client
    c, err := client.Dial(client.Options{
        HostPort: os.Getenv("TEMPORAL_HOST_PORT"),
    })
    if err != nil {
        log.Fatalln("Unable to create Temporal client", err)
    }
    defer c.Close()
    
    // Create activities with dependencies
    activities := activities.NewActivities(
        config.NewS3Client(),
        config.NewVectorDB(),
        config.NewOpenAIClient(),
        config.NewDocumentParser(),
    )
    
    // Create worker
    w := worker.New(c, "document-processing", worker.Options{
        MaxConcurrentActivityExecutionSize: 100,
        MaxConcurrentWorkflowTaskExecutionSize: 50,
    })
    
    // Register workflows
    w.RegisterWorkflow(workflows.DocumentProcessingWorkflow)
    
    // Register activities
    w.RegisterActivity(activities.ParseCSVActivity)
    w.RegisterActivity(activities.ProcessBatchActivity)
    w.RegisterActivity(activities.StoreInVectorDBActivity)
    w.RegisterActivity(activities.GenerateSummaryActivity)
    
    // Start worker
    err = w.Run(worker.InterruptCh())
    if err != nil {
        log.Fatalln("Unable to start worker", err)
    }
}
```

### Java Worker

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
        
        // Create Temporal service stubs
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        
        // Create workflow client
        WorkflowClient client = WorkflowClient.newInstance(service);
        
        // Create worker factory
        WorkerFactory factory = WorkerFactory.newInstance(client);
        
        // Create worker options
        WorkerOptions workerOptions = WorkerOptions.newBuilder()
                .setMaxConcurrentActivityExecutionSize(100)
                .setMaxConcurrentWorkflowTaskExecutionSize(50)
                .build();
        
        // Create worker
        Worker worker = factory.newWorker(TASK_QUEUE, workerOptions);
        
        // Register workflows
        worker.registerWorkflowImplementationTypes(DocumentProcessingWorkflowImpl.class);
        
        // Register activities with Spring dependencies
        DocumentActivitiesImpl activities = context.getBean(DocumentActivitiesImpl.class);
        worker.registerActivitiesImplementations(activities);
        
        // Start worker factory
        factory.start();
        
        System.out.println("Worker started on task queue: " + TASK_QUEUE);
    }
}
```