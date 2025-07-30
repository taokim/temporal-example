package workflows

import (
	"fmt"
	"time"

	"go.temporal.io/sdk/temporal"
	"go.temporal.io/sdk/workflow"

	"github.com/example/temporal-rag/internal/models"
	"github.com/example/temporal-rag/activities/cpu"
	"github.com/example/temporal-rag/activities/gpu"
	"github.com/example/temporal-rag/activities/io"
)

// ResourceOptimizedWorkflow is the workflow function
func ResourceOptimizedWorkflow(ctx workflow.Context, csvPath string, batchSize int, timeoutMinutes int) (*models.PipelineResult, error) {
	logger := workflow.GetLogger(ctx)
	logger.Info("Starting resource-optimized document pipeline workflow",
		"csvPath", csvPath,
		"batchSize", batchSize,
		"timeout", timeoutMinutes)

	// Set workflow timeout
	ctx, cancel := workflow.WithCancel(ctx)
	defer cancel()

	// Activity options for different task queues
	ioActivityOptions := workflow.ActivityOptions{
		TaskQueue:           "io-bound-queue",
		StartToCloseTimeout: 5 * time.Minute,
		RetryPolicy: &temporal.RetryPolicy{
			MaximumAttempts: 3,
		},
	}

	cpuActivityOptions := workflow.ActivityOptions{
		TaskQueue:           "cpu-bound-queue",
		StartToCloseTimeout: 10 * time.Minute,
		RetryPolicy: &temporal.RetryPolicy{
			MaximumAttempts: 3,
		},
	}

	gpuActivityOptions := workflow.ActivityOptions{
		TaskQueue:           "gpu-bound-queue",
		StartToCloseTimeout: 15 * time.Minute,
		RetryPolicy: &temporal.RetryPolicy{
			MaximumAttempts: 2,
		},
	}

	// Create activity contexts for each queue
	ioCtx := workflow.WithActivityOptions(ctx, ioActivityOptions)
	cpuCtx := workflow.WithActivityOptions(ctx, cpuActivityOptions)
	gpuCtx := workflow.WithActivityOptions(ctx, gpuActivityOptions)

	// Step 1: Parse CSV (IO-bound)
	var documents []models.Document
	err := workflow.ExecuteActivity(ioCtx, "parse_csv", csvPath).Get(ctx, &documents)
	if err != nil {
		return nil, fmt.Errorf("failed to parse CSV: %w", err)
	}

	logger.Info("CSV parsed successfully", "documentCount", len(documents))

	// Initialize result
	result := &models.PipelineResult{
		WorkflowID:          workflow.GetInfo(ctx).WorkflowExecution.ID,
		TotalDocuments:      len(documents),
		SuccessfulDocuments: 0,
		FailedDocuments:     0,
		ProcessingErrors:    []models.ProcessingError{},
		StartTime:           workflow.Now(ctx),
	}

	// Process documents in batches
	for i := 0; i < len(documents); i += batchSize {
		end := i + batchSize
		if end > len(documents) {
			end = len(documents)
		}
		batch := documents[i:end]
		
		logger.Info("Processing batch", "batchStart", i, "batchEnd", end, "batchSize", len(batch))

		// Process each document in the batch in parallel
		var futures []workflow.Future
		
		for _, doc := range batch {
			// Create a child workflow context for parallel execution
			childCtx := workflow.WithChildOptions(ctx, workflow.ChildWorkflowOptions{
				WorkflowExecutionTimeout: time.Duration(timeoutMinutes) * time.Minute,
			})

			future := processDocumentAsync(childCtx, ioCtx, cpuCtx, gpuCtx, doc)
			futures = append(futures, future)
		}

		// Wait for all documents in the batch to complete
		for idx, future := range futures {
			var docResult *DocumentResult
			err := future.Get(ctx, &docResult)
			if err != nil {
				logger.Error("Failed to process document", "error", err, "document", batch[idx].ID)
				result.FailedDocuments++
				result.ProcessingErrors = append(result.ProcessingErrors, models.ProcessingError{
					DocumentID: batch[idx].ID,
					Stage:      "processing",
					Error:      err.Error(),
					Timestamp:  workflow.Now(ctx),
				})
			} else if docResult != nil {
				if docResult.Success {
					result.SuccessfulDocuments++
				} else {
					result.FailedDocuments++
					result.ProcessingErrors = append(result.ProcessingErrors, models.ProcessingError{
						DocumentID: docResult.DocumentID,
						Stage:      docResult.FailedStage,
						Error:      docResult.Error,
						Timestamp:  workflow.Now(ctx),
					})
				}
			}
		}
	}

	result.EndTime = workflow.Now(ctx)
	result.Duration = result.EndTime.Sub(result.StartTime)

	logger.Info("Resource-optimized workflow completed",
		"successful", result.SuccessfulDocuments,
		"failed", result.FailedDocuments,
		"duration", result.Duration)

	return result, nil
}

