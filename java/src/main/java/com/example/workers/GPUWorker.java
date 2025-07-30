package com.example.workers;

import com.example.activities.gpu.GPUBoundActivitiesImpl;
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
@Profile("gpu-worker")
public class GPUWorker {
    
    private static final String TASK_QUEUE = "gpu-bound-queue";
    
    public static void main(String[] args) {
        SpringApplication.run(GPUWorker.class, args);
    }
    
    @Bean
    public CommandLineRunner run(WorkflowServiceStubs service, GPUBoundActivitiesImpl activities) {
        return args -> {
            WorkflowClient client = WorkflowClient.newInstance(service);
            WorkerFactory factory = WorkerFactory.newInstance(client);
            
            // Configure worker for GPU-intensive tasks
            // Limit concurrent activities to available GPUs
            int availableGPUs = getAvailableGPUCount();
            
            WorkerOptions workerOptions = WorkerOptions.newBuilder()
                .setMaxConcurrentActivityExecutionSize(availableGPUs) // One activity per GPU
                .setMaxConcurrentWorkflowTaskExecutionSize(50)
                .setMaxConcurrentLocalActivityExecutionSize(0) // No local activities
                .setActivityPollThreadCount(2)
                .setWorkflowPollThreadCount(1)
                .build();
            
            Worker worker = factory.newWorker(TASK_QUEUE, workerOptions);
            
            // Register GPU-bound activities
            worker.registerActivitiesImplementations(activities);
            
            // No workflows registered - this is an activity-only worker
            
            factory.start();
            
            log.info("GPU Worker started on task queue: {} with {} GPUs", 
                    TASK_QUEUE, availableGPUs);
            log.info("Optimized for GPU-accelerated ML inference and image processing");
        };
    }
    
    private int getAvailableGPUCount() {
        // In production, this would detect actual GPU devices
        // For now, simulate 2 GPUs available
        String gpuCount = System.getenv("GPU_COUNT");
        if (gpuCount != null) {
            try {
                return Integer.parseInt(gpuCount);
            } catch (NumberFormatException e) {
                log.warn("Invalid GPU_COUNT environment variable: {}", gpuCount);
            }
        }
        return 2; // Default to 2 GPUs
    }
}