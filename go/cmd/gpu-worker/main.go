package main

import (
	"log"
	"os"
	"strconv"

	"github.com/example/temporal-rag/activities/gpu"
	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/worker"
)

func main() {
	// Get GPU count from environment
	gpuCount := 2
	if gpuCountStr := os.Getenv("GPU_COUNT"); gpuCountStr != "" {
		if count, err := strconv.Atoi(gpuCountStr); err == nil {
			gpuCount = count
		}
	}

	// Create Temporal client
	c, err := client.Dial(client.Options{
		HostPort: getEnv("TEMPORAL_HOST", "localhost:7233"),
	})
	if err != nil {
		log.Fatalf("Unable to create Temporal client: %v", err)
	}
	defer c.Close()

	// Create worker for GPU-bound queue
	w := worker.New(c, "gpu-bound-queue", worker.Options{
		MaxConcurrentActivityExecutionSize: gpuCount * 4, // 4 concurrent tasks per GPU
		WorkerActivitiesPerSecond:          100,
	})

	// Create and register GPU activities
	gpuActivities := gpu.NewGPUBoundActivities(gpuCount)
	w.RegisterActivity(gpuActivities.GenerateEmbeddings)
	w.RegisterActivity(gpuActivities.ClassifyDocument)
	w.RegisterActivity(gpuActivities.PerformOCR)
	w.RegisterActivity(gpuActivities.AnalyzeImages)
	w.RegisterActivity(gpuActivities.RunLLMInference)

	log.Printf("Starting GPU Worker on queue: gpu-bound-queue with %d GPUs\n", gpuCount)
	log.Println("Optimized for GPU-accelerated ML inference and image processing")
	
	err = w.Run(worker.InterruptCh())
	if err != nil {
		log.Fatalf("Unable to start GPU Worker: %v", err)
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}