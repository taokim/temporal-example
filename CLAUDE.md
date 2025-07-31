# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a production-ready Temporal-based document processing pipeline that implements a complete 5-stage AI pipeline for building RAG (Retrieval-Augmented Generation) context. The system processes documents from CSV sources through ingestion, preprocessing, inference, postprocessing, and storage stages.

**Why Temporal for Vector Embeddings**: Temporal provides the ideal foundation for embedding generation with automatic failure recovery, long-running support for massive datasets, built-in handling of API rate limits, complete observability of the generation process, and human-in-the-loop capabilities for quality review.

## Architecture Overview

### Core Workflow: DocumentPipelineWorkflow
- **Input**: PipelineInput with CSV path, S3 bucket, vector DB collection, model configs
- **Output**: PipelineResult with references to stored data (NOT the actual embeddings/content)
- **5-Stage Process**: 
  1. **Data Ingestion**: Parse CSV, download documents with validation
  2. **Pre-processing**: Extract text, chunk documents, detect language, optional PII removal
  3. **Model Inference**: Generate embeddings and summaries using LLM APIs
  4. **Post-processing**: Quality scoring, metadata enrichment, search indexing
  5. **Storage**: Store in ChromaDB (vectors), MinIO/S3 (documents), PostgreSQL (metadata)

### Current Implementation Structure

#### Go Implementation (Implemented)
```
/go/
  ├── cmd/
  │   ├── worker/main.go       - Worker process with all activities registered
  │   └── starter/main.go      - CLI to start workflows with configuration
  ├── internal/
  │   ├── workflows/
  │   │   └── document_pipeline.go         - Main 5-stage pipeline workflow
  │   ├── activities/
  │   │   ├── ingestion/
  │   │   │   ├── csv_parser.go           - Parse CSV with validation
  │   │   │   └── downloader.go           - Download with retry and validation
  │   │   ├── preprocessing/
  │   │   │   └── text_processor.go       - Text extraction, chunking, PII removal
  │   │   ├── inference/
  │   │   │   └── embeddings.go           - Generate embeddings and summaries
  │   │   ├── postprocessing/
  │   │   │   └── quality_enhancer.go     - Quality scoring and enrichment
  │   │   └── storage/
  │   │       ├── vector_store.go         - ChromaDB integration
  │   │       ├── s3_store.go             - MinIO/S3 storage
  │   │       └── metadata_store.go       - PostgreSQL metadata
  │   ├── models/                         - Comprehensive data models
  │   └── utils/
  │       └── document/parser.go          - Document type parsers
  └── Makefile                             - Complete build/test/run commands

#### Java Implementation (Planned)
```
/java/
  ├── src/main/java/com/example/
  │   ├── workflows/
  │   │   └── DocumentPipelineWorkflow.java - With signals and queries
  │   ├── activities/                        - 5-stage activity implementations
  │   ├── models/                           - POJOs with Lombok
  │   ├── worker/                           - Worker configuration
  │   └── starter/                          - Workflow starter CLI
  └── build.gradle                          - Dependencies and custom tasks
```

## Development Setup

### Prerequisites
- Go 1.21+ or Java 17+
- Docker and Docker Compose
- Make (optional, for Go commands)
- PostgreSQL client (for schema setup)

### Language Selection Guide

**Quick Decision Guide:**
- **Choose Go**: Maximum performance, cloud-native, minimal resources, cost optimization
- **Choose Java**: Enterprise environment, complex business logic, existing JVM infrastructure
- **Choose Python**: AI/ML heavy workflows, rapid prototyping, LangChain integration
- **Choose Hybrid**: Combine Go/Java performance with Python AI capabilities

**For this document processing use case**, we recommend:
- **Small Scale** (<10K docs/day): Python for faster development
- **Medium Scale** (10K-100K docs/day): Go + Python hybrid for cost-effective scaling
- **Large Scale** (100K-1M docs/day): Pure Go or Go + Python hybrid
- **Enterprise Scale** (1M+ docs/day): Go workflows + Python AI activities

### Environment Variables
```bash
# Temporal
TEMPORAL_HOST_URL=localhost:7233
TEMPORAL_NAMESPACE=default
TEMPORAL_TASK_QUEUE=document-processing

