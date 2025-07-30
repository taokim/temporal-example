package com.example.workflows;

import com.example.activities.ingestion.IngestionActivities;
import com.example.activities.preprocessing.PreprocessingActivities;
import com.example.activities.inference.InferenceActivities;
import com.example.activities.postprocessing.PostprocessingActivities;
import com.example.activities.storage.StorageActivities;
import com.example.models.*;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DocumentPipelineWorkflowImpl implements DocumentPipelineWorkflow {
    
    private final IngestionActivities ingestionActivities;
    private final PreprocessingActivities preprocessingActivities;
    private final InferenceActivities inferenceActivities;
    private final PostprocessingActivities postprocessingActivities;
    private final StorageActivities storageActivities;
    
    public DocumentPipelineWorkflowImpl() {
        // Configure activity options with retries
        RetryOptions retryOptions = RetryOptions.newBuilder()
                .setInitialInterval(Duration.ofSeconds(1))
                .setBackoffCoefficient(2)
                .setMaximumInterval(Duration.ofSeconds(30))
                .setMaximumAttempts(3)
                .build();
        
        ActivityOptions defaultOptions = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(10))
                .setRetryOptions(retryOptions)
                .build();
        
        ActivityOptions longRunningOptions = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(30))
                .setHeartbeatTimeout(Duration.ofSeconds(30))
                .setRetryOptions(retryOptions)
                .build();
        
        // Initialize activity stubs
        this.ingestionActivities = Workflow.newActivityStub(
                IngestionActivities.class, longRunningOptions);
        this.preprocessingActivities = Workflow.newActivityStub(
                PreprocessingActivities.class, defaultOptions);
        this.inferenceActivities = Workflow.newActivityStub(
                InferenceActivities.class, longRunningOptions);
        this.postprocessingActivities = Workflow.newActivityStub(
                PostprocessingActivities.class, defaultOptions);
        this.storageActivities = Workflow.newActivityStub(
                StorageActivities.class, longRunningOptions);
    }
    
    @Override
    public PipelineResult runPipeline(PipelineInput input) {
        LocalDateTime startTime = LocalDateTime.now();
        String pipelineID = Workflow.getInfo().getWorkflowId();
        
        log.info("Starting document pipeline: {}", pipelineID);
        
        // Initialize result tracking
        List<PostprocessedDocument> allProcessedDocs = new ArrayList<>();
        List<ProcessingError> allErrors = new ArrayList<>();
        List<String> allVectorIDs = new ArrayList<>();
        List<String> allS3Keys = new ArrayList<>();
        
        try {
            // Stage 1: Data Ingestion - Parse CSV
            log.info("Stage 1: Parsing CSV from {}", input.getCsvPath());
            List<Document> documents = ingestionActivities.parseCSVActivity(input.getCsvPath());
            
            if (documents.isEmpty()) {
                log.warn("No documents found in CSV");
                return buildEmptyResult();
            }
            
            log.info("Found {} documents to process", documents.size());
            
            // Process in batches
            List<List<Document>> batches = createBatches(documents, input.getBatchSize());
            log.info("Processing {} batches of size {}", batches.size(), input.getBatchSize());
            
            // Process each batch in parallel
            List<Promise<BatchProcessingResult>> batchPromises = new ArrayList<>();
            
            for (int i = 0; i < batches.size(); i++) {
                List<Document> batch = batches.get(i);
                String batchID = String.format("%s-batch-%d", pipelineID, i);
                
                // Process batch asynchronously
                Promise<BatchProcessingResult> batchPromise = Async.function(
                    () -> processBatch(batchID, batch, input)
                );
                batchPromises.add(batchPromise);
            }
            
            // Wait for all batches to complete
            for (Promise<BatchProcessingResult> promise : batchPromises) {
                BatchProcessingResult batchResult = promise.get();
                allProcessedDocs.addAll(batchResult.processedDocuments);
                allErrors.addAll(batchResult.errors);
            }
            
            log.info("All batches processed. Total documents: {}, Errors: {}", 
                    allProcessedDocs.size(), allErrors.size());
            
            // Stage 5: Storage - Store results
            if (!allProcessedDocs.isEmpty()) {
                log.info("Stage 5: Storing {} processed documents", allProcessedDocs.size());
                
                // Store in parallel
                Promise<VectorDBStorageResult> vectorPromise = Async.function(() -> 
                    storageActivities.storeInVectorDB(VectorDBStorageInput.builder()
                            .collection(input.getChromaCollection())
                            .documents(allProcessedDocs)
                            .build())
                );
                
                Promise<S3StorageResult> s3Promise = Async.function(() ->
                    storageActivities.storeInS3(S3StorageInput.builder()
                            .bucket(input.getS3Bucket())
                            .keyPrefix(String.format("pipeline/%s", pipelineID))
                            .documents(allProcessedDocs)
                            .build())
                );
                
                // Wait for storage to complete
                VectorDBStorageResult vectorResult = vectorPromise.get();
                S3StorageResult s3Result = s3Promise.get();
                
                allVectorIDs.addAll(vectorResult.getStoredIDs());
                allS3Keys.addAll(s3Result.getObjectKeys());
                
                // Add any storage errors
                vectorResult.getErrors().forEach(e -> allErrors.add(
                    ProcessingError.builder()
                            .documentID(e.getDocumentID())
                            .stage("vector_storage")
                            .error(e.getError())
                            .timestamp(e.getTimestamp())
                            .build()
                ));
                
                s3Result.getErrors().forEach(e -> allErrors.add(
                    ProcessingError.builder()
                            .documentID(e.getDocumentID())
                            .stage("s3_storage")
                            .error(e.getError())
                            .timestamp(e.getTimestamp())
                            .build()
                ));
                
                // Store metadata
                MetadataStorageResult metadataResult = storageActivities.storeMetadata(
                    MetadataStorageInput.builder()
                            .pipelineID(pipelineID)
                            .startTime(startTime)
                            .endTime(LocalDateTime.now())
                            .documents(allProcessedDocs)
                            .processingErrors(allErrors)
                            .config(buildConfigMap(input))
                            .build()
                );
                
                log.info("Storage complete. VectorIDs: {}, S3Keys: {}, MetadataID: {}",
                        allVectorIDs.size(), allS3Keys.size(), metadataResult.getMetadataID());
                
                // Calculate final metrics
                double avgQualityScore = allProcessedDocs.stream()
                        .mapToDouble(PostprocessedDocument::getQualityScore)
                        .average()
                        .orElse(0.0);
                
                return PipelineResult.builder()
                        .totalProcessed(documents.size())
                        .successCount(allProcessedDocs.size())
                        .errorCount(allErrors.size())
                        .avgQualityScore(avgQualityScore)
                        .metadataID(metadataResult.getMetadataID())
                        .vectorStorageIDs(allVectorIDs)
                        .s3ObjectKeys(allS3Keys)
                        .errors(allErrors)
                        .build();
            }
            
        } catch (Exception e) {
            log.error("Pipeline failed with error: {}", e.getMessage(), e);
            throw new RuntimeException("Pipeline execution failed", e);
        }
        
        return buildEmptyResult();
    }
    
    private BatchProcessingResult processBatch(String batchID, List<Document> batch, PipelineInput input) {
        BatchProcessingResult result = new BatchProcessingResult();
        result.processedDocuments = new ArrayList<>();
        result.errors = new ArrayList<>();
        
        try {
            // Stage 1: Download documents
            IngestionBatchResult ingestionResult = ingestionActivities.downloadAndValidateBatch(
                IngestionBatchInput.builder()
                        .batchID(batchID)
                        .documents(batch)
                        .maxSizeMB(input.getMaxSizeMB())
                        .build()
            );
            
            if (ingestionResult.getSuccess().isEmpty()) {
                log.warn("No documents successfully downloaded in batch {}", batchID);
                result.errors.addAll(ingestionResult.getErrors());
                return result;
            }
            
            // Stage 2: Preprocessing
            PreprocessBatchResult preprocessResult = preprocessingActivities.preprocessBatch(
                PreprocessBatchInput.builder()
                        .batchID(batchID)
                        .documents(ingestionResult.getSuccess())
                        .options(input.getPreprocessingOptions())
                        .build()
            );
            
            result.errors.addAll(ingestionResult.getErrors());
            result.errors.addAll(preprocessResult.getErrors());
            
            if (preprocessResult.getSuccess().isEmpty()) {
                log.warn("No documents successfully preprocessed in batch {}", batchID);
                return result;
            }
            
            // Stage 3: Model Inference
            InferenceBatchResult inferenceResult = inferenceActivities.runInferenceBatch(
                InferenceBatchInput.builder()
                        .batchID(batchID)
                        .documents(preprocessResult.getSuccess())
                        .models(input.getModelConfig())
                        .build()
            );
            
            result.errors.addAll(inferenceResult.getErrors());
            
            if (inferenceResult.getSuccess().isEmpty()) {
                log.warn("No documents successfully inferred in batch {}", batchID);
                return result;
            }
            
            // Stage 4: Post-processing
            PostprocessedBatchResult postprocessResult = postprocessingActivities.postprocessDocuments(
                PostprocessBatchInput.builder()
                        .batchID(batchID)
                        .documents(inferenceResult.getSuccess())
                        .build()
            );
            
            result.processedDocuments.addAll(postprocessResult.getDocuments());
            
            log.info("Batch {} completed. Processed: {}, Errors: {}", 
                    batchID, result.processedDocuments.size(), result.errors.size());
            
        } catch (Exception e) {
            log.error("Batch {} processing failed: {}", batchID, e.getMessage());
            batch.forEach(doc -> result.errors.add(
                ProcessingError.builder()
                        .documentID(doc.getId())
                        .stage("batch_processing")
                        .error(e.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build()
            ));
        }
        
        return result;
    }
    
    private List<List<Document>> createBatches(List<Document> documents, int batchSize) {
        List<List<Document>> batches = new ArrayList<>();
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            batches.add(documents.subList(i, end));
        }
        return batches;
    }
    
    private Map<String, Object> buildConfigMap(PipelineInput input) {
        Map<String, Object> config = new HashMap<>();
        config.put("csv_path", input.getCsvPath());
        config.put("batch_size", input.getBatchSize());
        config.put("max_size_mb", input.getMaxSizeMB());
        config.put("chroma_collection", input.getChromaCollection());
        config.put("s3_bucket", input.getS3Bucket());
        
        if (input.getPreprocessingOptions() != null) {
            Map<String, Object> preprocConfig = new HashMap<>();
            preprocConfig.put("chunk_size", input.getPreprocessingOptions().getChunkSize());
            preprocConfig.put("chunk_overlap", input.getPreprocessingOptions().getChunkOverlap());
            preprocConfig.put("remove_pii", input.getPreprocessingOptions().isRemovePII());
            preprocConfig.put("detect_language", input.getPreprocessingOptions().isDetectLanguage());
            config.put("preprocessing", preprocConfig);
        }
        
        if (input.getModelConfig() != null) {
            Map<String, Object> modelConfig = new HashMap<>();
            modelConfig.put("generate_embeddings", input.getModelConfig().isGenerateEmbeddings());
            modelConfig.put("embedding_model", input.getModelConfig().getEmbeddingModel());
            modelConfig.put("generate_summary", input.getModelConfig().isGenerateSummary());
            modelConfig.put("summary_model", input.getModelConfig().getSummaryModel());
            modelConfig.put("extract_entities", input.getModelConfig().isExtractEntities());
            config.put("models", modelConfig);
        }
        
        return config;
    }
    
    private PipelineResult buildEmptyResult() {
        return PipelineResult.builder()
                .totalProcessed(0)
                .successCount(0)
                .errorCount(0)
                .avgQualityScore(0.0)
                .metadataID("")
                .vectorStorageIDs(new ArrayList<>())
                .s3ObjectKeys(new ArrayList<>())
                .errors(new ArrayList<>())
                .build();
    }
    
    // Inner class for batch processing results
    private static class BatchProcessingResult {
        List<PostprocessedDocument> processedDocuments;
        List<ProcessingError> errors;
    }
}