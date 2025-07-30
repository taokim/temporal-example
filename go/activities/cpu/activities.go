package cpu

import (
	"context"
	"fmt"
	"runtime"
	"strings"
	"time"

	"go.temporal.io/sdk/activity"
)

// CPUBoundActivities defines CPU-intensive activities
type CPUBoundActivities struct {
	cpuCores int
}

// NewCPUBoundActivities creates a new instance
func NewCPUBoundActivities() *CPUBoundActivities {
	return &CPUBoundActivities{
		cpuCores: runtime.NumCPU(),
	}
}

// TextProcessingResult represents the result of text preprocessing
type TextProcessingResult struct {
	ProcessedText string
	WordCount     int
	Language      string
	ProcessingTime time.Duration
}

// ValidationResult represents document validation result
type ValidationResult struct {
	IsValid      bool
	Issues       []string
	Structure    string
	ValidatedAt  time.Time
}

// PreprocessText performs CPU-intensive text preprocessing
func (a *CPUBoundActivities) PreprocessText(ctx context.Context, documentID string, text string) (*TextProcessingResult, error) {
	activity.GetLogger(ctx).Info("CPU-bound text preprocessing started",
		"documentID", documentID,
		"cpuCores", a.cpuCores)

	startTime := time.Now()

	// Simulate CPU-intensive text processing
	processedText := strings.TrimSpace(text)
	processedText = strings.ReplaceAll(processedText, "\r\n", "\n")
	processedText = strings.ReplaceAll(processedText, "\t", " ")
	
	// Remove multiple spaces
	for strings.Contains(processedText, "  ") {
		processedText = strings.ReplaceAll(processedText, "  ", " ")
	}

	// Count words (CPU-intensive)
	words := strings.Fields(processedText)
	wordCount := len(words)

	// Simple language detection (CPU-intensive pattern matching)
	language := "en"
	if strings.Contains(processedText, "der") || strings.Contains(processedText, "die") || strings.Contains(processedText, "das") {
		language = "de"
	} else if strings.Contains(processedText, "le") || strings.Contains(processedText, "la") || strings.Contains(processedText, "les") {
		language = "fr"
	}

	return &TextProcessingResult{
		ProcessedText:  processedText,
		WordCount:      wordCount,
		Language:       language,
		ProcessingTime: time.Since(startTime),
	}, nil
}

// ValidateDocumentStructure validates document structure using CPU
func (a *CPUBoundActivities) ValidateDocumentStructure(ctx context.Context, documentID string, content string) (*ValidationResult, error) {
	activity.GetLogger(ctx).Info("Validating document structure",
		"documentID", documentID)

	issues := []string{}
	isValid := true

	// CPU-intensive validation checks
	if len(content) < 10 {
		issues = append(issues, "Content too short")
		isValid = false
	}

	if !strings.Contains(content, " ") {
		issues = append(issues, "No spaces found in content")
		isValid = false
	}

	// Detect structure
	structure := "plain"
	if strings.Contains(content, "{") && strings.Contains(content, "}") {
		structure = "json"
	} else if strings.Contains(content, "<") && strings.Contains(content, ">") {
		structure = "xml"
	} else if strings.Contains(content, "#") || strings.Contains(content, "##") {
		structure = "markdown"
	}

	return &ValidationResult{
		IsValid:     isValid,
		Issues:      issues,
		Structure:   structure,
		ValidatedAt: time.Now(),
	}, nil
}

// ExtractTextFromDocument extracts text from binary document data
func (a *CPUBoundActivities) ExtractTextFromDocument(ctx context.Context, documentID string, documentData []byte) (string, error) {
	activity.GetLogger(ctx).Info("Extracting text from document",
		"documentID", documentID,
		"dataSize", len(documentData),
		"cpuCores", a.cpuCores)

	// Simulate CPU-intensive text extraction
	// In real implementation, this would use libraries like pdftotext, docx parsers, etc.
	extractedText := string(documentData)
	
	// Simple extraction based on file type detection
	if len(documentData) > 4 && string(documentData[:4]) == "%PDF" {
		extractedText = fmt.Sprintf("Extracted text from PDF document %s (simulated)", documentID)
	} else if len(documentData) > 2 && documentData[0] == 0x50 && documentData[1] == 0x4B {
		extractedText = fmt.Sprintf("Extracted text from DOCX document %s (simulated)", documentID)
	}

	return extractedText, nil
}

// TokenizeText performs text tokenization
func (a *CPUBoundActivities) TokenizeText(ctx context.Context, documentID string, text string) ([]string, error) {
	activity.GetLogger(ctx).Info("Tokenizing text",
		"documentID", documentID,
		"textLength", len(text))

	// Simple word-based tokenization (CPU-intensive for large texts)
	tokens := strings.Fields(text)
	
	// Additional processing on tokens
	processedTokens := make([]string, 0, len(tokens))
	for _, token := range tokens {
		// Remove punctuation
		cleaned := strings.Trim(token, ".,!?;:'\"")
		if cleaned != "" {
			processedTokens = append(processedTokens, strings.ToLower(cleaned))
		}
	}

	return processedTokens, nil
}

// CompressDocument compresses document data
func (a *CPUBoundActivities) CompressDocument(ctx context.Context, documentID string, documentData []byte) ([]byte, error) {
	activity.GetLogger(ctx).Info("Compressing document",
		"documentID", documentID,
		"originalSize", len(documentData))

	// Simulate CPU-intensive compression
	// In real implementation, would use compression libraries
	compressed := []byte(fmt.Sprintf("COMPRESSED[%s]:%d", documentID, len(documentData)))

	activity.GetLogger(ctx).Info("Document compressed",
		"documentID", documentID,
		"compressedSize", len(compressed),
		"ratio", float64(len(compressed))/float64(len(documentData)))

	return compressed, nil
}