# Storage
S3_BUCKET=documents
S3_ENDPOINT=http://localhost:9000
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin

# Vector Database (ChromaDB)
VECTOR_DB_URL=http://localhost:8000

# Metadata Database (PostgreSQL)
METADATA_DB_HOST=localhost
METADATA_DB_PORT=5433
METADATA_DB_NAME=document_metadata
METADATA_DB_USER=docuser
METADATA_DB_PASSWORD=docpass

# LLM Service
LLM_SERVICE_URL=http://localhost:8081
USE_MOCK_SERVICES=true  # Uses local mock for testing

# Processing
MAX_CONCURRENT_DOWNLOADS=10
MAX_DOCUMENT_SIZE_MB=100
PROCESSING_TIMEOUT_MINUTES=30
```

### Starting Services
```bash
# Start all services
docker-compose up -d

# Setup MinIO bucket and PostgreSQL schema
cd go
make setup          # Complete setup
# or separately:
make setup-minio    # Create S3 bucket
make setup-db       # Initialize database

# Run complete demo (Go)
cd go
make demo           # Runs worker and starter automatically

# Or run separately:
# Terminal 1 - Start worker
cd go
make run-local-worker

# Terminal 2 - Start workflow
cd go
make run-local-starter

# Java implementation
cd java
./gradlew runWorker     # Terminal 1
./gradlew runStarter    # Terminal 2
```

### Worker Scaling and Management

#### Understanding Temporal's Architecture
1. **Workflow Definitions**: Live in your application code, not in Temporal
2. **Workers**: Your application instances that poll Temporal for work
3. **Task Queues**: Named queues that route work to specific worker pools
4. **Pull Model**: Workers actively poll for tasks (no push overhead)

#### Basic Monitoring
```bash
# Key metrics to watch
temporal workflow list --query 'WorkflowType="DocumentPipelineWorkflow"'
temporal task-queue describe --task-queue document-pipeline-queue

# View in Temporal UI
open http://localhost:8080
```

## Common Commands

### Go Commands (via Makefile)
```bash
cd go

# Development
make build              # Build worker and starter
make test               # Run unit tests
make test-integration   # Run integration tests
make fmt                # Format code
make lint               # Lint code

# Running
make demo               # Complete demo with worker + starter
make run-local-worker   # Run worker with local config
make run-local-starter  # Start workflow

# Infrastructure
make docker-up          # Start Docker services
make docker-down        # Stop Docker services
make setup              # Complete local setup
make clean              # Clean build artifacts
```

### Java Commands (via Gradle)
```bash
cd java

# Build and test
./gradlew build         # Build everything
./gradlew test          # Run unit tests
./gradlew integrationTest # Run integration tests

# Running
./gradlew runWorker     # Run worker process
./gradlew runStarter    # Start workflow

# Docker
./gradlew buildDocker   # Build Docker image

# Shadow JAR
./gradlew shadowJar     # Create fat JAR
```

### Temporal CLI Commands
```bash
# Monitor workflows
temporal workflow list
temporal workflow describe -w <workflow-id>

