package com.example;

import com.example.models.PipelineInput;
import com.example.models.PipelineResult;
import com.example.workflows.ResourceOptimizedWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.UUID;

@Slf4j
public class ResourceOptimizedStarter {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: ResourceOptimizedStarter --csv-file <path> [--batch-size <size>] [--timeout <minutes>]");
            System.exit(1);
        }
        
        // Parse command line arguments
        String csvPath = null;
        int batchSize = 10;
        long timeoutMinutes = 60;
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--csv-file":
                    if (i + 1 < args.length) {
                        csvPath = args[++i];
                    }
                    break;
                case "--batch-size":
                    if (i + 1 < args.length) {
                        batchSize = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--timeout":
                    if (i + 1 < args.length) {
                        timeoutMinutes = Long.parseLong(args[++i]);
                    }
                    break;
            }
        }
        
        if (csvPath == null) {
            System.err.println("Error: --csv-file is required");
            System.exit(1);
        }
        
        // Create Temporal client
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);
        
        // Create workflow options
        String workflowId = "resource-optimized-pipeline-" + UUID.randomUUID();
        WorkflowOptions options = WorkflowOptions.newBuilder()
            .setWorkflowId(workflowId)
            .setTaskQueue("document-pipeline-queue")
            .setWorkflowExecutionTimeout(Duration.ofMinutes(timeoutMinutes))
            .build();
        
        // Create workflow stub
        ResourceOptimizedWorkflow workflow = client.newWorkflowStub(ResourceOptimizedWorkflow.class, options);
        
        // Prepare input
        PipelineInput input = PipelineInput.builder()
            .csvPath(csvPath)
            .batchSize(batchSize)
            .build();
        
        log.info("Starting resource-optimized document pipeline workflow");
        log.info("Workflow ID: {}", workflowId);
        log.info("CSV file: {}", csvPath);
        log.info("Batch size: {}", batchSize);
        log.info("Timeout: {} minutes", timeoutMinutes);
        log.info("");
        log.info("This workflow uses specialized workers:");
        log.info("  - CPU-bound tasks: Text processing, validation, compression");
        log.info("  - GPU-bound tasks: ML inference, embeddings, OCR");
        log.info("  - IO-bound tasks: Downloads, uploads, database queries");
        
        try {
            // Start workflow execution (synchronous)
            PipelineResult result = workflow.runOptimizedPipeline(input);
            
            log.info("\nPipeline completed!");
            log.info("Total documents: {}", result.getTotalDocuments());
            log.info("Successful: {}", result.getSuccessfulDocuments());
            log.info("Failed: {}", result.getFailedDocuments());
            
            if (!result.getErrors().isEmpty()) {
                log.error("\nErrors:");
                result.getErrors().forEach(error -> log.error("  - {}", error));
            }
            
        } catch (Exception e) {
            log.error("Workflow execution failed", e);
            System.exit(1);
        }
    }
}