// processDocumentAsync processes a single document asynchronously across different workers
func processDocumentAsync(ctx workflow.Context, ioCtx workflow.Context, cpuCtx workflow.Context, gpuCtx workflow.Context, doc models.Document) workflow.Future {
	return workflow.ExecuteLocalActivity(ctx, func(ctx workflow.Context) (*DocumentResult, error) {
		logger := workflow.GetLogger(ctx)
		logger.Info("Processing document", "documentID", doc.ID)

		result := &DocumentResult{
			DocumentID: doc.ID,
			Success:    true,
		}

		// Step 1: Download document (IO-bound)
		var downloadResult io.DownloadResult
		err := workflow.ExecuteActivity(ioCtx, "DownloadDocument", doc.ID, doc.URL).Get(ctx, &downloadResult)
		if err != nil {
			result.Success = false
			result.FailedStage = "download"
			result.Error = err.Error()
			return result, nil
		}

		// Step 2: Validate document structure (CPU-bound)
		var validationResult cpu.ValidationResult
		err = workflow.ExecuteActivity(cpuCtx, "ValidateDocumentStructure", doc.ID, string(downloadResult.Data)).Get(ctx, &validationResult)
		if err != nil {
			result.Success = false
			result.FailedStage = "validation"
			result.Error = err.Error()
			return result, nil
		}

		if !validationResult.IsValid {
			result.Success = false
			result.FailedStage = "validation"
			result.Error = fmt.Sprintf("Document validation failed: %v", validationResult.Issues)
			return result, nil
		}

		// Parallel execution of CPU and GPU tasks
		var cpuFutures []workflow.Future
		var gpuFutures []workflow.Future

		// CPU tasks
		cpuFutures = append(cpuFutures,
			workflow.ExecuteActivity(cpuCtx, "ExtractTextFromDocument", doc.ID, downloadResult.Data),
			workflow.ExecuteActivity(cpuCtx, "PreprocessText", doc.ID, string(downloadResult.Data)),
		)

		// GPU tasks
		gpuFutures = append(gpuFutures,
			workflow.ExecuteActivity(gpuCtx, "PerformOCR", doc.ID, downloadResult.Data),
			workflow.ExecuteActivity(gpuCtx, "ClassifyDocument", doc.ID, string(downloadResult.Data)),
		)

		// Wait for CPU tasks
		var extractedText string
		var textProcessingResult cpu.TextProcessingResult
		
		err = cpuFutures[0].Get(ctx, &extractedText)
		if err != nil {
			result.Success = false
			result.FailedStage = "text_extraction"
			result.Error = err.Error()
			return result, nil
		}

		err = cpuFutures[1].Get(ctx, &textProcessingResult)
		if err != nil {
			result.Success = false
			result.FailedStage = "text_processing"
			result.Error = err.Error()
			return result, nil
		}

		// Wait for GPU tasks
		var ocrResult gpu.OCRResult
		var classificationResult gpu.ClassificationResult

		err = gpuFutures[0].Get(ctx, &ocrResult)
		if err != nil {
			logger.Warn("OCR failed, continuing", "error", err)
		}

		err = gpuFutures[1].Get(ctx, &classificationResult)
		if err != nil {
			logger.Warn("Classification failed, continuing", "error", err)
		}

		// Generate embeddings (GPU-bound)
		chunks := []string{textProcessingResult.ProcessedText}
		if len(chunks[0]) > 1000 {
			// Simple chunking
			chunks = []string{chunks[0][:1000], chunks[0][1000:]}
		}

		var embeddingResult gpu.EmbeddingResult
		err = workflow.ExecuteActivity(gpuCtx, "GenerateEmbeddings", doc.ID, chunks).Get(ctx, &embeddingResult)
		if err != nil {
			result.Success = false
			result.FailedStage = "embeddings"
			result.Error = err.Error()
			return result, nil
		}

		// Store results in parallel (IO-bound)
		var ioFutures []workflow.Future
		
		// Store in S3
		ioFutures = append(ioFutures,
			workflow.ExecuteActivity(ioCtx, "UploadToS3", doc.ID, downloadResult.Data),
		)

		// Store embeddings in vector DB
		ioFutures = append(ioFutures,
			workflow.ExecuteActivity(ioCtx, "StoreVectorEmbeddings", doc.ID, embeddingResult.Embeddings),
		)

		// Wait for storage operations
		for _, future := range ioFutures {
			err = future.Get(ctx, nil)
			if err != nil {
				logger.Warn("Storage operation failed", "error", err)
				// Continue processing even if storage fails
			}
		}

		result.ProcessingTime = workflow.Now(ctx).Sub(workflow.Now(ctx))
		logger.Info("Document processed successfully", "documentID", doc.ID)
		
		return result, nil
	})
}

// DocumentResult represents the processing result for a single document
type DocumentResult struct {
	DocumentID     string
	Success        bool
	FailedStage    string
	Error          string
	ProcessingTime time.Duration
}

