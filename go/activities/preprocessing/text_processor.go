package preprocessing

import (
	"context"
	"fmt"
	"regexp"
	"strings"
	"time"
	"unicode"

	"go.temporal.io/sdk/activity"

	"github.com/example/temporal-rag/internal/models"
	"github.com/example/temporal-rag/internal/utils/document"
)

type TextProcessor struct {
	chunkSize       int
	chunkOverlap    int
	minChunkSize    int
	removePII       bool
	languageFilter  []string
}

func NewTextProcessor() *TextProcessor {
	return &TextProcessor{
		chunkSize:    1000,  // characters
		chunkOverlap: 200,   // characters
		minChunkSize: 100,   // minimum chunk size
		removePII:    false, // default to false
	}
}

// PreprocessBatch processes a batch of ingested documents
func (tp *TextProcessor) PreprocessBatch(ctx context.Context, input models.PreprocessBatchInput) (*models.PreprocessBatchResult, error) {
	logger := activity.GetLogger(ctx)
	logger.Info("Starting preprocessing batch", "batchId", input.BatchID, "documentCount", len(input.Documents))

	// Apply configuration
	if input.Config.RemovePII {
		tp.removePII = true
	}
	if len(input.Config.AcceptedLanguages) > 0 {
		tp.languageFilter = input.Config.AcceptedLanguages
	}

	result := &models.PreprocessBatchResult{
		BatchID: input.BatchID,
		Success: make([]models.PreprocessedDocument, 0, len(input.Documents)),
		Errors:  make([]models.ProcessingError, 0),
	}

	// Process each document
	for i, doc := range input.Documents {
		// Report heartbeat every 5 documents
		if i%5 == 0 {
			activity.RecordHeartbeat(ctx, fmt.Sprintf("Processing document %d/%d", i+1, len(input.Documents)))
		}

		preprocessedDoc, err := tp.processDocument(ctx, doc)
		if err != nil {
			logger.Error("Failed to preprocess document", 
				"documentId", doc.ID, 
				"error", err)
			
			result.Errors = append(result.Errors, models.ProcessingError{
				DocumentID: doc.ID,
				Stage:      "preprocessing",
				Error:      err.Error(),
				Timestamp:  time.Now(),
			})
			continue
		}

		result.Success = append(result.Success, *preprocessedDoc)
	}

	logger.Info("Preprocessing batch completed", 
		"batchId", input.BatchID,
		"success", len(result.Success),
		"errors", len(result.Errors))

	return result, nil
}

func (tp *TextProcessor) processDocument(ctx context.Context, doc models.IngestedDocument) (*models.PreprocessedDocument, error) {
	// Extract text from document based on type
	text, err := tp.extractText(doc)
	if err != nil {
		return nil, fmt.Errorf("failed to extract text: %w", err)
	}

	// Clean and normalize text
	text = tp.cleanText(text)

	// Remove PII if configured
	if tp.removePII {
		text = tp.removePIIFromText(text)
	}

	// Detect language
	language := tp.detectLanguage(text)
	
	// Filter by language if configured
	if len(tp.languageFilter) > 0 && !tp.isLanguageAccepted(language) {
		return nil, fmt.Errorf("document language '%s' not in accepted languages", language)
	}

	// Create text chunks
	chunks := tp.createChunks(doc.ID, text)

	return &models.PreprocessedDocument{
		IngestedDocument: doc,
		Chunks:          chunks,
		Language:        language,
		ProcessedAt:     time.Now(),
	}, nil
}

func (tp *TextProcessor) extractText(doc models.IngestedDocument) (string, error) {
	// Use document parser utility based on content type
	parser := document.GetParser(doc.Type)
	if parser == nil {
		return "", fmt.Errorf("no parser available for document type: %s", doc.Type)
	}

	return parser.Extract(doc.Content)
}

func (tp *TextProcessor) cleanText(text string) string {
	// Remove excessive whitespace
	text = regexp.MustCompile(`\s+`).ReplaceAllString(text, " ")
	
	// Remove control characters
	text = strings.Map(func(r rune) rune {
		if unicode.IsControl(r) && r != '\n' && r != '\t' {
			return -1
		}
		return r
	}, text)

	// Normalize line breaks
	text = strings.ReplaceAll(text, "\r\n", "\n")
	text = strings.ReplaceAll(text, "\r", "\n")
	
	// Trim
	text = strings.TrimSpace(text)

	return text
}

