package com.example.workflows;

import com.example.activities.cpu.CPUBoundActivities;
import com.example.activities.gpu.GPUBoundActivities;
import com.example.activities.io.IOBoundActivities;
import com.example.activities.ingestion.IngestionActivities;
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
public class ResourceOptimizedWorkflowImpl implements ResourceOptimizedWorkflow {
    
    // CPU-bound activities on CPU-optimized workers
    private final CPUBoundActivities cpuActivities = 
        Workflow.newActivityStub(CPUBoundActivities.class,
            ActivityOptions.newBuilder()
                .setTaskQueue("cpu-bound-queue")
                .setStartToCloseTimeout(Duration.ofMinutes(30))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3)
                    .setBackoffCoefficient(2.0)
                    .build())
                .build());
    
    // GPU-bound activities on GPU-equipped workers
    private final GPUBoundActivities gpuActivities = 
        Workflow.newActivityStub(GPUBoundActivities.class,
            ActivityOptions.newBuilder()
                .setTaskQueue("gpu-bound-queue")
                .setStartToCloseTimeout(Duration.ofHours(2))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(2)
                    .setDoNotRetry("java.lang.OutOfMemoryError")
                    .build())
                .build());
    
    // IO-bound activities on standard workers with async IO
    private final IOBoundActivities ioActivities = 
        Workflow.newActivityStub(IOBoundActivities.class,
            ActivityOptions.newBuilder()
                .setTaskQueue("io-bound-queue")
                .setStartToCloseTimeout(Duration.ofMinutes(15))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(5)
                    .setBackoffCoefficient(1.5)
                    .build())
                .build());
    
    // Keep existing activities for compatibility
    private final IngestionActivities ingestionActivities = 
        Workflow.newActivityStub(IngestionActivities.class,
            ActivityOptions.newBuilder()
                .setTaskQueue("io-bound-queue")  // CSV parsing is IO-bound
                .setStartToCloseTimeout(Duration.ofMinutes(10))
                .build());
    
    private final StorageActivities storageActivities = 
        Workflow.newActivityStub(StorageActivities.class,
            ActivityOptions.newBuilder()
                .setTaskQueue("document-pipeline-queue")
                .setStartToCloseTimeout(Duration.ofMinutes(10))
                .build());
    
    @Override
    public PipelineResult runOptimizedPipeline(PipelineInput input) {
        String workflowId = Workflow.getInfo().getWorkflowId();
        log.info("Starting resource-optimized pipeline: {}", workflowId);
        
        List<DocumentResult> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            // Parse CSV (CPU-bound)
            List<Document> documents = parseCSVOnCPU(input.getCsvPath());
            
            // Process documents in batches
            int batchSize = 5;
            for (int i = 0; i < documents.size(); i += batchSize) {
                List<Document> batch = documents.subList(i, 
                    Math.min(i + batchSize, documents.size()));
                
                try {
                    ProcessingBatchResult batchResult = processBatchOptimized(batch, workflowId, i / batchSize);
                    results.addAll(batchResult.getSuccessful());
                    errors.addAll(batchResult.getErrors());
                } catch (Exception e) {
                    log.error("Batch processing failed", e);
                    errors.add("Batch " + (i / batchSize) + " failed: " + e.getMessage());
                }
            }
            
            return PipelineResult.builder()
                .workflowId(workflowId)
                .totalDocuments(documents.size())
                .successfulDocuments(results.size())
                .failedDocuments(errors.size())
                .results(results)
                .errors(errors.stream()
                    .map(e -> ProcessingError.builder()
                        .error(e)
                        .stage("batch-processing")
                        .timestamp(java.time.LocalDateTime.now())
                        .build())
                    .collect(Collectors.toList()))
                .build();
                
        } catch (Exception e) {
            log.error("Pipeline failed", e);
            return PipelineResult.builder()
                .workflowId(workflowId)
                .totalDocuments(0)
                .successfulDocuments(0)
                .failedDocuments(1)
                .errors(List.of(ProcessingError.builder()
                    .error("Pipeline failed: " + e.getMessage())
                    .stage("pipeline-initialization")
                    .timestamp(java.time.LocalDateTime.now())
                    .build()))
                .build();
        }
    }
    
    private List<Document> parseCSVOnCPU(String csvPath) {
        // Use ingestion activity for CSV parsing (IO-bound)
        return ingestionActivities.parseCSVActivity(csvPath);
    }
    
    private ProcessingBatchResult processBatchOptimized(List<Document> batch, String workflowId, int batchNumber) {
        List<DocumentResult> successful = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        for (Document doc : batch) {
            try {
                // Step 1: Download document (IO-bound)
                Promise<DownloadResult> downloadPromise = Async.function(ioActivities::downloadDocument, doc.getUrl());
                
                // Step 2: Start CPU preprocessing while download is in progress
                Promise<ValidationResult> validationPromise = Async.function(cpuActivities::validateDocumentStructure, doc);
                
                // Wait for download to complete
                DownloadResult downloadResult = downloadPromise.get();
                if (!downloadResult.isSuccess()) {
                    errors.add("Download failed for " + doc.getId() + ": " + downloadResult.getError());
                    continue;
                }
                
                // Update document with downloaded content
                doc.setMetadataRaw(new String(downloadResult.getContent()));
                
                // Step 3: Extract text (CPU-bound) and perform OCR (GPU-bound) in parallel
                Promise<TextExtractionResult> textExtractionPromise = Async.function(cpuActivities::extractTextFromDocument, doc);
                Promise<OCRResult> ocrPromise = Async.function(gpuActivities::performOCR, doc);
                
                // Wait for validation
                ValidationResult validation = validationPromise.get();
                if (!validation.isValid()) {
                    errors.add("Validation failed for " + doc.getId() + ": " + validation.getErrors());
                    continue;
                }
                
                // Wait for text extraction and OCR
                TextExtractionResult textResult = textExtractionPromise.get();
                OCRResult ocrResult = ocrPromise.get();
                
                // Step 4: Process text (CPU-bound)
                PreprocessingInput preprocessInput = PreprocessingInput.builder()
                    .documentId(doc.getId())
                    .processedChunks(Arrays.asList(
                        TextChunk.builder()
                            .documentId(doc.getId())
                            .chunkId("text-chunk")
                            .content(textResult.getExtractedText() + " " + ocrResult.getExtractedText())
                            .build()
                    ))
                    .build();
                    
                TextProcessingResult textProcessing = cpuActivities.preprocessText(preprocessInput);
                
                // Step 5: Generate embeddings (GPU-bound)
                List<TextChunk> chunks = textProcessing.getSentences().stream()
                    .map(sentence -> TextChunk.builder()
                        .documentId(doc.getId())
                        .chunkId(UUID.randomUUID().toString())
                        .content(sentence)
                        .build())
                    .collect(Collectors.toList());
                    
                EmbeddingResult embeddings = gpuActivities.generateEmbeddings(chunks);
                
                // Step 6: Store results (IO-bound) - parallel operations
                Promise<UploadResult> s3UploadPromise = Async.function(() -> 
                    ioActivities.uploadToS3(doc, downloadResult.getContent())
                );
                
                Promise<ChromaDBResult> vectorStorePromise = Async.function(() -> {
                    List<VectorEmbedding> vectorEmbeddings = new ArrayList<>();
                    for (int i = 0; i < chunks.size(); i++) {
                        vectorEmbeddings.add(VectorEmbedding.builder()
                            .id(UUID.randomUUID().toString())
                            .documentId(doc.getId())
                            .chunkId(chunks.get(i).getChunkId())
                            .vector(embeddings.getEmbeddings().get(i))
                            .text(chunks.get(i).getContent())
                            .metadata(doc.getMetadata())
                            .build());
                    }
                    return ioActivities.storeVectorEmbeddings(vectorEmbeddings);
                });
                
                // Wait for storage operations
                UploadResult uploadResult = s3UploadPromise.get();
                ChromaDBResult chromaResult = vectorStorePromise.get();
                
                // Create result
                DocumentResult docResult = DocumentResult.builder()
                    .documentId(doc.getId())
                    .documentName(doc.getName())
                    .status("completed")
                    .s3Url(uploadResult.getS3Url())
                    .embeddingIds(chromaResult.getEmbeddingIds())
                    .processingTimeMs(System.currentTimeMillis())
                    .build();
                    
                successful.add(docResult);
                
            } catch (Exception e) {
                log.error("Document processing failed: {}", doc.getId(), e);
                errors.add("Processing failed for " + doc.getId() + ": " + e.getMessage());
            }
        }
        
        return ProcessingBatchResult.builder()
            .successful(successful)
            .errors(errors)
            .build();
    }
    
    @lombok.Data
    @lombok.Builder
    private static class ProcessingBatchResult {
        private List<DocumentResult> successful;
        private List<String> errors;
    }
}