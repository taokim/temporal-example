package com.example.workers;

import com.example.activities.io.IOBoundActivitiesImpl;
import com.example.activities.ingestion.IngestionActivitiesImpl;
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
@Profile("io-worker")
public class IOWorker {
    
    private static final String TASK_QUEUE = "io-bound-queue";
    
    public static void main(String[] args) {
        SpringApplication.run(IOWorker.class, args);
    }
    
    @Bean
    public CommandLineRunner run(WorkflowServiceStubs service, 
                               IOBoundActivitiesImpl ioActivities,
                               IngestionActivitiesImpl ingestionActivities) {
        return args -> {
            WorkflowClient client = WorkflowClient.newInstance(service);
            WorkerFactory factory = WorkerFactory.newInstance(client);
            
            // Configure worker for IO-intensive tasks
            // High concurrency since most time is spent waiting for IO
            WorkerOptions workerOptions = WorkerOptions.newBuilder()
                .setMaxConcurrentActivityExecutionSize(200) // High concurrency for IO
                .setMaxConcurrentWorkflowTaskExecutionSize(100)
                .setMaxConcurrentLocalActivityExecutionSize(50)
                .setActivityPollThreadCount(10)
                .setWorkflowPollThreadCount(5)
                .build();
            
            Worker worker = factory.newWorker(TASK_QUEUE, workerOptions);
            
            // Register IO-bound activities
            worker.registerActivitiesImplementations(ioActivities, ingestionActivities);
            
            // No workflows registered - this is an activity-only worker
            
            factory.start();
            
            log.info("IO Worker started on task queue: {}", TASK_QUEUE);
            log.info("Optimized for network IO, database queries, and external API calls");
            log.info("Configured for high concurrency with async IO operations");
        };
    }
}