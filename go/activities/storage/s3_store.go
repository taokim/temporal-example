package storage

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/credentials"
	"github.com/aws/aws-sdk-go/aws/session"
	"github.com/aws/aws-sdk-go/service/s3"
	"go.temporal.io/sdk/activity"

	"github.com/example/temporal-rag/internal/models"
)

type S3Store struct {
	s3Client *s3.S3
	bucket   string
}

func NewS3Store(endpoint, accessKey, secretKey, bucket string) (*S3Store, error) {
	// Create AWS session
	sess, err := session.NewSession(&aws.Config{
		Endpoint:         aws.String(endpoint),
		Region:           aws.String("us-east-1"),
		Credentials:      credentials.NewStaticCredentials(accessKey, secretKey, ""),
		S3ForcePathStyle: aws.Bool(true), // Required for MinIO
	})
	if err != nil {
		return nil, fmt.Errorf("failed to create S3 session: %w", err)
	}

	return &S3Store{
		s3Client: s3.New(sess),
		bucket:   bucket,
	}, nil
}

// StoreInS3 stores processed documents in S3/MinIO
func (s3s *S3Store) StoreInS3(ctx context.Context, input models.S3StorageInput) (*models.S3StorageResult, error) {
	logger := activity.GetLogger(ctx)
	logger.Info("Storing documents in S3",
		"bucket", input.Bucket,
		"documentCount", len(input.Documents))

	result := &models.S3StorageResult{
		ObjectKeys: make([]string, 0),
		Errors:     make([]models.StorageError, 0),
	}

	// Store each document
	for i, doc := range input.Documents {
		// Report heartbeat
		if i%5 == 0 {
			activity.RecordHeartbeat(ctx, fmt.Sprintf("Storing document %d/%d in S3", i+1, len(input.Documents)))
		}

		objectKey, err := s3s.storeDocument(ctx, input.Bucket, input.KeyPrefix, doc)
		if err != nil {
			logger.Error("Failed to store document in S3",
				"documentId", doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.ID,
				"error", err)

			result.Errors = append(result.Errors, models.StorageError{
				DocumentID: doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.ID,
				Error:      err.Error(),
				Timestamp:  time.Now(),
			})
			continue
		}

		result.ObjectKeys = append(result.ObjectKeys, objectKey)
	}

	logger.Info("S3 storage completed",
		"totalStored", len(result.ObjectKeys),
		"errors", len(result.Errors))

	return result, nil
}

func (s3s *S3Store) storeDocument(ctx context.Context, bucket, keyPrefix string, doc models.PostprocessedDocument) (string, error) {
	// Prepare document data for storage
	storageDoc := s3s.prepareStorageDocument(doc)

	// Serialize to JSON
	jsonData, err := json.MarshalIndent(storageDoc, "", "  ")
	if err != nil {
		return "", fmt.Errorf("failed to serialize document: %w", err)
	}

	// Generate object key
	docID := doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.ID
	timestamp := time.Now().Format("2006-01-02")
	objectKey := fmt.Sprintf("%s/%s/%s.json", keyPrefix, timestamp, docID)

	// Upload to S3
	_, err = s3s.s3Client.PutObjectWithContext(ctx, &s3.PutObjectInput{
		Bucket: aws.String(bucket),
		Key:    aws.String(objectKey),
		Body:   bytes.NewReader(jsonData),
		ContentType: aws.String("application/json"),
		Metadata: map[string]*string{
			"document-id":   aws.String(docID),
			"document-name": aws.String(doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.Name),
			"processed-at":  aws.String(time.Now().Format(time.RFC3339)),
			"quality-score": aws.String(fmt.Sprintf("%.2f", doc.QualityScore)),
		},
	})

	if err != nil {
		return "", fmt.Errorf("failed to upload to S3: %w", err)
	}

	return objectKey, nil
}

func (s3s *S3Store) prepareStorageDocument(doc models.PostprocessedDocument) map[string]interface{} {
	storageDoc := make(map[string]interface{})

	// Document metadata
	storageDoc["document"] = map[string]interface{}{
		"id":          doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.ID,
		"name":        doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.Name,
		"type":        doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.Type,
		"url":         doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.URL,
		"metadata":    doc.InferredDocument.PreprocessedDocument.IngestedDocument.Document.Metadata,
		"language":    doc.InferredDocument.PreprocessedDocument.Language,
		"size":        doc.InferredDocument.PreprocessedDocument.IngestedDocument.Size,
		"content_type": doc.InferredDocument.PreprocessedDocument.IngestedDocument.ContentType,
	}

	// Processing results
	storageDoc["processing"] = map[string]interface{}{
		"summary":       doc.InferredDocument.InferenceResults.Summary,
		"entities":      doc.InferredDocument.InferenceResults.Entities,
		"chunk_count":   len(doc.InferredDocument.PreprocessedDocument.Chunks),
		"quality_score": doc.QualityScore,
		"search_index":  doc.SearchIndex,
		"metadata":      doc.Metadata,
	}

	// Chunks (without embeddings to save space)
	chunks := make([]map[string]interface{}, 0)
	for _, chunk := range doc.InferredDocument.PreprocessedDocument.Chunks {
		chunks = append(chunks, map[string]interface{}{
			"id":       chunk.ID,
			"text":     chunk.Text,
			"position": chunk.Position,
			"length":   chunk.Length,
		})
	}
	storageDoc["chunks"] = chunks

	// Timestamps
	storageDoc["timestamps"] = map[string]string{
		"downloaded":   doc.InferredDocument.PreprocessedDocument.IngestedDocument.DownloadedAt.Format(time.RFC3339),
		"preprocessed": doc.InferredDocument.PreprocessedDocument.ProcessedAt.Format(time.RFC3339),
		"inference":    doc.InferredDocument.InferenceResults.ProcessedAt.Format(time.RFC3339),
		"stored":       time.Now().Format(time.RFC3339),
	}

	return storageDoc
}