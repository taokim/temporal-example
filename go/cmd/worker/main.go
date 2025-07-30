package main

import (
	"log"
	"os"

	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/worker"

	"github.com/example/temporal-rag/activities/ingestion"
	"github.com/example/temporal-rag/activities/preprocessing"
	"github.com/example/temporal-rag/activities/inference"
	"github.com/example/temporal-rag/activities/postprocessing"
	"github.com/example/temporal-rag/activities/storage"
	"github.com/example/temporal-rag/workflows"
)

const (
	taskQueue = "document-pipeline-queue"
)

func main() {
	// Get Temporal server URL from environment
	temporalHost := os.Getenv("TEMPORAL_HOST_URL")
	if temporalHost == "" {
		temporalHost = "localhost:7233"
	}

	// Create Temporal client
	c, err := client.Dial(client.Options{
		HostPort: temporalHost,
	})
	if err != nil {
		log.Fatal("Unable to create Temporal client:", err)
	}
	defer c.Close()

	// Create worker
	w := worker.New(c, taskQueue, worker.Options{
		MaxConcurrentActivityExecutionSize:     10,
		MaxConcurrentWorkflowTaskExecutionSize: 10,
	})

	// Initialize activity implementations
	log.Println("Initializing activity implementations...")
	
	// Ingestion activities
	downloader := ingestion.NewDownloader()
	
	// Preprocessing activities
	textProcessor := preprocessing.NewTextProcessor()
	
	// Inference activities
	llmServiceURL := os.Getenv("LLM_SERVICE_URL")
	if llmServiceURL == "" {
		llmServiceURL = "http://localhost:8081"
	}
	useMockServices := os.Getenv("USE_MOCK_SERVICES") == "true"
	embeddingGenerator := inference.NewEmbeddingGenerator(llmServiceURL, useMockServices)
	
	// Postprocessing activities
	qualityEnhancer := postprocessing.NewQualityEnhancer()
	
	// Storage activities
	// Vector store (ChromaDB)
	vectorDBURL := os.Getenv("VECTOR_DB_URL")
	if vectorDBURL == "" {
		vectorDBURL = "http://localhost:8000"
	}
	vectorStore := storage.NewVectorStore(vectorDBURL)
	
	// S3 store
	s3Endpoint := os.Getenv("S3_ENDPOINT")
	s3AccessKey := os.Getenv("S3_ACCESS_KEY")
	s3SecretKey := os.Getenv("S3_SECRET_KEY")
	s3Bucket := os.Getenv("S3_BUCKET")
	
	if s3Endpoint == "" {
		s3Endpoint = "http://localhost:9000"
	}
	if s3AccessKey == "" {
		s3AccessKey = "minioadmin"
	}
	if s3SecretKey == "" {
		s3SecretKey = "minioadmin"
	}
	if s3Bucket == "" {
		s3Bucket = "documents"
	}
	
	s3Store, err := storage.NewS3Store(s3Endpoint, s3AccessKey, s3SecretKey, s3Bucket)
	if err != nil {
		log.Fatal("Failed to create S3 store:", err)
	}
	
	// Metadata store (PostgreSQL)
	dbHost := os.Getenv("METADATA_DB_HOST")
	dbPort := os.Getenv("METADATA_DB_PORT")
	dbName := os.Getenv("METADATA_DB_NAME")
	dbUser := os.Getenv("METADATA_DB_USER")
	dbPassword := os.Getenv("METADATA_DB_PASSWORD")
	
	if dbHost == "" {
		dbHost = "localhost"
	}
	if dbPort == "" {
		dbPort = "5433"
	}
	if dbName == "" {
		dbName = "document_metadata"
	}
	if dbUser == "" {
		dbUser = "docuser"
	}
	if dbPassword == "" {
		dbPassword = "docpass"
	}
	
	metadataStore, err := storage.NewMetadataStore(dbHost, dbPort, dbName, dbUser, dbPassword)
	if err != nil {
		log.Fatal("Failed to create metadata store:", err)
	}
	defer metadataStore.Close()

	// Register workflows
	w.RegisterWorkflow(workflows.DocumentPipelineWorkflow)

	// Register activities
	// Ingestion
	w.RegisterActivity(ingestion.ParseCSVActivity)
	w.RegisterActivity(downloader.DownloadAndValidateBatch)
	
	// Preprocessing
	w.RegisterActivity(textProcessor.PreprocessBatch)
	
	// Inference
	w.RegisterActivity(embeddingGenerator.RunInferenceBatch)
	
	// Postprocessing
	w.RegisterActivity(qualityEnhancer.PostprocessDocuments)
	
	// Storage
	w.RegisterActivity(vectorStore.StoreInVectorDB)
	w.RegisterActivity(s3Store.StoreInS3)
	w.RegisterActivity(metadataStore.StoreMetadata)

	log.Println("Worker starting...")
	log.Printf("Connected to Temporal at: %s", temporalHost)
	log.Printf("Task Queue: %s", taskQueue)
	log.Printf("Mock Services: %v", useMockServices)

	// Start worker
	err = w.Run(worker.InterruptCh())
	if err != nil {
		log.Fatal("Unable to start worker:", err)
	}
}