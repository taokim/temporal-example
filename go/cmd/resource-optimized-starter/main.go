package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"

	"github.com/example/temporal-rag/internal/models"
	"github.com/example/temporal-rag/workflows"
	"go.temporal.io/sdk/client"
)

func main() {
	var csvFile string
	var batchSize int
	var timeout int
	var workflowID string

	flag.StringVar(&csvFile, "csv-file", "", "Path to CSV file containing documents")
	flag.IntVar(&batchSize, "batch-size", 10, "Number of documents to process in parallel")
	flag.IntVar(&timeout, "timeout", 60, "Workflow timeout in minutes")
	flag.StringVar(&workflowID, "workflow-id", "resource-optimized-pipeline", "Workflow ID")
	flag.Parse()

	if csvFile == "" {
		fmt.Println("Usage: resource-optimized-starter -csv-file <path> [-batch-size <n>] [-timeout <minutes>] [-workflow-id <id>]")
		os.Exit(1)
	}

	// Create Temporal client
	c, err := client.Dial(client.Options{
		HostPort: getEnv("TEMPORAL_HOST", "localhost:7233"),
	})
	if err != nil {
		log.Fatalf("Unable to create Temporal client: %v", err)
	}
	defer c.Close()

	// Start workflow execution
	workflowOptions := client.StartWorkflowOptions{
		ID:        workflowID,
		TaskQueue: "document-pipeline-queue",
	}

	we, err := c.ExecuteWorkflow(context.Background(), workflowOptions, 
		workflows.ResourceOptimizedWorkflow, csvFile, batchSize, timeout)
	if err != nil {
		log.Fatalf("Unable to execute workflow: %v", err)
	}

	log.Printf("Started resource-optimized workflow: %s\n", we.GetID())
	log.Printf("Parameters: csv=%s, batchSize=%d, timeout=%d minutes\n", csvFile, batchSize, timeout)
	log.Println("Workflow will distribute activities across specialized workers:")
	log.Println("  - CPU Worker: Text processing, validation, extraction")
	log.Println("  - GPU Worker: ML inference, embeddings, OCR")
	log.Println("  - IO Worker: Downloads, uploads, database operations")

	// Wait for workflow completion
	var result models.PipelineResult
	err = we.Get(context.Background(), &result)
	if err != nil {
		log.Fatalf("Workflow failed: %v", err)
	}

	// Print results
	fmt.Printf("\nWorkflow completed successfully!\n")
	fmt.Printf("Total documents: %d\n", result.TotalDocuments)
	fmt.Printf("Successful: %d\n", result.SuccessfulDocuments)
	fmt.Printf("Failed: %d\n", result.FailedDocuments)
	fmt.Printf("Duration: %v\n", result.Duration)

	if len(result.ProcessingErrors) > 0 {
		fmt.Printf("\nProcessing errors:\n")
		for _, err := range result.ProcessingErrors {
			fmt.Printf("  - Document %s failed at %s: %s\n", err.DocumentID, err.Stage, err.Error)
		}
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}