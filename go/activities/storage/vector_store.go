package storage

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"go.temporal.io/sdk/activity"

	"github.com/example/temporal-rag/internal/models"
)

type VectorStore struct {
	chromaDBURL string
	httpClient  *http.Client
}

func NewVectorStore(chromaDBURL string) *VectorStore {
	return &VectorStore{
		chromaDBURL: chromaDBURL,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// StoreInVectorDB stores document embeddings in ChromaDB
func (vs *VectorStore) StoreInVectorDB(ctx context.Context, input models.VectorDBStorageInput) (*models.VectorDBStorageResult, error) {
	logger := activity.GetLogger(ctx)
	logger.Info("Storing documents in vector database", 
		"collection", input.Collection,
		"documentCount", len(input.Documents))

	result := &models.VectorDBStorageResult{
		StoredIDs: make([]string, 0),
		Errors:    make([]models.StorageError, 0),
	}

	// Create or get collection
	err := vs.ensureCollection(ctx, input.Collection)
	if err != nil {
		return nil, fmt.Errorf("failed to ensure collection: %w", err)
	}

	// Store each document's embeddings
	for i, doc := range input.Documents {
		// Report heartbeat
		if i%5 == 0 {
			activity.RecordHeartbeat(ctx, fmt.Sprintf("Storing document %d/%d", i+1, len(input.Documents)))
		}

		vectorIDs, err := vs.storeDocumentVectors(ctx, input.Collection, doc)
		if err != nil {
			logger.Error("Failed to store document vectors",
				"documentId", doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.ID,
				"error", err)
			
			result.Errors = append(result.Errors, models.StorageError{
				DocumentID: doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.ID,
				Error:      err.Error(),
				Timestamp:  time.Now(),
			})
			continue
		}

		result.StoredIDs = append(result.StoredIDs, vectorIDs...)
	}

	logger.Info("Vector storage completed",
		"totalStored", len(result.StoredIDs),
		"errors", len(result.Errors))

	return result, nil
}

func (vs *VectorStore) ensureCollection(ctx context.Context, collection string) error {
	// Create collection if it doesn't exist
	createReq := map[string]interface{}{
		"name": collection,
		"metadata": map[string]string{
			"created_by": "temporal-rag-pipeline",
			"created_at": time.Now().Format(time.RFC3339),
		},
	}

	jsonData, err := json.Marshal(createReq)
	if err != nil {
		return err
	}

	req, err := http.NewRequestWithContext(ctx, "POST",
		fmt.Sprintf("%s/api/v1/collections", vs.chromaDBURL),
		bytes.NewBuffer(jsonData))
	if err != nil {
		return err
	}

	req.Header.Set("Content-Type", "application/json")

	resp, err := vs.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	// ChromaDB returns 200 for existing collection, 201 for new
	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusCreated {
		return fmt.Errorf("failed to create collection: status %d", resp.StatusCode)
	}

	return nil
}

func (vs *VectorStore) storeDocumentVectors(ctx context.Context, collection string, doc models.PostprocessedDocument) ([]string, error) {
	var storedIDs []string

	// Prepare batch add request
	ids := make([]string, 0)
	embeddings := make([][]float32, 0)
	metadatas := make([]map[string]interface{}, 0)
	documents := make([]string, 0)

	for _, embedding := range doc.InferredDocument.InferenceResults.Embeddings {
		// Find corresponding chunk
		var chunkText string
		for _, chunk := range doc.InferredDocument.PreprocessedDocument.Chunks {
			if chunk.ID == embedding.ChunkID {
				chunkText = chunk.Text
				break
			}
		}

		if chunkText == "" {
			continue // Skip if chunk not found
		}

		// Prepare metadata
		metadata := map[string]interface{}{
			"document_id":   doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.ID,
			"document_name": doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.Name,
			"document_type": doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.Type,
			"chunk_id":      embedding.ChunkID,
			"quality_score": doc.QualityScore,
			"language":      doc.InferredDocument.PreprocessedDocument.Language,
		}

		// Add custom metadata
		if doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.Metadata != nil {
			for k, v := range doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.Metadata {
				metadata["custom_"+k] = v
			}
		}

		ids = append(ids, embedding.ChunkID)
		embeddings = append(embeddings, embedding.Embedding)
		metadatas = append(metadatas, metadata)
		documents = append(documents, chunkText)
	}

	if len(ids) == 0 {
		return storedIDs, nil // No embeddings to store
	}

	// Create add request
	addReq := map[string]interface{}{
		"ids":        ids,
		"embeddings": embeddings,
		"metadatas":  metadatas,
		"documents":  documents,
	}

	jsonData, err := json.Marshal(addReq)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, "POST",
		fmt.Sprintf("%s/api/v1/collections/%s/add", vs.chromaDBURL, collection),
		bytes.NewBuffer(jsonData))
	if err != nil {
		return nil, err
	}

	req.Header.Set("Content-Type", "application/json")

	resp, err := vs.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("failed to add embeddings: status %d", resp.StatusCode)
	}

	return ids, nil
}