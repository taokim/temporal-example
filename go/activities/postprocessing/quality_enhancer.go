package postprocessing

import (
	"context"
	"fmt"
	"math"
	"strings"
	"time"

	"go.temporal.io/sdk/activity"

	"github.com/example/temporal-rag/internal/models"
)

type QualityEnhancer struct {
	minQualityScore float64
}

func NewQualityEnhancer() *QualityEnhancer {
	return &QualityEnhancer{
		minQualityScore: 0.7,
	}
}

// PostprocessDocuments performs quality enhancement and metadata enrichment
func (qe *QualityEnhancer) PostprocessDocuments(ctx context.Context, input models.PostprocessBatchInput) (*models.PostprocessedBatchResult, error) {
	logger := activity.GetLogger(ctx)
	logger.Info("Starting postprocessing", "documentCount", len(input.Documents))

	result := &models.PostprocessedBatchResult{
		Documents: make([]models.PostprocessedDocument, 0, len(input.Documents)),
	}

	totalQualityScore := 0.0
	totalProcessingTime := time.Now()

	// Process each document
	for i, doc := range input.Documents {
		// Report heartbeat
		if i%5 == 0 {
			activity.RecordHeartbeat(ctx, fmt.Sprintf("Postprocessing document %d/%d", i+1, len(input.Documents)))
		}

		postprocessedDoc := qe.enhanceDocument(doc)
		result.Documents = append(result.Documents, postprocessedDoc)
		totalQualityScore += postprocessedDoc.QualityScore
	}

	// Calculate summary statistics
	result.Summary = models.PipelineSummary{
		TotalDocuments:  len(input.Documents),
		AvgQualityScore: totalQualityScore / float64(len(input.Documents)),
		ProcessingTime:  time.Since(totalProcessingTime).String(),
	}

	logger.Info("Postprocessing completed", 
		"documentsProcessed", len(result.Documents),
		"avgQualityScore", result.Summary.AvgQualityScore)

	return result, nil
}

func (qe *QualityEnhancer) enhanceDocument(doc models.InferredDocument) models.PostprocessedDocument {
	// Calculate quality score
	qualityScore := qe.calculateQualityScore(doc)

	// Build search index metadata
	searchIndex := qe.buildSearchIndex(doc)

	// Enrich metadata
	enrichedMetadata := qe.enrichMetadata(doc, qualityScore)

	return models.PostprocessedDocument{
		InferredDocument: doc,
		SearchIndex:      searchIndex,
		Metadata:         enrichedMetadata,
		QualityScore:     qualityScore,
	}
}

func (qe *QualityEnhancer) calculateQualityScore(doc models.InferredDocument) float64 {
	score := 0.0
	weights := 0.0

	// Content length score (20% weight)
	contentLength := 0
	for _, chunk := range doc.PreprocessedDocument.Chunks {
		contentLength += len(chunk.Text)
	}
	if contentLength > 0 {
		// Normalize to 0-1 scale (assuming ideal length is 5000 chars)
		lengthScore := math.Min(float64(contentLength)/5000.0, 1.0)
		score += lengthScore * 0.2
		weights += 0.2
	}

	// Embedding coverage score (30% weight)
	if len(doc.InferenceResults.Embeddings) > 0 {
		coverage := float64(len(doc.InferenceResults.Embeddings)) / float64(len(doc.PreprocessedDocument.Chunks))
		score += coverage * 0.3
		weights += 0.3
	}

	// Summary quality score (20% weight)
	if doc.InferenceResults.Summary != "" {
		// Simple heuristic: good summaries are 50-200 words
		wordCount := len(strings.Fields(doc.InferenceResults.Summary))
		summaryScore := 0.0
		if wordCount >= 50 && wordCount <= 200 {
			summaryScore = 1.0
		} else if wordCount < 50 {
			summaryScore = float64(wordCount) / 50.0
		} else {
			summaryScore = math.Max(0, 1.0-(float64(wordCount-200)/200.0))
		}
		score += summaryScore * 0.2
		weights += 0.2
	}

	// Entity extraction score (15% weight)
	if len(doc.InferenceResults.Entities) > 0 {
		// More entities generally means better extraction
		entityScore := math.Min(float64(len(doc.InferenceResults.Entities))/10.0, 1.0)
		score += entityScore * 0.15
		weights += 0.15
	}

	// Language detection confidence (15% weight)
	if doc.PreprocessedDocument.Language != "unknown" {
		score += 1.0 * 0.15
		weights += 0.15
	}

	// Normalize score
	if weights > 0 {
		return score / weights
	}
	return 0.5 // Default middle score
}