# Task queue metrics
temporal task-queue describe --task-queue document-processing
```

## Key Workflows and Activities

### DocumentPipelineWorkflow
- **Input**: `PipelineInput{CSVPath, S3Bucket, VectorDBCollection, EmbeddingModel, SummaryModel, RemovePII, AcceptedLanguages}`
- **Output**: `PipelineResult{PipelineID, TotalProcessed, TotalErrors, VectorDBIndices, S3Locations, MetadataID, Stages, ExecutionTime}`
- **Signals** (Java): `pausePipeline()`, `resumePipeline()`, `updateConfig()`
- **Queries** (Java): `getCurrentStatus()`, `getProcessingStats()`, `getFailedDocuments()`
- **Batch Processing**: Processes documents in batches of 10, max 3 parallel batches

**Important**: Workflow returns only references (vector IDs, S3 paths, metadata ID), not the actual embeddings or content. This prevents Temporal history bloat.

### Implemented Activities (Go)

**Stage 1 - Data Ingestion**:
1. **ParseCSVActivity**: Validates headers, parses metadata JSON
2. **DownloadAndValidateBatch**: Downloads with retry, validates content type and size

**Stage 2 - Pre-processing**:
3. **PreprocessBatch**: Text extraction, chunking (1000 chars), language detection, optional PII removal

**Stage 3 - Model Inference**:
4. **RunInferenceBatch**: Generates embeddings (1536-dim), summaries, extracts entities

**Stage 4 - Post-processing**:
5. **PostprocessDocuments**: Quality scoring (0-1), metadata enrichment, search indexing

**Stage 5 - Storage**:
6. **StoreInVectorDB**: ChromaDB storage with chunk metadata
7. **StoreInS3**: Document JSON storage with timestamp-based paths
8. **StoreMetadata**: PostgreSQL pipeline run and document metadata

## Testing Strategy

### Go Testing
```go
// Test workflow with mocked activities
func TestDocumentProcessingWorkflow(t *testing.T) {
    testSuite := &testsuite.WorkflowTestSuite{}
    env := testSuite.NewTestWorkflowEnvironment()
    
    // Mock activities
    env.OnActivity(ParseCSVActivity).Return(documents, nil)
    env.OnActivity(DownloadDocumentActivity).Return(mockDoc, nil)
    
    // Execute workflow
    env.ExecuteWorkflow(DocumentProcessingWorkflow, input)
    
    require.True(t, env.IsWorkflowCompleted())
    require.NoError(t, env.GetWorkflowError())
    
    var result ProcessingResult
    require.NoError(t, env.GetWorkflowResult(&result))
    assert.Equal(t, 100, result.ProcessedCount)
}

// Test activity with real dependencies
func TestProcessDocumentActivity(t *testing.T) {
    // Setup test environment
    ctx := context.Background()
    env := testsuite.NewTestActivityEnvironment()
    env.RegisterActivity(ProcessDocumentActivity)
    
    // Execute activity
    val, err := env.ExecuteActivity(ProcessDocumentActivity, testDoc)
    require.NoError(t, err)
    
    var result ProcessedDoc
    require.NoError(t, val.Get(&result))
    assert.NotEmpty(t, result.Embedding)
}
```

### Java Testing
```java
// Test workflow with mocked activities
@Test
public void testDocumentProcessingWorkflow() {
    TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance();
    Worker worker = testEnv.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(DocumentProcessingWorkflowImpl.class);
    
    // Mock activities
    DocumentActivities activities = mock(DocumentActivities.class);
    when(activities.parseCSV(anyString())).thenReturn(documents);
    when(activities.processBatch(any())).thenReturn(processedDocs);
    worker.registerActivitiesImplementations(activities);
    
    // Start test environment
    testEnv.start();
    
    // Get workflow stub
    DocumentProcessingWorkflow workflow = testEnv.newWorkflowStub(
        DocumentProcessingWorkflow.class
    );
    
    // Execute workflow
    ProcessingResult result = workflow.processDocuments("test.csv");
    
    // Assertions
    assertEquals(100, result.getProcessedCount());
    assertEquals(0, result.getFailedCount());
    
    testEnv.close();
}

