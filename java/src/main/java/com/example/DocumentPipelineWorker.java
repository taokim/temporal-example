package com.example;

import com.example.activities.ingestion.IngestionActivitiesImpl;
import com.example.activities.preprocessing.PreprocessingActivitiesImpl;
import com.example.activities.inference.InferenceActivitiesImpl;
import com.example.activities.postprocessing.PostprocessingActivitiesImpl;
import com.example.activities.storage.StorageActivitiesImpl;
import com.example.workflows.DocumentPipelineWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@Slf4j
@SpringBootApplication
public class DocumentPipelineWorker {
    
    private static final String TASK_QUEUE = "document-pipeline-queue";
    
    public static void main(String[] args) {
        // Start Spring Boot application
        ConfigurableApplicationContext context = SpringApplication.run(DocumentPipelineWorker.class, args);
        
        try {
            // Create Temporal service connection
            WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
            WorkflowClient client = WorkflowClient.newInstance(service);
            
            // Create worker factory
            WorkerFactory factory = WorkerFactory.newInstance(client);
            Worker worker = factory.newWorker(TASK_QUEUE);
            
            // Get activity implementations from Spring context
            IngestionActivitiesImpl ingestionActivities = context.getBean(IngestionActivitiesImpl.class);
            PreprocessingActivitiesImpl preprocessingActivities = context.getBean(PreprocessingActivitiesImpl.class);
            InferenceActivitiesImpl inferenceActivities = context.getBean(InferenceActivitiesImpl.class);
            PostprocessingActivitiesImpl postprocessingActivities = context.getBean(PostprocessingActivitiesImpl.class);
            StorageActivitiesImpl storageActivities = context.getBean(StorageActivitiesImpl.class);
            
            // Register workflow implementation
            worker.registerWorkflowImplementationTypes(DocumentPipelineWorkflowImpl.class);
            
            // Register activity implementations
            worker.registerActivitiesImplementations(
                    ingestionActivities,
                    preprocessingActivities,
                    inferenceActivities,
                    postprocessingActivities,
                    storageActivities
            );
            
            // Start the worker
            factory.start();
            
            log.info("Document Pipeline Worker started on task queue: {}", TASK_QUEUE);
            
            // Keep the worker running
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down worker...");
                factory.shutdown();
                context.close();
            }));
            
            // Block and wait
            Thread.currentThread().join();
            
        } catch (Exception e) {
            log.error("Worker failed to start", e);
            context.close();
            System.exit(1);
        }
    }
}