func (qe *QualityEnhancer) buildSearchIndex(doc models.InferredDocument) map[string]interface{} {
	index := make(map[string]interface{})

	// Basic document info
	index["doc_id"] = doc.PreprocessedDocument.IngestedDocument.Document.ID
	index["doc_name"] = doc.PreprocessedDocument.IngestedDocument.Document.Name
	index["doc_type"] = doc.PreprocessedDocument.IngestedDocument.Document.Type
	index["language"] = doc.PreprocessedDocument.Language

	// Content metadata
	index["chunk_count"] = len(doc.PreprocessedDocument.Chunks)
	index["has_embeddings"] = len(doc.InferenceResults.Embeddings) > 0
	index["has_summary"] = doc.InferenceResults.Summary != ""

	// Entity index
	if len(doc.InferenceResults.Entities) > 0 {
		entityIndex := make(map[string][]string)
		for _, entity := range doc.InferenceResults.Entities {
			if _, exists := entityIndex[entity.Type]; !exists {
				entityIndex[entity.Type] = []string{}
			}
			entityIndex[entity.Type] = append(entityIndex[entity.Type], entity.Text)
		}
		index["entities"] = entityIndex
	}

	// Timestamps
	index["downloaded_at"] = doc.PreprocessedDocument.IngestedDocument.DownloadedAt
	index["processed_at"] = doc.PreprocessedDocument.ProcessedAt
	index["inference_at"] = doc.InferenceResults.ProcessedAt

	// Custom metadata from original document
	if doc.PreprocessedDocument.IngestedDocument.Document.Metadata != nil {
		for k, v := range doc.PreprocessedDocument.IngestedDocument.Document.Metadata {
			index["custom_"+k] = v
		}
	}

	return index
}

func (qe *QualityEnhancer) enrichMetadata(doc models.InferredDocument, qualityScore float64) map[string]interface{} {
	metadata := make(map[string]interface{})

	// Quality metrics
	metadata["quality_score"] = qualityScore
	metadata["quality_tier"] = qe.getQualityTier(qualityScore)

	// Processing statistics
	stats := make(map[string]interface{})
	stats["total_chunks"] = len(doc.PreprocessedDocument.Chunks)
	stats["total_embeddings"] = len(doc.InferenceResults.Embeddings)
	stats["total_entities"] = len(doc.InferenceResults.Entities)
	stats["content_size"] = doc.PreprocessedDocument.IngestedDocument.Size
	stats["language"] = doc.PreprocessedDocument.Language
	metadata["statistics"] = stats

	// Summary info
	if doc.InferenceResults.Summary != "" {
		summaryInfo := make(map[string]interface{})
		summaryInfo["length"] = len(doc.InferenceResults.Summary)
		summaryInfo["word_count"] = len(strings.Fields(doc.InferenceResults.Summary))
		metadata["summary_info"] = summaryInfo
	}

	// Entity summary
	if len(doc.InferenceResults.Entities) > 0 {
		entitySummary := make(map[string]int)
		for _, entity := range doc.InferenceResults.Entities {
			entitySummary[entity.Type] += entity.Count
		}
		metadata["entity_summary"] = entitySummary
	}

	// Processing timestamps
	timestamps := make(map[string]string)
	timestamps["downloaded"] = doc.PreprocessedDocument.IngestedDocument.DownloadedAt.Format(time.RFC3339)
	timestamps["preprocessed"] = doc.PreprocessedDocument.ProcessedAt.Format(time.RFC3339)
	timestamps["inference_completed"] = doc.InferenceResults.ProcessedAt.Format(time.RFC3339)
	timestamps["postprocessed"] = time.Now().Format(time.RFC3339)
	metadata["timestamps"] = timestamps

	// Recommendations
	recommendations := qe.generateRecommendations(doc, qualityScore)
	if len(recommendations) > 0 {
		metadata["recommendations"] = recommendations
	}

	return metadata
}

func (qe *QualityEnhancer) getQualityTier(score float64) string {
	if score >= 0.9 {
		return "excellent"
	} else if score >= 0.8 {
		return "good"
	} else if score >= 0.7 {
		return "acceptable"
	} else if score >= 0.5 {
		return "needs_improvement"
	}
	return "poor"
}

func (qe *QualityEnhancer) generateRecommendations(doc models.InferredDocument, qualityScore float64) []string {
	var recommendations []string

	// Low quality score
	if qualityScore < qe.minQualityScore {
		recommendations = append(recommendations, "Consider reprocessing with different parameters")
	}

	// Missing embeddings
	if len(doc.InferenceResults.Embeddings) == 0 {
		recommendations = append(recommendations, "No embeddings generated - check model configuration")
	}

	// Missing summary
	if doc.InferenceResults.Summary == "" {
		recommendations = append(recommendations, "No summary generated - document may be too short or model unavailable")
	}

	// Language issues
	if doc.PreprocessedDocument.Language == "unknown" {
		recommendations = append(recommendations, "Language detection failed - consider manual language setting")
	}

	// Small content
	totalContent := 0
	for _, chunk := range doc.PreprocessedDocument.Chunks {
		totalContent += len(chunk.Text)
	}
	if totalContent < 500 {
		recommendations = append(recommendations, "Document content is very short - verify extraction quality")
	}

	return recommendations
}