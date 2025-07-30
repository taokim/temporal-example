package workflows

import (
	"fmt"
	"time"

	"go.temporal.io/sdk/temporal"
	"go.temporal.io/sdk/workflow"
	
	"github.com/example/temporal-rag/internal/activities/ingestion"
	"github.com/example/temporal-rag/internal/activities/preprocessing"
	"github.com/example/temporal-rag/internal/activities/inference"
	"github.com/example/temporal-rag/internal/activities/postprocessing"
	"github.com/example/temporal-rag/internal/activities/storage"
	"github.com/example/temporal-rag/internal/models"
)

// DocumentPipelineWorkflow orchestrates the entire document processing pipeline
func DocumentPipelineWorkflow(ctx workflow.Context, input models.PipelineInput) (*models.PipelineResult, error) {
	logger := workflow.GetLogger(ctx)
	logger.Info("Starting document processing pipeline", 
		"csvPath", input.CSVPath,
		"pipelineID", workflow.GetInfo(ctx).WorkflowExecution.ID)

	// Initialize result tracking
	result := &models.PipelineResult{
		PipelineID: workflow.GetInfo(ctx).WorkflowExecution.ID,
		StartTime:  workflow.Now(ctx),
		Stages:     make(map[string]models.StageResult),
	}

	// Configure activity options for each stage
	ingestionOptions := workflow.ActivityOptions{
		TaskQueue:           "document-pipeline",
		StartToCloseTimeout: 10 * time.Minute,
		RetryPolicy: &temporal.RetryPolicy{
			MaximumAttempts:        3,
			InitialInterval:        time.Second,
			MaximumInterval:        10 * time.Second,
			BackoffCoefficient:     2,
			NonRetryableErrorTypes: []string{"ValidationError"},
		},
	}

	preprocessingOptions := workflow.ActivityOptions{
		TaskQueue:           "document-pipeline",
		StartToCloseTimeout: 20 * time.Minute,
		HeartbeatTimeout:    30 * time.Second,
		RetryPolicy: &temporal.RetryPolicy{
			MaximumAttempts: 5,
		},
	}

	inferenceOptions := workflow.ActivityOptions{
		TaskQueue:           "document-pipeline",
		StartToCloseTimeout: 30 * time.Minute,
		HeartbeatTimeout:    1 * time.Minute,
		RetryPolicy: &temporal.RetryPolicy{
			MaximumAttempts: 3,
			MaximumInterval: 30 * time.Second,
		},
	}

	storageOptions := workflow.ActivityOptions{
		TaskQueue:           "document-pipeline",
		StartToCloseTimeout: 15 * time.Minute,
		RetryPolicy: &temporal.RetryPolicy{
			MaximumAttempts: 5,
		},
	}

	// Stage 1: Data Ingestion
	logger.Info("Stage 1: Data Ingestion")
	ingestionCtx := workflow.WithActivityOptions(ctx, ingestionOptions)
	
	var csvDocuments []models.Document
	err := workflow.ExecuteActivity(ingestionCtx, 
		ingestion.ParseCSVActivity, 
		input.CSVPath).Get(ctx, &csvDocuments)
	if err != nil {
		return nil, fmt.Errorf("failed to parse CSV: %w", err)
	}

	// Download documents in parallel batches
	downloadBatches := createBatches(csvDocuments, 10)
	var allDocuments []models.IngestedDocument
	var ingestionErrors []models.ProcessingError

	for i, batch := range downloadBatches {
		var batchResult models.IngestionBatchResult
		err := workflow.ExecuteActivity(ingestionCtx,
			ingestion.DownloadAndValidateBatch,
			models.IngestionBatchInput{
				BatchID:   fmt.Sprintf("batch-%d", i),
				Documents: batch,
			}).Get(ctx, &batchResult)
		
		if err != nil {
			logger.Error("Batch download failed", "batch", i, "error", err)
			continue
		}
		
		allDocuments = append(allDocuments, batchResult.Success...)
		ingestionErrors = append(ingestionErrors, batchResult.Errors...)
	}

	result.Stages["ingestion"] = models.StageResult{
		ProcessedCount: len(allDocuments),
		ErrorCount:     len(ingestionErrors),
		Duration:       workflow.Now(ctx).Sub(result.StartTime),
	}

	// Stage 2: Pre-processing
	logger.Info("Stage 2: Pre-processing", "documentCount", len(allDocuments))
	preprocessingCtx := workflow.WithActivityOptions(ctx, preprocessingOptions)
	
	// Process documents in parallel
	preprocessBatches := createIngestedBatches(allDocuments, 20)
	var preprocessedDocs []models.PreprocessedDocument
	var preprocessErrors []models.ProcessingError

	selector := workflow.NewSelector(ctx)
	var futures []workflow.Future

	for i, batch := range preprocessBatches {
		future := workflow.ExecuteActivity(preprocessingCtx,
			preprocessing.PreprocessBatch,
			models.PreprocessBatchInput{
				BatchID:   fmt.Sprintf("preprocess-%d", i),
				Documents: batch,
				Options: models.PreprocessOptions{
					ChunkSize:        1000,
					ChunkOverlap:     200,
					CleanHTML:        true,
					RemovePII:        input.RemovePII,
					LanguageFilter:   input.AcceptedLanguages,
				},
			})
		
		futures = append(futures, future)
		
		// Add to selector for parallel execution monitoring
		selector.AddFuture(future, func(f workflow.Future) {
			var result models.PreprocessBatchResult
			if err := f.Get(ctx, &result); err != nil {
				logger.Error("Preprocessing batch failed", "error", err)
			} else {
				preprocessedDocs = append(preprocessedDocs, result.Success...)
				preprocessErrors = append(preprocessErrors, result.Errors...)
			}
		})
	}

	// Wait for all preprocessing to complete
	for range futures {
		selector.Select(ctx)
	}

	result.Stages["preprocessing"] = models.StageResult{
		ProcessedCount: len(preprocessedDocs),
		ErrorCount:     len(preprocessErrors),
		Duration:       workflow.Now(ctx).Sub(result.StartTime) - result.Stages["ingestion"].Duration,
	}

	// Stage 3: Model Inference
	logger.Info("Stage 3: Model Inference", "documentCount", len(preprocessedDocs))
	inferenceCtx := workflow.WithActivityOptions(ctx, inferenceOptions)
	
	// Run inference in parallel batches
	inferenceBatches := createPreprocessedBatches(preprocessedDocs, 10)
	var inferredDocs []models.InferredDocument
	var inferenceErrors []models.ProcessingError

	for i, batch := range inferenceBatches {
		var inferenceResult models.InferenceBatchResult
		err := workflow.ExecuteActivity(inferenceCtx,
			inference.RunInferenceBatch,
			models.InferenceBatchInput{
				BatchID:   fmt.Sprintf("inference-%d", i),
				Documents: batch,
				Models: models.ModelConfig{
					EmbeddingModel:   input.EmbeddingModel,
					SummaryModel:     input.SummaryModel,
					NERModel:         "spacy-en-core-web-sm",
					GenerateEmbeddings: true,
					GenerateSummary:   true,
					ExtractEntities:   true,
				},
			}).Get(ctx, &inferenceResult)
		
		if err != nil {
			logger.Error("Inference batch failed", "batch", i, "error", err)
			continue
		}
		
		inferredDocs = append(inferredDocs, inferenceResult.Success...)
		inferenceErrors = append(inferenceErrors, inferenceResult.Errors...)
	}

	prevDuration := result.Stages["ingestion"].Duration + result.Stages["preprocessing"].Duration
	result.Stages["inference"] = models.StageResult{
		ProcessedCount: len(inferredDocs),
		ErrorCount:     len(inferenceErrors),
		Duration:       workflow.Now(ctx).Sub(result.StartTime) - prevDuration,
	}

	// Stage 4: Post-processing
	logger.Info("Stage 4: Post-processing", "documentCount", len(inferredDocs))
	postprocessingCtx := workflow.WithActivityOptions(ctx, storageOptions)
	
	var processedDocs models.PostprocessedBatchResult
	err = workflow.ExecuteActivity(postprocessingCtx,
		postprocessing.PostprocessDocuments,
		models.PostprocessBatchInput{
			Documents: inferredDocs,
			Options: models.PostprocessOptions{
				BuildSearchIndex:  true,
				GenerateMetadata:  true,
				QualityThreshold:  0.7,
			},
		}).Get(ctx, &processedDocs)
	
	if err != nil {
		return nil, fmt.Errorf("post-processing failed: %w", err)
	}

	prevDuration = prevDuration + result.Stages["inference"].Duration
	result.Stages["postprocessing"] = models.StageResult{
		ProcessedCount: len(processedDocs.Documents),
		ErrorCount:     0,
		Duration:       workflow.Now(ctx).Sub(result.StartTime) - prevDuration,
	}

	// Stage 5: Storage
	logger.Info("Stage 5: Storage", "documentCount", len(processedDocs.Documents))
	storageCtx := workflow.WithActivityOptions(ctx, storageOptions)
	
	// Store in vector database
	var vectorDBResult models.VectorDBStorageResult
	err = workflow.ExecuteActivity(storageCtx,
		storage.StoreInVectorDB,
		models.VectorDBStorageInput{
			Documents:    processedDocs.Documents,
			CollectionName: input.VectorDBCollection,
			CreateIndex:   true,
		}).Get(ctx, &vectorDBResult)
	
	if err != nil {
		logger.Error("Vector DB storage failed", "error", err)
	}

	// Store documents in S3
	var s3Result models.S3StorageResult
	err = workflow.ExecuteActivity(storageCtx,
		storage.StoreInS3,
		models.S3StorageInput{
			Documents:  processedDocs.Documents,
			BucketName: input.S3Bucket,
			Prefix:     fmt.Sprintf("pipeline-runs/%s/", result.PipelineID),
		}).Get(ctx, &s3Result)
	
	if err != nil {
		logger.Error("S3 storage failed", "error", err)
	}

	// Store metadata
	var metadataResult models.MetadataStorageResult
	err = workflow.ExecuteActivity(storageCtx,
		storage.StoreMetadata,
		models.MetadataStorageInput{
			PipelineID: result.PipelineID,
			Documents:  processedDocs.Documents,
			Summary:    processedDocs.Summary,
		}).Get(ctx, &metadataResult)
	
	if err != nil {
		logger.Error("Metadata storage failed", "error", err)
	}

	prevDuration = prevDuration + result.Stages["postprocessing"].Duration
	result.Stages["storage"] = models.StageResult{
		ProcessedCount: len(vectorDBResult.StoredIDs),
		ErrorCount:     0,
		Duration:       workflow.Now(ctx).Sub(result.StartTime) - prevDuration,
	}

	// Complete pipeline result
	result.EndTime = workflow.Now(ctx)
	result.TotalDuration = result.EndTime.Sub(result.StartTime)
	result.TotalProcessed = len(processedDocs.Documents)
	result.TotalErrors = len(ingestionErrors) + len(preprocessErrors) + len(inferenceErrors)
	result.VectorDBIndices = vectorDBResult.StoredIDs
	result.S3Locations = s3Result.ObjectKeys
	result.MetadataID = metadataResult.MetadataID
	
	logger.Info("Pipeline completed",
		"pipelineID", result.PipelineID,
		"totalProcessed", result.TotalProcessed,
		"totalErrors", result.TotalErrors,
		"duration", result.TotalDuration)

	return result, nil
}

// Helper functions to create batches
func createBatches(docs []models.Document, size int) [][]models.Document {
	var batches [][]models.Document
	for i := 0; i < len(docs); i += size {
		end := i + size
		if end > len(docs) {
			end = len(docs)
		}
		batches = append(batches, docs[i:end])
	}
	return batches
}

func createIngestedBatches(docs []models.IngestedDocument, size int) [][]models.IngestedDocument {
	var batches [][]models.IngestedDocument
	for i := 0; i < len(docs); i += size {
		end := i + size
		if end > len(docs) {
			end = len(docs)
		}
		batches = append(batches, docs[i:end])
	}
	return batches
}

func createPreprocessedBatches(docs []models.PreprocessedDocument, size int) [][]models.PreprocessedDocument {
	var batches [][]models.PreprocessedDocument
	for i := 0; i < len(docs); i += size {
		end := i + size
		if end > len(docs) {
			end = len(docs)
		}
		batches = append(batches, docs[i:end])
	}
	return batches
}