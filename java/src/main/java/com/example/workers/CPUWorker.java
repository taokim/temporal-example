package com.example.workers;

import com.example.activities.cpu.CPUBoundActivitiesImpl;
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

import java.util.concurrent.Executors;

@Slf4j
@SpringBootApplication(scanBasePackages = "com.example")
@Profile("cpu-worker")
public class CPUWorker {
    
    private static final String TASK_QUEUE = "cpu-bound-queue";
    
    public static void main(String[] args) {
        SpringApplication.run(CPUWorker.class, args);
    }
    
    @Bean
    public CommandLineRunner run(WorkflowServiceStubs service, CPUBoundActivitiesImpl activities) {
        return args -> {
            WorkflowClient client = WorkflowClient.newInstance(service);
            WorkerFactory factory = WorkerFactory.newInstance(client);
            
            // Configure worker for CPU-intensive tasks
            WorkerOptions workerOptions = WorkerOptions.newBuilder()
                .setMaxConcurrentActivityExecutionSize(Runtime.getRuntime().availableProcessors() * 2)
                .setMaxConcurrentWorkflowTaskExecutionSize(100)
                .setMaxConcurrentLocalActivityExecutionSize(Runtime.getRuntime().availableProcessors())
                .setActivityPollThreadCount(5)
                .setWorkflowPollThreadCount(2)
                .build();
            
            Worker worker = factory.newWorker(TASK_QUEUE, workerOptions);
            
            // Register CPU-bound activities
            worker.registerActivitiesImplementations(activities);
            
            // No workflows registered - this is an activity-only worker
            
            factory.start();
            
            log.info("CPU Worker started on task queue: {} with {} cores", 
                    TASK_QUEUE, Runtime.getRuntime().availableProcessors());
            log.info("Optimized for CPU-intensive operations");
        };
    }
}