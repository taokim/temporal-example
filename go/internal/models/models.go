package models

import (
	"time"
)

// PipelineInput represents the input to the document processing pipeline
type PipelineInput struct {
	CSVPath            string
	S3Bucket           string
	VectorDBCollection string
	EmbeddingModel     string
	SummaryModel       string
	RemovePII          bool
	ChunkSize          int
	ChunkOverlap       int
	MaxConcurrency     int
}

// PipelineResult represents the overall result of the pipeline execution
type PipelineResult struct {
	PipelineID          string
	StartTime           time.Time
	EndTime             time.Time
	TotalDocuments      int
	ProcessedDocuments  int
	FailedDocuments     int
	Stages              map[string]StageResult
	WorkflowID          string
	SuccessfulDocuments int
	ProcessingErrors    []ProcessingError
	Duration            time.Duration
}

// StageResult represents the result of a single stage in the pipeline
type StageResult struct {
	StageName       string
	StartTime       time.Time
	EndTime         time.Time
	ProcessedCount  int
	FailedCount     int
	Errors          []string
}

// ProcessingError represents an error that occurred during processing
type ProcessingError struct {
	DocumentID string
	Stage      string
	Error      string
	Timestamp  time.Time
}

// Document represents a document to be processed
type Document struct {
	ID          string
	Title       string
	URL         string
	Category    string
	Size        int64
	ProcessedAt time.Time
}

// DocumentBatch represents a batch of documents for processing
type DocumentBatch struct {
	Documents []Document
	BatchID   string
	Size      int
}

// ProcessingResult represents the result of processing a single document
type ProcessingResult struct {
	DocumentID     string
	Success        bool
	ErrorMessage   string
	ProcessingTime time.Duration
	Chunks         []TextChunk
	Embeddings     [][]float32
	Summary        string
	QualityScore   float64
	Metadata       map[string]interface{}
}

// TextChunk represents a chunk of text from a document
type TextChunk struct {
	ID         string
	DocumentID string
	Text       string
	ChunkIndex int
	StartChar  int
	EndChar    int
	Metadata   map[string]interface{}
}

// ValidationResult represents the result of document validation
type ValidationResult struct {
	IsValid bool
	Errors  []string
}

// EmbeddingResult represents the result of embedding generation
type EmbeddingResult struct {
	Embeddings [][]float32
	Model      string
	Dimensions int
}

// SummaryResult represents the result of document summarization
type SummaryResult struct {
	Summary  string
	Keywords []string
	Model    string
}