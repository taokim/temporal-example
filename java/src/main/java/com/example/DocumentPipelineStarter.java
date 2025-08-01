package com.example;

import com.example.models.*;
import com.example.workflows.DocumentPipelineWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.UUID;

@Slf4j
public class DocumentPipelineStarter {
    
    private static final String TASK_QUEUE = "document-pipeline-queue";
    
    public static void main(String[] args) {
        // Parse command line arguments
        String csvPath = "../testdata/documents.csv";
        String s3Bucket = "documents";
        String vectorCollection = "documents";
        String embeddingModel = "text-embedding-3-small";
        String summaryModel = "gpt-3.5-turbo";
        int batchSize = 10;
        int maxSizeMB = 50;
        
        // Parse flags
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--csv-file":
                    csvPath = args[++i];
                    break;
                case "--s3-bucket":
                    s3Bucket = args[++i];
                    break;
                case "--vector-db-collection":
                    vectorCollection = args[++i];
                    break;
                case "--embedding-model":
                    embeddingModel = args[++i];
                    break;
                case "--summary-model":
                    summaryModel = args[++i];
                    break;
                case "--batch-size":
                    batchSize = Integer.parseInt(args[++i]);
                    break;
                case "--max-size-mb":
                    maxSizeMB = Integer.parseInt(args[++i]);
                    break;
            }
        }
        
        // Create workflow client
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);
        
        // Generate unique workflow ID
        String workflowId = "doc-pipeline-" + UUID.randomUUID().toString();
        
        // Configure workflow options
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofHours(2))
                .build();
        
        // Create workflow stub
        DocumentPipelineWorkflow workflow = client.newWorkflowStub(
                DocumentPipelineWorkflow.class, options);
        
        // Prepare input
        PipelineInput input = PipelineInput.builder()
                .csvPath(csvPath)
                .batchSize(batchSize)
                .maxSizeMB(maxSizeMB)
                .preprocessingOptions(PreprocessingOptions.builder()
                        .chunkSize(1000)
                        .chunkOverlap(200)
                        .removePII(true)
                        .detectLanguage(true)
                        .build())
                .modelConfig(ModelConfig.builder()
                        .generateEmbeddings(true)
                        .embeddingModel(embeddingModel)
                        .generateSummary(true)
                        .summaryModel(summaryModel)
                        .extractEntities(true)
                        .build())
                .chromaCollection(vectorCollection)
                .s3Bucket(s3Bucket)
                .build();
        
        log.info("Starting workflow: {}", workflowId);
        log.info("Input parameters:");
        log.info("  CSV Path: {}", csvPath);
        log.info("  Batch Size: {}", batchSize);
        log.info("  Max Size MB: {}", maxSizeMB);
        log.info("  Chroma Collection: {}", input.getChromaCollection());
        log.info("  S3 Bucket: {}", input.getS3Bucket());
        
        try {
            // Start workflow execution
            WorkflowClient.start(workflow::runPipeline, input);
            
            log.info("Workflow started successfully!");
            log.info("To check status, run:");
            log.info("  temporal workflow describe --workflow-id={}", workflowId);
            
            // Optionally wait for result
            if (args.length > 3 && "wait".equals(args[3])) {
                log.info("Waiting for workflow completion...");
                PipelineResult result = workflow.runPipeline(input);
                
                log.info("Workflow completed!");
                log.info("Results:");
                log.info("  Total Processed: {}", result.getTotalProcessed());
                log.info("  Success Count: {}", result.getSuccessCount());
                log.info("  Error Count: {}", result.getErrorCount());
                log.info("  Average Quality Score: {:.2f}", result.getAvgQualityScore());
                log.info("  Metadata ID: {}", result.getMetadataID());
                log.info("  Vector Storage IDs: {}", result.getVectorStorageIDs().size());
                log.info("  S3 Object Keys: {}", result.getS3ObjectKeys().size());
                
                if (!result.getErrors().isEmpty()) {
                    log.warn("Errors encountered:");
                    result.getErrors().forEach(err -> 
                        log.warn("  - Document: {}, Stage: {}, Error: {}", 
                                err.getDocumentID(), err.getStage(), err.getError())
                    );
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to start workflow", e);
            System.exit(1);
        }
    }
}