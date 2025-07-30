package main

import (
	"log"
	"os"

	"github.com/example/temporal-rag/workflows"
	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/worker"
)

func main() {
	// Create Temporal client
	c, err := client.Dial(client.Options{
		HostPort: getEnv("TEMPORAL_HOST", "localhost:7233"),
	})
	if err != nil {
		log.Fatalf("Unable to create Temporal client: %v", err)
	}
	defer c.Close()

	// Create worker for workflow queue only
	w := worker.New(c, "document-pipeline-queue", worker.Options{
		// Only poll for workflows, not activities
		EnableSessionWorker: false,
	})

	// Register workflows
	w.RegisterWorkflow(workflows.ResourceOptimizedWorkflow)

	log.Println("Starting Workflow Worker on queue: document-pipeline-queue")
	log.Println("This worker only handles workflow orchestration")
	
	err = w.Run(worker.InterruptCh())
	if err != nil {
		log.Fatalf("Unable to start Workflow Worker: %v", err)
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}