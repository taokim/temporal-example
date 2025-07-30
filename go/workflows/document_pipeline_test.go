package workflows

import (
	"testing"
	"time"

	"github.com/stretchr/testify/mock"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"go.temporal.io/sdk/testsuite"
	"go.temporal.io/sdk/workflow"

	"github.com/example/temporal-rag/internal/activities/ingestion"
	"github.com/example/temporal-rag/internal/activities/preprocessing"
	"github.com/example/temporal-rag/internal/activities/inference"
	"github.com/example/temporal-rag/internal/activities/postprocessing"
	"github.com/example/temporal-rag/internal/activities/storage"
	"github.com/example/temporal-rag/internal/models"
)

type DocumentPipelineTestSuite struct {
	suite.Suite
	testsuite.WorkflowTestSuite
	env *testsuite.TestWorkflowEnvironment
}

func TestDocumentPipelineTestSuite(t *testing.T) {
	suite.Run(t, new(DocumentPipelineTestSuite))
}

func (s *DocumentPipelineTestSuite) SetupTest() {
	s.env = s.NewTestWorkflowEnvironment()
	s.env.SetTestTimeout(5 * time.Minute)
}

func (s *DocumentPipelineTestSuite) TearDownTest() {
	s.env.AssertExpectations(s.T())
}

func (s *DocumentPipelineTestSuite) TestDocumentPipelineWorkflow_Success() {
	// Test input
	input := models.PipelineInput{
		CSVPath:            "test.csv",
		S3Bucket:           "test-bucket",
		VectorDBCollection: "test-collection",
		EmbeddingModel:     "text-embedding-3-small",
		SummaryModel:       "gpt-3.5-turbo",
		RemovePII:          true,
		AcceptedLanguages:  []string{"en", "es"},
	}

	// Mock data
	csvDocuments := []models.Document{
		{
			ID:          "doc1",
			URL:         "https://example.com/doc1.pdf",
			Name:        "Test Document 1",
			Type:        "pdf",
			MetadataRaw: `{"category":"test"}`,
		},
		{
			ID:          "doc2",
			URL:         "https://example.com/doc2.docx",
			Name:        "Test Document 2",
			Type:        "docx",
			MetadataRaw: `{"category":"test"}`,
		},
	}

	ingestedDocs := []models.IngestedDocument{
		{
			Document:    csvDocuments[0],
			Content:     []byte("Test content 1"),
			ContentType: "application/pdf",
			Size:        1024,
			DownloadedAt: time.Now(),
		},
		{
			Document:    csvDocuments[1],
			Content:     []byte("Test content 2"),
			ContentType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			Size:        2048,
			DownloadedAt: time.Now(),
		},
	}

	preprocessedDocs := []models.PreprocessedDocument{
		{
			IngestedDocument: ingestedDocs[0],
			Chunks: []models.TextChunk{
				{
					ID:       "chunk1",
					Text:     "This is test content from document 1",
					Position: 0,
					Length:   100,
				},
			},
			Language:     "en",
			ProcessedAt:  time.Now(),
		},
		{
			IngestedDocument: ingestedDocs[1],
			Chunks: []models.TextChunk{
				{
					ID:       "chunk2",
					Text:     "This is test content from document 2",
					Position: 0,
					Length:   100,
				},
			},
			Language:     "en",
			ProcessedAt:  time.Now(),
		},
	}

	inferredDocs := []models.InferredDocument{
		{
			PreprocessedDocument: preprocessedDocs[0],
			InferenceResults: models.InferenceResults{
				Embeddings: []models.ChunkEmbedding{
					{
						ChunkID:   "chunk1",
						Embedding: make([]float32, 1536),
						Model:     "text-embedding-3-small",
					},
				},
				Summary: "Test summary for document 1",
				Entities: []models.Entity{
					{Text: "Test Entity", Type: "ORG", Count: 1},
				},
				ProcessedAt: time.Now(),
			},
		},
		{
			PreprocessedDocument: preprocessedDocs[1],
			InferenceResults: models.InferenceResults{
				Embeddings: []models.ChunkEmbedding{
					{
						ChunkID:   "chunk2",
						Embedding: make([]float32, 1536),
						Model:     "text-embedding-3-small",
					},
				},
				Summary: "Test summary for document 2",
				Entities: []models.Entity{
					{Text: "Another Entity", Type: "PERSON", Count: 1},
				},
				ProcessedAt: time.Now(),
			},
		},
	}

	// Mock activities
	s.env.OnActivity(ingestion.ParseCSVActivity, mock.Anything, "test.csv").
		Return(csvDocuments, nil)

	s.env.OnActivity(ingestion.DownloadAndValidateBatch, mock.Anything, mock.Anything).
		Return(func(ctx workflow.Context, input models.IngestionBatchInput) (*models.IngestionBatchResult, error) {
			return &models.IngestionBatchResult{
				BatchID: input.BatchID,
				Success: ingestedDocs,
				Errors:  []models.ProcessingError{},
			}, nil
		})

	s.env.OnActivity(preprocessing.PreprocessBatch, mock.Anything, mock.Anything).
		Return(func(ctx workflow.Context, input models.PreprocessBatchInput) (*models.PreprocessBatchResult, error) {
			return &models.PreprocessBatchResult{
				BatchID: input.BatchID,
				Success: preprocessedDocs,
				Errors:  []models.ProcessingError{},
			}, nil
		})

	s.env.OnActivity(inference.RunInferenceBatch, mock.Anything, mock.Anything).
		Return(func(ctx workflow.Context, input models.InferenceBatchInput) (*models.InferenceBatchResult, error) {
			return &models.InferenceBatchResult{
				BatchID: input.BatchID,
				Success: inferredDocs,
				Errors:  []models.ProcessingError{},
			}, nil
		})

	s.env.OnActivity(postprocessing.PostprocessDocuments, mock.Anything, mock.Anything).
		Return(&models.PostprocessedBatchResult{
			Documents: []models.PostprocessedDocument{
				{
					InferredDocument: inferredDocs[0],
					SearchIndex:      map[string]interface{}{"indexed": true},
					Metadata:         map[string]interface{}{"quality_score": 0.95},
					QualityScore:     0.95,
				},
				{
					InferredDocument: inferredDocs[1],
					SearchIndex:      map[string]interface{}{"indexed": true},
					Metadata:         map[string]interface{}{"quality_score": 0.92},
					QualityScore:     0.92,
				},
			},
			Summary: models.PipelineSummary{
				TotalDocuments:  2,
				AvgQualityScore: 0.935,
				ProcessingTime:  "2m30s",
			},
		}, nil)

	s.env.OnActivity(storage.StoreInVectorDB, mock.Anything, mock.Anything).
		Return(&models.VectorDBStorageResult{
			StoredIDs: []string{"vec1", "vec2"},
			Errors:    []models.StorageError{},
		}, nil)

	s.env.OnActivity(storage.StoreInS3, mock.Anything, mock.Anything).
		Return(&models.S3StorageResult{
			ObjectKeys: []string{"pipeline-runs/test-id/doc1.json", "pipeline-runs/test-id/doc2.json"},
			Errors:     []models.StorageError{},
		}, nil)

	s.env.OnActivity(storage.StoreMetadata, mock.Anything, mock.Anything).
		Return(&models.MetadataStorageResult{
			MetadataID: "meta-123",
			RecordCount: 2,
		}, nil)

	// Execute workflow
	s.env.ExecuteWorkflow(DocumentPipelineWorkflow, input)

	// Verify results
	require.True(s.T(), s.env.IsWorkflowCompleted())
	require.NoError(s.T(), s.env.GetWorkflowError())

	var result models.PipelineResult
	require.NoError(s.T(), s.env.GetWorkflowResult(&result))

	// Assertions
	require.NotEmpty(s.T(), result.PipelineID)
	require.Equal(s.T(), 2, result.TotalProcessed)
	require.Equal(s.T(), 0, result.TotalErrors)
	require.Len(s.T(), result.VectorDBIndices, 2)
	require.Len(s.T(), result.S3Locations, 2)
	require.Equal(s.T(), "meta-123", result.MetadataID)
	require.Contains(s.T(), result.Stages, "ingestion")
	require.Contains(s.T(), result.Stages, "preprocessing")
	require.Contains(s.T(), result.Stages, "inference")
	require.Contains(s.T(), result.Stages, "postprocessing")
	require.Contains(s.T(), result.Stages, "storage")
}