// Test activity with real dependencies
@Test
public void testProcessDocumentActivity() {
    TestActivityEnvironment testEnv = TestActivityEnvironment.newInstance();
    testEnv.registerActivitiesImplementations(new DocumentActivitiesImpl());
    
    DocumentActivities activities = testEnv.newActivityStub(DocumentActivities.class);
    
    ProcessedDoc result = activities.processDocument(testDoc);
    
    assertNotNull(result.getEmbedding());
    assertNotNull(result.getSummary());
    assertTrue(result.getEmbedding().length > 0);
}
```

## Error Handling and Retries

- **Download Failures**: Exponential backoff with max 5 retries
- **Processing Failures**: Document marked as failed, workflow continues
- **OCR Failures**: Fallback to basic text extraction
- **API Rate Limits**: Activity-level retry with backoff
- **Partial Failures**: Workflow returns both successes and failures

## Performance Considerations

- Use workflow.Go() for parallel document processing
- Implement batching for embedding generation
- Cache processed documents in S3
- Use continue-as-new for workflows processing >1000 documents
- Implement rate limiting for external API calls

## Monitoring and Observability

- Workflow metrics: documents_processed, processing_duration, failure_rate
- Activity metrics: download_time, ocr_accuracy, embedding_generation_time
- Custom search attributes: document_type, processing_status, error_type
- Structured logging with correlation IDs

## CSV Format

Expected CSV format with headers: `url,name,type,metadata`
```csv
url,name,type,metadata
https://example.com/doc.pdf,Technical Spec,pdf,"{""category"":""engineering"",""priority"":""high""}"
file:///tmp/report.docx,Annual Report,docx,"{""category"":""financial"",""year"":""2024""}"
```

See `testdata/documents.csv` for complete examples with various document types.

## Data Storage Patterns

### Workflow Result (What gets returned)
```go
type PipelineResult struct {
    PipelineID      string                    // Unique pipeline run ID
    TotalProcessed  int                       // Successfully processed documents
    TotalErrors     int                       // Failed documents
    VectorDBIndices []string                  // ChromaDB chunk IDs
    S3Locations     []string                  // S3 object keys
    MetadataID      string                    // PostgreSQL metadata record ID
    Stages          map[string]StageMetrics   // Metrics per stage
    Errors          []ProcessingError         // Detailed error information
    ExecutionTime   string                    // Total execution duration
}
```

### Vector Database Storage (Actual embeddings)
```json
{
  "id": "doc_chunk_001",
  "values": [0.1, 0.2, ...],  // 1536-dimension embedding
  "metadata": {
    "document_id": "uuid",
    "source_url": "https://...",
    "chunk_index": 0,
    "text_preview": "first 200 chars...",
    "page": 1,
    "processing_id": "proc_123"
  }
}
```

### Storage Structure

**MinIO/S3**:
```
/documents/
  └── /pipeline-runs/
      └── /{date}/            # e.g., 2024-01-30
          └── {document_id}.json  # Complete processed document
```

**ChromaDB Collections**:
- Collection per pipeline run
- Each chunk stored with embedding and metadata
- Searchable by document_id, quality_score, language

**PostgreSQL Tables**:
- `pipeline_runs`: Overall pipeline execution records
- `document_metadata`: Per-document processing details
- `processing_errors`: Detailed error tracking
- `vector_metadata`: ChromaDB reference tracking

### Accessing Processed Data

```go
// After workflow completion, retrieve data using references
result := workflowResult.(PipelineResult)

// Query PostgreSQL for detailed metadata
rows := db.Query(`
    SELECT * FROM document_metadata 
    WHERE pipeline_run_id = $1
`, result.MetadataID)

// Search ChromaDB for similar documents
results := chromaClient.Query(
    collection=result.VectorDBCollection,
    queryEmbeddings=queryVector,
    nResults=10,
)

// Retrieve full documents from S3
for _, s3Key := range result.S3Locations {
    doc := s3Client.GetObject(bucket, s3Key)
}
```

## Best Practices for Large Data in Temporal

1. **Never return large data in workflow results** - Use references instead
2. **Store embeddings in vector databases** - Purpose-built for similarity search
3. **Use S3/blob storage for documents** - Cost-effective for large files
4. **Implement pagination for queries** - Return chunks of results
5. **Use Temporal queries for status** - Not for retrieving processed data
6. **Set history size limits** - Prevent workflow history from growing too large

## Workflow Patterns for Data Access

### Pattern 1: Query-Based Retrieval
```go
// Workflow implements query handler
func (w *DocumentProcessingWorkflow) GetDocumentReferences(batchSize int, offset int) []string {
    // Return paginated references
}
```

### Pattern 2: Signal-Based Export
```go
// Signal to trigger export to specific location
type ExportSignal struct {
    ExportLocation string
    Format         string // "json", "parquet", "csv"
}
```

### Pattern 3: Child Workflow for Retrieval
```go
// Spawn child workflow to handle data retrieval
func DataRetrievalWorkflow(references []string) DataExport {
    // Fetch and package data for delivery
}
```

## Additional Documentation

For detailed guides on workflow orchestration, language comparisons, deployment strategies, and advanced patterns, see the [documentation directory](./docs/README.md).