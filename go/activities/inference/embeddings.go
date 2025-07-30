package inference

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

type EmbeddingGenerator struct {
	llmServiceURL string
	httpClient    *http.Client
	useMockService bool
}

func NewEmbeddingGenerator(llmServiceURL string, useMockService bool) *EmbeddingGenerator {
	return &EmbeddingGenerator{
		llmServiceURL: llmServiceURL,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
		useMockService: useMockService,
	}
}

// RunInferenceBatch processes a batch of documents through the inference pipeline
func (eg *EmbeddingGenerator) RunInferenceBatch(ctx context.Context, input models.InferenceBatchInput) (*models.InferenceBatchResult, error) {
	logger := activity.GetLogger(ctx)
	logger.Info("Starting inference batch", "batchId", input.BatchID, "documentCount", len(input.Documents))

	result := &models.InferenceBatchResult{
		BatchID: input.BatchID,
		Success: make([]models.InferredDocument, 0, len(input.Documents)),
		Errors:  make([]models.ProcessingError, 0),
	}

	// Process each document
	for i, doc := range input.Documents {
		// Report heartbeat every 5 documents
		if i%5 == 0 {
			activity.RecordHeartbeat(ctx, fmt.Sprintf("Processing document %d/%d", i+1, len(input.Documents)))
		}

		inferredDoc, err := eg.processDocument(ctx, doc, input.Models)
		if err != nil {
			logger.Error("Failed to process document", 
				"documentId", doc.ID, 
				"error", err)
			
			result.Errors = append(result.Errors, models.ProcessingError{
				DocumentID: doc.ID,
				Stage:      "inference",
				Error:      err.Error(),
				Timestamp:  time.Now(),
			})
			continue
		}

		result.Success = append(result.Success, *inferredDoc)
	}

	logger.Info("Inference batch completed", 
		"batchId", input.BatchID,
		"success", len(result.Success),
		"errors", len(result.Errors))

	return result, nil
}

func (eg *EmbeddingGenerator) processDocument(ctx context.Context, doc models.PreprocessedDocument, config models.ModelConfig) (*models.InferredDocument, error) {
	inferredDoc := &models.InferredDocument{
		PreprocessedDocument: doc,
		InferenceResults:     models.InferenceResults{},
	}

	// Generate embeddings for each chunk
	if config.GenerateEmbeddings {
		embeddings := make([]models.ChunkEmbedding, 0, len(doc.Chunks))
		
		for i, chunk := range doc.Chunks {
			embedding, err := eg.generateEmbedding(ctx, chunk.Text, config.EmbeddingModel)
			if err != nil {
				return nil, fmt.Errorf("failed to generate embedding for chunk %d: %w", i, err)
			}
			
			embeddings = append(embeddings, models.ChunkEmbedding{
				ChunkID:   chunk.ID,
				Embedding: embedding,
				Model:     config.EmbeddingModel,
			})
		}
		
		inferredDoc.InferenceResults.Embeddings = embeddings
	}

	// Generate summary
	if config.GenerateSummary {
		fullText := ""
		for _, chunk := range doc.Chunks {
			fullText += chunk.Text + "\n"
		}
		
		summary, err := eg.generateSummary(ctx, fullText, config.SummaryModel)
		if err != nil {
			return nil, fmt.Errorf("failed to generate summary: %w", err)
		}
		
		inferredDoc.InferenceResults.Summary = summary
	}

	// Extract entities (mock for now)
	if config.ExtractEntities {
		entities := eg.extractEntities(ctx, doc.Chunks)
		inferredDoc.InferenceResults.Entities = entities
	}

	inferredDoc.InferenceResults.ProcessedAt = time.Now()

	return inferredDoc, nil
}

func (eg *EmbeddingGenerator) generateEmbedding(ctx context.Context, text string, model string) ([]float32, error) {
	if eg.useMockService {
		return eg.generateMockEmbedding(ctx, text, model)
	}

	// Real OpenAI API call would go here
	return eg.generateMockEmbedding(ctx, text, model)
}

func (eg *EmbeddingGenerator) generateMockEmbedding(ctx context.Context, text string, model string) ([]float32, error) {
	reqBody := map[string]interface{}{
		"model": model,
		"input": text,
	}

	jsonData, err := json.Marshal(reqBody)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, "POST", 
		eg.llmServiceURL+"/v1/embeddings", 
		bytes.NewBuffer(jsonData))
	if err != nil {
		return nil, err
	}

	req.Header.Set("Content-Type", "application/json")

	resp, err := eg.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("embedding service returned status %d", resp.StatusCode)
	}

	var response struct {
		Data []struct {
			Embedding []float32 `json:"embedding"`
		} `json:"data"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return nil, err
	}

	if len(response.Data) == 0 {
		return nil, fmt.Errorf("no embeddings returned")
	}

	return response.Data[0].Embedding, nil
}

func (eg *EmbeddingGenerator) generateSummary(ctx context.Context, text string, model string) (string, error) {
	if eg.useMockService {
		return eg.generateMockSummary(ctx, text, model)
	}

	// Real LLM API call would go here
	return eg.generateMockSummary(ctx, text, model)
}

func (eg *EmbeddingGenerator) generateMockSummary(ctx context.Context, text string, model string) (string, error) {
	prompt := fmt.Sprintf("Please summarize the following document in 2-3 sentences:\n\n%s", text)
	
	reqBody := map[string]interface{}{
		"model": model,
		"messages": []map[string]string{
			{
				"role":    "system",
				"content": "You are a helpful assistant that creates concise summaries.",
			},
			{
				"role":    "user",
				"content": prompt,
			},
		},
	}

	jsonData, err := json.Marshal(reqBody)
	if err != nil {
		return "", err
	}

	req, err := http.NewRequestWithContext(ctx, "POST",
		eg.llmServiceURL+"/v1/chat/completions",
		bytes.NewBuffer(jsonData))
	if err != nil {
		return "", err
	}

	req.Header.Set("Content-Type", "application/json")

	resp, err := eg.httpClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("summary service returned status %d", resp.StatusCode)
	}

	var response struct {
		Choices []struct {
			Message struct {
				Content string `json:"content"`
			} `json:"message"`
		} `json:"choices"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return "", err
	}

	if len(response.Choices) == 0 {
		return "", fmt.Errorf("no summary returned")
	}

	return response.Choices[0].Message.Content, nil
}

func (eg *EmbeddingGenerator) extractEntities(ctx context.Context, chunks []models.TextChunk) []models.Entity {
	// Mock entity extraction
	// In real implementation, this would use spaCy or similar NER service
	entities := []models.Entity{
		{
			Text:  "John Doe",
			Type:  "PERSON",
			Count: 3,
		},
		{
			Text:  "Acme Corporation",
			Type:  "ORG",
			Count: 5,
		},
		{
			Text:  "San Francisco",
			Type:  "LOC",
			Count: 2,
		},
		{
			Text:  "2024-01-15",
			Type:  "DATE",
			Count: 1,
		},
	}
	
	return entities
}