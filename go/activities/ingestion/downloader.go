package ingestion

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"go.temporal.io/sdk/activity"

	"github.com/example/temporal-rag/internal/models"
)

type Downloader struct {
	httpClient     *http.Client
	maxFileSize    int64
	tempDir        string
	allowedTypes   map[string]bool
}

func NewDownloader() *Downloader {
	return &Downloader{
		httpClient: &http.Client{
			Timeout: 5 * time.Minute,
		},
		maxFileSize: 100 * 1024 * 1024, // 100MB
		tempDir:     "/tmp/document-downloads",
		allowedTypes: map[string]bool{
			"pdf":  true,
			"docx": true,
			"doc":  true,
			"txt":  true,
			"pptx": true,
			"xlsx": true,
		},
	}
}

// DownloadAndValidateBatch downloads and validates a batch of documents
func (d *Downloader) DownloadAndValidateBatch(ctx context.Context, input models.IngestionBatchInput) (*models.IngestionBatchResult, error) {
	logger := activity.GetLogger(ctx)
	logger.Info("Starting document download batch", "batchId", input.BatchID, "documentCount", len(input.Documents))

	// Create temp directory if it doesn't exist
	if err := os.MkdirAll(d.tempDir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create temp directory: %w", err)
	}

	result := &models.IngestionBatchResult{
		BatchID: input.BatchID,
		Success: make([]models.IngestedDocument, 0, len(input.Documents)),
		Errors:  make([]models.ProcessingError, 0),
	}

	// Process each document
	for i, doc := range input.Documents {
		// Report heartbeat every 5 documents
		if i%5 == 0 {
			activity.RecordHeartbeat(ctx, fmt.Sprintf("Downloading document %d/%d", i+1, len(input.Documents)))
		}

		// Check if document type is allowed
		if !d.allowedTypes[doc.Type] {
			result.Errors = append(result.Errors, models.ProcessingError{
				DocumentID: doc.ID,
				Stage:      "ingestion",
				Error:      fmt.Sprintf("unsupported document type: %s", doc.Type),
				Timestamp:  time.Now(),
			})
			continue
		}

		ingestedDoc, err := d.downloadDocument(ctx, doc)
		if err != nil {
			logger.Error("Failed to download document", 
				"documentId", doc.ID, 
				"url", doc.URL,
				"error", err)
			
			result.Errors = append(result.Errors, models.ProcessingError{
				DocumentID: doc.ID,
				Stage:      "ingestion",
				Error:      err.Error(),
				Timestamp:  time.Now(),
			})
			continue
		}

		result.Success = append(result.Success, *ingestedDoc)
	}

	logger.Info("Download batch completed", 
		"batchId", input.BatchID,
		"success", len(result.Success),
		"errors", len(result.Errors))

	return result, nil
}

func (d *Downloader) downloadDocument(ctx context.Context, doc models.Document) (*models.IngestedDocument, error) {
	var content []byte
	var contentType string
	var err error

	// Handle file:// URLs for local files
	if strings.HasPrefix(doc.URL, "file://") {
		content, contentType, err = d.downloadLocalFile(doc.URL)
	} else {
		content, contentType, err = d.downloadHTTPFile(ctx, doc.URL)
	}

	if err != nil {
		return nil, err
	}

	// Validate file size
	if int64(len(content)) > d.maxFileSize {
		return nil, fmt.Errorf("file size %d exceeds maximum allowed size %d", len(content), d.maxFileSize)
	}

	// Validate content type matches expected type
	if !d.validateContentType(doc.Type, contentType) {
		return nil, fmt.Errorf("content type mismatch: expected %s, got %s", doc.Type, contentType)
	}

	return &models.IngestedDocument{
		Document:     doc,
		Content:      content,
		ContentType:  contentType,
		Size:         int64(len(content)),
		DownloadedAt: time.Now(),
	}, nil
}

func (d *Downloader) downloadHTTPFile(ctx context.Context, url string) ([]byte, string, error) {
	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return nil, "", fmt.Errorf("failed to create request: %w", err)
	}

	resp, err := d.httpClient.Do(req)
	if err != nil {
		return nil, "", fmt.Errorf("failed to download: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, "", fmt.Errorf("download failed with status %d", resp.StatusCode)
	}

	// Read content with size limit
	limitedReader := io.LimitReader(resp.Body, d.maxFileSize+1)
	content, err := io.ReadAll(limitedReader)
	if err != nil {
		return nil, "", fmt.Errorf("failed to read content: %w", err)
	}

	contentType := resp.Header.Get("Content-Type")
	if contentType == "" {
		// Try to detect content type from content
		contentType = http.DetectContentType(content)
	}

	return content, contentType, nil
}

func (d *Downloader) downloadLocalFile(url string) ([]byte, string, error) {
	// Convert file:// URL to local path
	path := strings.TrimPrefix(url, "file://")
	
	// Check if file exists
	if _, err := os.Stat(path); os.IsNotExist(err) {
		return nil, "", fmt.Errorf("file not found: %s", path)
	}

	// Read file
	content, err := os.ReadFile(path)
	if err != nil {
		return nil, "", fmt.Errorf("failed to read file: %w", err)
	}

	// Detect content type
	contentType := getContentTypeFromExtension(filepath.Ext(path))
	if contentType == "" {
		contentType = http.DetectContentType(content)
	}

	return content, contentType, nil
}

func (d *Downloader) validateContentType(expectedType, actualType string) bool {
	// Map of expected types to valid content types
	validTypes := map[string][]string{
		"pdf": {"application/pdf"},
		"doc": {"application/msword"},
		"docx": {"application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
		"txt": {"text/plain"},
		"pptx": {"application/vnd.openxmlformats-officedocument.presentationml.presentation"},
		"xlsx": {"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
	}

	valid, exists := validTypes[expectedType]
	if !exists {
		return false
	}

	for _, vt := range valid {
		if strings.Contains(actualType, vt) {
			return true
		}
	}

	// Allow octet-stream for any type (generic binary)
	return strings.Contains(actualType, "application/octet-stream")
}

func getContentTypeFromExtension(ext string) string {
	ext = strings.ToLower(ext)
	contentTypes := map[string]string{
		".pdf":  "application/pdf",
		".doc":  "application/msword",
		".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
		".txt":  "text/plain",
		".pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
		".xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
	}
	return contentTypes[ext]
}

// SaveToTempFile saves content to a temporary file for processing
func (d *Downloader) SaveToTempFile(content []byte, filename string) (string, error) {
	tempFile := filepath.Join(d.tempDir, fmt.Sprintf("%d-%s", time.Now().Unix(), filename))
	if err := os.WriteFile(tempFile, content, 0644); err != nil {
		return "", fmt.Errorf("failed to save temp file: %w", err)
	}
	return tempFile, nil
}

// CleanupTempFile removes a temporary file
func (d *Downloader) CleanupTempFile(path string) error {
	return os.Remove(path)
}