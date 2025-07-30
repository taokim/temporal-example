package storage

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"time"

	_ "github.com/lib/pq"
	"go.temporal.io/sdk/activity"

	"github.com/example/temporal-rag/internal/models"
)

type MetadataStore struct {
	db *sql.DB
}

func NewMetadataStore(dbHost, dbPort, dbName, dbUser, dbPassword string) (*MetadataStore, error) {
	// Build connection string
	connStr := fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=disable",
		dbHost, dbPort, dbUser, dbPassword, dbName)

	// Open database connection
	db, err := sql.Open("postgres", connStr)
	if err != nil {
		return nil, fmt.Errorf("failed to open database: %w", err)
	}

	// Test connection
	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}

	// Set connection pool settings
	db.SetMaxOpenConns(25)
	db.SetMaxIdleConns(5)
	db.SetConnMaxLifetime(5 * time.Minute)

	return &MetadataStore{db: db}, nil
}

// StoreMetadata stores pipeline metadata in PostgreSQL
func (ms *MetadataStore) StoreMetadata(ctx context.Context, input models.MetadataStorageInput) (*models.MetadataStorageResult, error) {
	logger := activity.GetLogger(ctx)
	logger.Info("Storing pipeline metadata", "pipelineId", input.PipelineID)

	// Begin transaction
	tx, err := ms.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer tx.Rollback()

	// Store pipeline metadata
	metadataID, err := ms.storePipelineMetadata(ctx, tx, input)
	if err != nil {
		return nil, fmt.Errorf("failed to store pipeline metadata: %w", err)
	}

	// Store document metadata
	recordCount := 0
	for i, doc := range input.Documents {
		// Report heartbeat
		if i%10 == 0 {
			activity.RecordHeartbeat(ctx, fmt.Sprintf("Storing document metadata %d/%d", i+1, len(input.Documents)))
		}

		err := ms.storeDocumentMetadata(ctx, tx, metadataID, doc)
		if err != nil {
			logger.Error("Failed to store document metadata",
				"documentId", doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.ID,
				"error", err)
			continue
		}
		recordCount++
	}

	// Store processing errors if any
	if len(input.ProcessingErrors) > 0 {
		err = ms.storeProcessingErrors(ctx, tx, metadataID, input.ProcessingErrors)
		if err != nil {
			logger.Error("Failed to store processing errors", "error", err)
		}
	}

	// Commit transaction
	if err := tx.Commit(); err != nil {
		return nil, fmt.Errorf("failed to commit transaction: %w", err)
	}

	logger.Info("Metadata storage completed",
		"metadataId", metadataID,
		"recordCount", recordCount)

	return &models.MetadataStorageResult{
		MetadataID:  metadataID,
		RecordCount: recordCount,
	}, nil
}

func (ms *MetadataStore) storePipelineMetadata(ctx context.Context, tx *sql.Tx, input models.MetadataStorageInput) (string, error) {
	query := `
		INSERT INTO pipeline_runs (pipeline_id, started_at, completed_at, total_documents, 
		                          successful_documents, failed_documents, configuration)
		VALUES ($1, $2, $3, $4, $5, $6, $7)
		RETURNING id
	`

	// Serialize configuration
	configJSON, err := json.Marshal(input.Config)
	if err != nil {
		return "", fmt.Errorf("failed to serialize configuration: %w", err)
	}

	var metadataID string
	err = tx.QueryRowContext(ctx, query,
		input.PipelineID,
		input.StartTime,
		input.EndTime,
		len(input.Documents),
		len(input.Documents), // Assuming all successful for now
		len(input.ProcessingErrors),
		configJSON,
	).Scan(&metadataID)

	if err != nil {
		return "", fmt.Errorf("failed to insert pipeline metadata: %w", err)
	}

	return metadataID, nil
}

func (ms *MetadataStore) storeDocumentMetadata(ctx context.Context, tx *sql.Tx, pipelineRunID string, doc models.PostprocessedDocument) error {
	query := `
		INSERT INTO document_metadata (pipeline_run_id, document_id, document_name, document_type,
		                              document_url, language, quality_score, summary, entity_count,
		                              chunk_count, s3_location, vector_ids, metadata, processed_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)
	`

	// Prepare data
	docID := doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.ID
	docName := doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.Name
	docType := doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.Type
	docURL := doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.URL
	language := doc.InferredDocument.PreprocessedDocument.Language
	summary := doc.InferredDocument.InferenceResults.Summary
	entityCount := len(doc.InferredDocument.InferenceResults.Entities)
	chunkCount := len(doc.InferredDocument.PreprocessedDocument.Chunks)

	// Serialize metadata
	metadataJSON, err := json.Marshal(doc.Metadata)
	if err != nil {
		return fmt.Errorf("failed to serialize metadata: %w", err)
	}

	// Extract vector IDs
	vectorIDs := make([]string, 0)
	for _, embedding := range doc.InferredDocument.InferenceResults.Embeddings {
		vectorIDs = append(vectorIDs, embedding.ChunkID)
	}
	vectorIDsJSON, err := json.Marshal(vectorIDs)
	if err != nil {
		return fmt.Errorf("failed to serialize vector IDs: %w", err)
	}

	_, err = tx.ExecContext(ctx, query,
		pipelineRunID,
		docID,
		docName,
		docType,
		docURL,
		language,
		doc.QualityScore,
		summary,
		entityCount,
		chunkCount,
		"", // S3 location will be updated later
		vectorIDsJSON,
		metadataJSON,
		time.Now(),
	)

	return err
}

func (ms *MetadataStore) storeProcessingErrors(ctx context.Context, tx *sql.Tx, pipelineRunID string, errors []models.ProcessingError) error {
	query := `
		INSERT INTO processing_errors (pipeline_run_id, document_id, stage, error_message, occurred_at)
		VALUES ($1, $2, $3, $4, $5)
	`

	stmt, err := tx.PrepareContext(ctx, query)
	if err != nil {
		return fmt.Errorf("failed to prepare statement: %w", err)
	}
	defer stmt.Close()

	for _, procErr := range errors {
		_, err := stmt.ExecContext(ctx,
			pipelineRunID,
			procErr.DocumentID,
			procErr.Stage,
			procErr.Error,
			procErr.Timestamp,
		)
		if err != nil {
			return fmt.Errorf("failed to insert error record: %w", err)
		}
	}

	return nil
}

// Close closes the database connection
func (ms *MetadataStore) Close() error {
	return ms.db.Close()
}