func (s *DocumentPipelineTestSuite) TestDocumentPipelineWorkflow_PartialFailure() {
	// Test input
	input := models.PipelineInput{
		CSVPath:            "test.csv",
		S3Bucket:           "test-bucket",
		VectorDBCollection: "test-collection",
		EmbeddingModel:     "text-embedding-3-small",
		SummaryModel:       "gpt-3.5-turbo",
	}

	// Mock data with one successful and one failed document
	csvDocuments := []models.Document{
		{
			ID:   "doc1",
			URL:  "https://example.com/doc1.pdf",
			Name: "Test Document 1",
			Type: "pdf",
		},
		{
			ID:   "doc2",
			URL:  "https://invalid-url.com/doc2.pdf",
			Name: "Test Document 2",
			Type: "pdf",
		},
	}

	// Mock activities with partial failures
	s.env.OnActivity(ingestion.ParseCSVActivity, mock.Anything, "test.csv").
		Return(csvDocuments, nil)

	s.env.OnActivity(ingestion.DownloadAndValidateBatch, mock.Anything, mock.Anything).
		Return(&models.IngestionBatchResult{
			BatchID: "batch-0",
			Success: []models.IngestedDocument{
				{
					Document:    csvDocuments[0],
					Content:     []byte("Test content"),
					ContentType: "application/pdf",
					Size:        1024,
				},
			},
			Errors: []models.ProcessingError{
				{
					DocumentID: "doc2",
					Stage:      "ingestion",
					Error:      "failed to download: 404 not found",
					Timestamp:  time.Now(),
				},
			},
		}, nil)

	// Continue with successful document through the pipeline
	// ... (mock remaining activities for the successful document)

	// Execute workflow
	s.env.ExecuteWorkflow(DocumentPipelineWorkflow, input)

	// Verify partial success
	require.True(s.T(), s.env.IsWorkflowCompleted())
	require.NoError(s.T(), s.env.GetWorkflowError())

	var result models.PipelineResult
	require.NoError(s.T(), s.env.GetWorkflowResult(&result))

	// Should complete with 1 success and 1 error
	require.Equal(s.T(), 1, result.TotalProcessed)
	require.Equal(s.T(), 1, result.TotalErrors)
}