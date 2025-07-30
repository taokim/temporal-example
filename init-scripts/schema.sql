-- Create database schema for document pipeline metadata

-- Pipeline runs table
CREATE TABLE IF NOT EXISTS pipeline_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id VARCHAR(255) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    total_documents INTEGER NOT NULL DEFAULT 0,
    successful_documents INTEGER NOT NULL DEFAULT 0,
    failed_documents INTEGER NOT NULL DEFAULT 0,
    configuration JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pipeline_id ON pipeline_runs(pipeline_id);
CREATE INDEX IF NOT EXISTS idx_started_at ON pipeline_runs(started_at);

-- Document metadata table
CREATE TABLE IF NOT EXISTS document_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_run_id UUID NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
    document_id VARCHAR(255) NOT NULL,
    document_name VARCHAR(500) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    document_url TEXT,
    language VARCHAR(10),
    quality_score DECIMAL(3,2),
    summary TEXT,
    entity_count INTEGER DEFAULT 0,
    chunk_count INTEGER DEFAULT 0,
    s3_location TEXT,
    vector_ids JSONB,
    metadata JSONB,
    processed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dm_pipeline_run_id ON document_metadata(pipeline_run_id);
CREATE INDEX IF NOT EXISTS idx_dm_document_id ON document_metadata(document_id);
CREATE INDEX IF NOT EXISTS idx_dm_quality_score ON document_metadata(quality_score);
CREATE INDEX IF NOT EXISTS idx_dm_processed_at ON document_metadata(processed_at);

-- Processing errors table
CREATE TABLE IF NOT EXISTS processing_errors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_run_id UUID NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
    document_id VARCHAR(255),
    stage VARCHAR(50) NOT NULL,
    error_message TEXT NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pe_pipeline_run_id ON processing_errors(pipeline_run_id);
CREATE INDEX IF NOT EXISTS idx_pe_document_id ON processing_errors(document_id);
CREATE INDEX IF NOT EXISTS idx_pe_stage ON processing_errors(stage);

-- Vector search metadata (for tracking what's in ChromaDB)
CREATE TABLE IF NOT EXISTS vector_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_name VARCHAR(255) NOT NULL,
    document_id VARCHAR(255) NOT NULL,
    chunk_id VARCHAR(255) NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    dimension INTEGER NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_chunk UNIQUE (collection_name, chunk_id)
);

CREATE INDEX IF NOT EXISTS idx_vm_collection_document ON vector_metadata(collection_name, document_id);
CREATE INDEX IF NOT EXISTS idx_vm_created_at ON vector_metadata(created_at);

-- Summary statistics view
CREATE OR REPLACE VIEW pipeline_summary AS
SELECT 
    pr.id,
    pr.pipeline_id,
    pr.started_at,
    pr.completed_at,
    pr.total_documents,
    pr.successful_documents,
    pr.failed_documents,
    EXTRACT(EPOCH FROM (pr.completed_at - pr.started_at)) as duration_seconds,
    COUNT(DISTINCT dm.id) as actual_processed,
    AVG(dm.quality_score) as avg_quality_score,
    SUM(dm.chunk_count) as total_chunks,
    COUNT(DISTINCT pe.id) as error_count
FROM pipeline_runs pr
LEFT JOIN document_metadata dm ON pr.id = dm.pipeline_run_id
LEFT JOIN processing_errors pe ON pr.id = pe.pipeline_run_id
GROUP BY pr.id;

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_document_metadata_composite 
    ON document_metadata(pipeline_run_id, quality_score DESC, processed_at DESC);

CREATE INDEX IF NOT EXISTS idx_processing_errors_composite 
    ON processing_errors(pipeline_run_id, stage, occurred_at DESC);

-- Grant permissions (adjust user as needed)
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO docuser;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO docuser;