func (tp *TextProcessor) removePIIFromText(text string) string {
	// Email addresses
	emailRegex := regexp.MustCompile(`[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}`)
	text = emailRegex.ReplaceAllString(text, "[EMAIL]")

	// Phone numbers (US format)
	phoneRegex := regexp.MustCompile(`\b(?:\+?1[-.]?)?\(?([0-9]{3})\)?[-.]?([0-9]{3})[-.]?([0-9]{4})\b`)
	text = phoneRegex.ReplaceAllString(text, "[PHONE]")

	// SSN (US format)
	ssnRegex := regexp.MustCompile(`\b[0-9]{3}-[0-9]{2}-[0-9]{4}\b`)
	text = ssnRegex.ReplaceAllString(text, "[SSN]")

	// Credit card numbers (basic pattern)
	ccRegex := regexp.MustCompile(`\b[0-9]{4}[\s-]?[0-9]{4}[\s-]?[0-9]{4}[\s-]?[0-9]{4}\b`)
	text = ccRegex.ReplaceAllString(text, "[CREDIT_CARD]")

	return text
}

func (tp *TextProcessor) detectLanguage(text string) string {
	// Simple language detection based on character sets
	// In production, use a proper language detection library
	
	// Count Latin characters
	latinCount := 0
	totalAlpha := 0
	
	for _, r := range text {
		if unicode.IsLetter(r) {
			totalAlpha++
			if unicode.In(r, unicode.Latin) {
				latinCount++
			}
		}
	}

	if totalAlpha == 0 {
		return "unknown"
	}

	// If more than 90% Latin characters, likely English or European language
	if float64(latinCount)/float64(totalAlpha) > 0.9 {
		// Simple heuristic: check for common English words
		lowerText := strings.ToLower(text)
		englishWords := []string{"the", "and", "is", "in", "to", "of", "a", "for"}
		englishCount := 0
		
		for _, word := range englishWords {
			if strings.Contains(lowerText, " "+word+" ") {
				englishCount++
			}
		}
		
		if englishCount >= 3 {
			return "en"
		}
		
		// Check for Spanish indicators
		spanishWords := []string{"el", "la", "de", "que", "y", "en", "un", "es"}
		spanishCount := 0
		
		for _, word := range spanishWords {
			if strings.Contains(lowerText, " "+word+" ") {
				spanishCount++
			}
		}
		
		if spanishCount >= 3 {
			return "es"
		}
	}

	return "unknown"
}

func (tp *TextProcessor) isLanguageAccepted(language string) bool {
	for _, accepted := range tp.languageFilter {
		if language == accepted {
			return true
		}
	}
	return false
}

func (tp *TextProcessor) createChunks(docID string, text string) []models.TextChunk {
	var chunks []models.TextChunk
	textLen := len(text)
	chunkID := 0

	for start := 0; start < textLen; {
		// Calculate end position
		end := start + tp.chunkSize
		if end > textLen {
			end = textLen
		}

		// Try to break at a sentence or paragraph boundary
		if end < textLen {
			// Look for sentence ending
			for i := end; i > start+tp.minChunkSize && i > end-100; i-- {
				if text[i] == '.' || text[i] == '!' || text[i] == '?' {
					if i+1 < textLen && (text[i+1] == ' ' || text[i+1] == '\n') {
						end = i + 1
						break
					}
				}
			}
		}

		// Extract chunk
		chunkText := strings.TrimSpace(text[start:end])
		
		// Only add non-empty chunks that meet minimum size
		if len(chunkText) >= tp.minChunkSize {
			chunks = append(chunks, models.TextChunk{
				ID:       fmt.Sprintf("%s-chunk-%d", docID, chunkID),
				Text:     chunkText,
				Position: start,
				Length:   len(chunkText),
			})
			chunkID++
		}

		// Move to next chunk with overlap
		if end >= textLen {
			break
		}
		start = end - tp.chunkOverlap
	}

	return chunks
}