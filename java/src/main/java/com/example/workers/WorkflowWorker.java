package com.example.workers;

import com.example.workflows.DocumentPipelineWorkflowImpl;
import com.example.workflows.ResourceOptimizedWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@Slf4j
@SpringBootApplication(scanBasePackages = "com.example")
@Profile("workflow-worker")
public class WorkflowWorker {
    
    private static final String TASK_QUEUE = "document-pipeline-queue";
    
    public static void main(String[] args) {
        SpringApplication.run(WorkflowWorker.class, args);
    }
    
    @Bean
    public CommandLineRunner run(WorkflowServiceStubs service) {
        return args -> {
            WorkflowClient client = WorkflowClient.newInstance(service);
            WorkerFactory factory = WorkerFactory.newInstance(client);
            
            // Configure worker for workflow execution only
            WorkerOptions workerOptions = WorkerOptions.newBuilder()
                .setMaxConcurrentWorkflowTaskExecutionSize(1000) // High workflow concurrency
                .setMaxConcurrentActivityExecutionSize(0) // No activities on this worker
                .setWorkflowPollThreadCount(5)
                .build();
            
            Worker worker = factory.newWorker(TASK_QUEUE, workerOptions);
            
            // Register workflows only
            worker.registerWorkflowImplementationTypes(
                DocumentPipelineWorkflowImpl.class,
                ResourceOptimizedWorkflowImpl.class
            );
            
            factory.start();
            
            log.info("Workflow Worker started on task queue: {}", TASK_QUEUE);
            log.info("This worker handles workflow orchestration only");
            log.info("Activities are executed on specialized workers:");
            log.info("  - CPU-bound activities: cpu-bound-queue");
            log.info("  - GPU-bound activities: gpu-bound-queue");
            log.info("  - IO-bound activities: io-bound-queue");
        };
    }
}