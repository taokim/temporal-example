package ingestion

import (
	"context"
	"encoding/csv"
	"encoding/json"
	"fmt"
	"os"
	"strings"

	"go.temporal.io/sdk/activity"

	"github.com/example/temporal-rag/internal/models"
)

// ParseCSVActivity reads a CSV file and returns a list of documents
func ParseCSVActivity(ctx context.Context, csvPath string) ([]models.Document, error) {
	logger := activity.GetLogger(ctx)
	logger.Info("Parsing CSV file", "path", csvPath)

	file, err := os.Open(csvPath)
	if err != nil {
		return nil, fmt.Errorf("failed to open CSV file: %w", err)
	}
	defer file.Close()

	reader := csv.NewReader(file)
	
	// Read header
	header, err := reader.Read()
	if err != nil {
		return nil, fmt.Errorf("failed to read CSV header: %w", err)
	}

	// Validate header
	expectedHeaders := []string{"url", "name", "type", "metadata"}
	if !validateHeaders(header, expectedHeaders) {
		return nil, fmt.Errorf("invalid CSV headers: expected %v, got %v", expectedHeaders, header)
	}

	// Read records
	var documents []models.Document
	lineNum := 1
	
	for {
		record, err := reader.Read()
		if err != nil {
			if err.Error() == "EOF" {
				break
			}
			return nil, fmt.Errorf("failed to read CSV record at line %d: %w", lineNum+1, err)
		}
		lineNum++

		// Report heartbeat every 100 documents
		if lineNum%100 == 0 {
			activity.RecordHeartbeat(ctx, fmt.Sprintf("Processed %d lines", lineNum))
		}

		// Parse document
		doc, err := parseDocument(record)
		if err != nil {
			logger.Warn("Failed to parse document, skipping", 
				"line", lineNum, 
				"error", err)
			continue
		}

		// Generate unique ID
		doc.ID = fmt.Sprintf("doc-%d-%s", lineNum, strings.ReplaceAll(doc.Name, " ", "-"))
		
		documents = append(documents, doc)
	}

	logger.Info("CSV parsing completed", 
		"totalLines", lineNum,
		"documentsFound", len(documents))

	return documents, nil
}

func validateHeaders(actual, expected []string) bool {
	if len(actual) != len(expected) {
		return false
	}
	for i, h := range expected {
		if strings.TrimSpace(actual[i]) != h {
			return false
		}
	}
	return true
}

func parseDocument(record []string) (models.Document, error) {
	if len(record) < 4 {
		return models.Document{}, fmt.Errorf("invalid record: expected 4 fields, got %d", len(record))
	}

	doc := models.Document{
		URL:         strings.TrimSpace(record[0]),
		Name:        strings.TrimSpace(record[1]),
		Type:        strings.TrimSpace(record[2]),
		MetadataRaw: strings.TrimSpace(record[3]),
	}

	// Parse metadata JSON if present
	if doc.MetadataRaw != "" {
		var metadata map[string]interface{}
		if err := json.Unmarshal([]byte(doc.MetadataRaw), &metadata); err != nil {
			// If metadata is not valid JSON, store as string
			doc.Metadata = map[string]interface{}{"raw": doc.MetadataRaw}
		} else {
			doc.Metadata = metadata
		}
	}

	// Validate required fields
	if doc.URL == "" {
		return models.Document{}, fmt.Errorf("URL is required")
	}
	if doc.Name == "" {
		return models.Document{}, fmt.Errorf("name is required")
	}
	if doc.Type == "" {
		return models.Document{}, fmt.Errorf("type is required")
	}

	return doc, nil
}