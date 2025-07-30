package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"
	"strings"
	"time"

	"go.temporal.io/sdk/client"

	"github.com/example/temporal-rag/internal/models"
	"github.com/example/temporal-rag/workflows"
)

const (
	taskQueue = "document-pipeline-queue"
)

func main() {
	// Parse command line flags
	var (
		csvFile            = flag.String("csv-file", "", "Path to CSV file containing documents")
		s3Bucket           = flag.String("s3-bucket", "documents", "S3 bucket for document storage")
		vectorDBCollection = flag.String("vector-db-collection", "documents", "Vector DB collection name")
		embeddingModel     = flag.String("embedding-model", "text-embedding-3-small", "Embedding model to use")
		summaryModel       = flag.String("summary-model", "gpt-3.5-turbo", "Summary model to use")
		removePII          = flag.Bool("remove-pii", false, "Remove PII from documents")
		languages          = flag.String("languages", "en,es", "Comma-separated list of accepted languages")
		workflowID         = flag.String("workflow-id", "", "Custom workflow ID (optional)")
	)

	flag.Parse()

	// Validate required arguments
	if *csvFile == "" {
		log.Fatal("--csv-file is required")
	}

	// Parse languages
	var acceptedLanguages []string
	if *languages != "" {
		for _, lang := range splitAndTrim(*languages, ",") {
			acceptedLanguages = append(acceptedLanguages, lang)
		}
	}

	// Get Temporal server URL from environment
	temporalHost := os.Getenv("TEMPORAL_HOST_URL")
	if temporalHost == "" {
		temporalHost = "localhost:7233"
	}

	// Create Temporal client
	c, err := client.Dial(client.Options{
		HostPort: temporalHost,
	})
	if err != nil {
		log.Fatal("Unable to create Temporal client:", err)
	}
	defer c.Close()

	// Prepare workflow input
	input := models.PipelineInput{
		CSVPath:            *csvFile,
		S3Bucket:           *s3Bucket,
		VectorDBCollection: *vectorDBCollection,
		EmbeddingModel:     *embeddingModel,
		SummaryModel:       *summaryModel,
		RemovePII:          *removePII,
		AcceptedLanguages:  acceptedLanguages,
	}

	// Generate workflow ID if not provided
	if *workflowID == "" {
		*workflowID = fmt.Sprintf("document-pipeline-%d", time.Now().Unix())
	}

	// Workflow options
	workflowOptions := client.StartWorkflowOptions{
		ID:        *workflowID,
		TaskQueue: taskQueue,
		WorkflowExecutionTimeout: 2 * time.Hour,
		WorkflowTaskTimeout:      10 * time.Minute,
	}

	// Start the workflow
	log.Printf("Starting workflow with ID: %s", *workflowID)
	log.Printf("CSV File: %s", *csvFile)
	log.Printf("S3 Bucket: %s", *s3Bucket)
	log.Printf("Vector DB Collection: %s", *vectorDBCollection)
	log.Printf("Embedding Model: %s", *embeddingModel)
	log.Printf("Summary Model: %s", *summaryModel)
	log.Printf("Remove PII: %v", *removePII)
	log.Printf("Accepted Languages: %v", acceptedLanguages)

	we, err := c.ExecuteWorkflow(context.Background(), workflowOptions, workflows.DocumentPipelineWorkflow, input)
	if err != nil {
		log.Fatal("Unable to execute workflow:", err)
	}

	log.Printf("Started workflow, WorkflowID: %s, RunID: %s", we.GetID(), we.GetRunID())

	// Wait for workflow completion
	log.Println("Waiting for workflow to complete...")
	var result models.PipelineResult
	err = we.Get(context.Background(), &result)
	if err != nil {
		log.Fatal("Unable to get workflow result:", err)
	}

	// Print results
	log.Println("\n=== Workflow Completed Successfully ===")
	log.Printf("Pipeline ID: %s", result.PipelineID)
	log.Printf("Total Documents Processed: %d", result.TotalProcessed)
	log.Printf("Total Errors: %d", result.TotalErrors)
	log.Printf("Execution Time: %s", result.ExecutionTime)
	log.Printf("\nVector DB Indices: %d stored", len(result.VectorDBIndices))
	log.Printf("S3 Objects: %d stored", len(result.S3Locations))
	log.Printf("Metadata ID: %s", result.MetadataID)
	
	log.Println("\nProcessing Stages:")
	for stage, metrics := range result.Stages {
		log.Printf("  %s: %v", stage, metrics)
	}

	if len(result.Errors) > 0 {
		log.Println("\nProcessing Errors:")
		for _, err := range result.Errors {
			log.Printf("  [%s] %s: %s", err.Stage, err.DocumentID, err.Error)
		}
	}

	log.Println("\n✅ Pipeline execution completed!")
	log.Printf("View workflow in Temporal UI: http://localhost:8080/namespaces/default/workflows/%s", *workflowID)
}

func splitAndTrim(s string, sep string) []string {
	var result []string
	for _, part := range strings.Split(s, sep) {
		if trimmed := strings.TrimSpace(part); trimmed != "" {
			result = append(result, trimmed)
		}
	}
	return result
}