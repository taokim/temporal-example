package main

import (
	"log"
	"os"
	"runtime"

	"github.com/example/temporal-rag/activities/cpu"
	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/worker"
)

func main() {
	// Set GOMAXPROCS to use all CPU cores
	runtime.GOMAXPROCS(runtime.NumCPU())

	// Create Temporal client
	c, err := client.Dial(client.Options{
		HostPort: getEnv("TEMPORAL_HOST", "localhost:7233"),
	})
	if err != nil {
		log.Fatalf("Unable to create Temporal client: %v", err)
	}
	defer c.Close()

	// Create worker for CPU-bound queue
	w := worker.New(c, "cpu-bound-queue", worker.Options{
		MaxConcurrentActivityExecutionSize: runtime.NumCPU() * 2,
		WorkerActivitiesPerSecond:          1000,
	})

	// Create and register CPU activities
	cpuActivities := cpu.NewCPUBoundActivities()
	w.RegisterActivity(cpuActivities.PreprocessText)
	w.RegisterActivity(cpuActivities.ValidateDocumentStructure)
	w.RegisterActivity(cpuActivities.ExtractTextFromDocument)
	w.RegisterActivity(cpuActivities.TokenizeText)
	w.RegisterActivity(cpuActivities.CompressDocument)

	log.Printf("Starting CPU Worker on queue: cpu-bound-queue with %d cores\n", runtime.NumCPU())
	log.Println("Optimized for CPU-intensive text processing and validation")
	
	err = w.Run(worker.InterruptCh())
	if err != nil {
		log.Fatalf("Unable to start CPU Worker: %v", err)
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}