package com.example.activities.storage;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.example.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class StorageActivitiesImpl implements StorageActivities {
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AmazonS3 s3Client;
    private final JdbcTemplate jdbcTemplate;
    private final String chromaDBURL;
    
    public StorageActivitiesImpl(
            @Value("${s3.endpoint:http://localhost:9000}") String s3Endpoint,
            @Value("${s3.access-key:minioadmin}") String s3AccessKey,
            @Value("${s3.secret-key:minioadmin}") String s3SecretKey,
            @Value("${s3.region:us-east-1}") String s3Region,
            @Value("${chromadb.url:http://localhost:8000}") String chromaDBURL,
            DataSource dataSource) {
        
        this.chromaDBURL = chromaDBURL;
        this.objectMapper = new ObjectMapper();
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.MINUTES)
                .build();
        
        // Configure S3 client for MinIO
        this.s3Client = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        s3Endpoint, s3Region))
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(s3AccessKey, s3SecretKey)))
                .withPathStyleAccessEnabled(true)
                .build();
    }
    
    @Override
    public VectorDBStorageResult storeInVectorDB(VectorDBStorageInput input) {
        log.info("Storing documents in vector database, collection: {}, document count: {}",
                input.getCollection(), input.getDocuments().size());
        
        VectorDBStorageResult result = new VectorDBStorageResult();
        result.setStoredIDs(new ArrayList<>());
        result.setErrors(new ArrayList<>());
        
        try {
            // Create or get collection
            ensureCollection(input.getCollection());
            
            // Store each document's embeddings
            for (int i = 0; i < input.getDocuments().size(); i++) {
                PostprocessedDocument doc = input.getDocuments().get(i);
                
                // Report heartbeat
                if (i % 5 == 0) {
                    Activity.getExecutionContext().heartbeat(
                        String.format("Storing document %d/%d", i + 1, input.getDocuments().size())
                    );
                }
                
                try {
                    List<String> vectorIDs = storeDocumentVectors(input.getCollection(), doc);
                    result.getStoredIDs().addAll(vectorIDs);
                } catch (Exception e) {
                    log.error("Failed to store document vectors: {}", e.getMessage());
                    result.getErrors().add(StorageError.builder()
                            .documentID(doc.getInferredDocument().getPreprocessedDocument()
                                      .getIngestedDocument().getDocument().getId())
                            .error(e.getMessage())
                            .timestamp(LocalDateTime.now())
                            .build());
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to store in vector DB: {}", e.getMessage());
            throw new RuntimeException("Vector DB storage failed", e);
        }
        
        log.info("Vector storage completed. Total stored: {}, Errors: {}",
                result.getStoredIDs().size(), result.getErrors().size());
        
        return result;
    }
    
    @Override
    public S3StorageResult storeInS3(S3StorageInput input) {
        log.info("Storing documents in S3, bucket: {}, document count: {}",
                input.getBucket(), input.getDocuments().size());
        
        S3StorageResult result = new S3StorageResult();
        result.setObjectKeys(new ArrayList<>());
        result.setErrors(new ArrayList<>());
        
        // Store each document
        for (int i = 0; i < input.getDocuments().size(); i++) {
            PostprocessedDocument doc = input.getDocuments().get(i);
            
            // Report heartbeat
            if (i % 5 == 0) {
                Activity.getExecutionContext().heartbeat(
                    String.format("Storing document %d/%d in S3", i + 1, input.getDocuments().size())
                );
            }
            
            try {
                String objectKey = storeDocument(input.getBucket(), input.getKeyPrefix(), doc);
                result.getObjectKeys().add(objectKey);
            } catch (Exception e) {
                log.error("Failed to store document in S3: {}", e.getMessage());
                result.getErrors().add(StorageError.builder()
                        .documentID(doc.getInferredDocument().getPreprocessedDocument()
                                  .getIngestedDocument().getDocument().getId())
                        .error(e.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build());
            }
        }
        
        log.info("S3 storage completed. Total stored: {}, Errors: {}",
                result.getObjectKeys().size(), result.getErrors().size());
        
        return result;
    }
    
    @Override
    @Transactional
    public MetadataStorageResult storeMetadata(MetadataStorageInput input) {
        log.info("Storing pipeline metadata, pipelineId: {}", input.getPipelineID());
        
        try {
            // Store pipeline metadata
            String metadataID = storePipelineMetadata(input);
            
            // Store document metadata
            int recordCount = 0;
            for (int i = 0; i < input.getDocuments().size(); i++) {
                PostprocessedDocument doc = input.getDocuments().get(i);
                
                // Report heartbeat
                if (i % 10 == 0) {
                    Activity.getExecutionContext().heartbeat(
                        String.format("Storing document metadata %d/%d", i + 1, input.getDocuments().size())
                    );
                }
                
                try {
                    storeDocumentMetadata(metadataID, doc);
                    recordCount++;
                } catch (Exception e) {
                    log.error("Failed to store document metadata: {}", e.getMessage());
                }
            }
            
            // Store processing errors if any
            if (!input.getProcessingErrors().isEmpty()) {
                storeProcessingErrors(metadataID, input.getProcessingErrors());
            }
            
            log.info("Metadata storage completed. MetadataID: {}, Records: {}",
                    metadataID, recordCount);
            
            return MetadataStorageResult.builder()
                    .metadataID(metadataID)
                    .recordCount(recordCount)
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to store metadata: {}", e.getMessage());
            throw new RuntimeException("Metadata storage failed", e);
        }
    }
    
    private void ensureCollection(String collection) throws IOException {
        Map<String, Object> createReq = Map.of(
                "name", collection,
                "metadata", Map.of(
                        "created_by", "temporal-rag-pipeline",
                        "created_at", LocalDateTime.now().toString()
                )
        );
        
        String jsonBody = objectMapper.writeValueAsString(createReq);
        
        Request request = new Request.Builder()
                .url(chromaDBURL + "/api/v1/collections")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() != 200 && response.code() != 201) {
                throw new IOException("Failed to create collection: status " + response.code());
            }
        }
    }
    
    private List<String> storeDocumentVectors(String collection, PostprocessedDocument doc) throws IOException {
        List<String> storedIDs = new ArrayList<>();
        
        if (doc.getInferredDocument().getInferenceResults().getEmbeddings() == null ||
            doc.getInferredDocument().getInferenceResults().getEmbeddings().isEmpty()) {
            return storedIDs;
        }
        
        // Prepare batch add request
        List<String> ids = new ArrayList<>();
        List<float[]> embeddings = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();
        List<String> documents = new ArrayList<>();
        
        for (ChunkEmbedding embedding : doc.getInferredDocument().getInferenceResults().getEmbeddings()) {
            // Find corresponding chunk
            String chunkText = "";
            for (TextChunk chunk : doc.getInferredDocument().getPreprocessedDocument().getChunks()) {
                if (chunk.getId().equals(embedding.getChunkID())) {
                    chunkText = chunk.getText();
                    break;
                }
            }
            
            if (chunkText.isEmpty()) {
                continue; // Skip if chunk not found
            }
            
            // Prepare metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("document_id", doc.getInferredDocument().getPreprocessedDocument()
                       .getIngestedDocument().getDocument().getId());
            metadata.put("document_name", doc.getInferredDocument().getPreprocessedDocument()
                       .getIngestedDocument().getDocument().getName());
            metadata.put("document_type", doc.getInferredDocument().getPreprocessedDocument()
                       .getIngestedDocument().getDocument().getType());
            metadata.put("chunk_id", embedding.getChunkID());
            metadata.put("quality_score", doc.getQualityScore());
            metadata.put("language", doc.getInferredDocument().getPreprocessedDocument().getLanguage());
            
            // Add custom metadata
            if (doc.getInferredDocument().getPreprocessedDocument().getIngestedDocument()
                   .getDocument().getMetadata() != null) {
                doc.getInferredDocument().getPreprocessedDocument().getIngestedDocument()
                   .getDocument().getMetadata().forEach((k, v) -> metadata.put("custom_" + k, v));
            }
            
            ids.add(embedding.getChunkID());
            embeddings.add(embedding.getEmbedding());
            metadatas.add(metadata);
            documents.add(chunkText);
        }
        
        if (ids.isEmpty()) {
            return storedIDs;
        }
        
        // Create add request
        Map<String, Object> addReq = Map.of(
                "ids", ids,
                "embeddings", embeddings,
                "metadatas", metadatas,
                "documents", documents
        );
        
        String jsonBody = objectMapper.writeValueAsString(addReq);
        
        Request request = new Request.Builder()
                .url(chromaDBURL + "/api/v1/collections/" + collection + "/add")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to add embeddings: status " + response.code());
            }
        }
        
        return ids;
    }
    
    private String storeDocument(String bucket, String keyPrefix, PostprocessedDocument doc) 
            throws IOException {
        // Prepare document data for storage
        Map<String, Object> storageDoc = prepareStorageDocument(doc);
        
        // Serialize to JSON
        String jsonData = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(storageDoc);
        
        // Generate object key
        String docID = doc.getInferredDocument().getPreprocessedDocument()
                         .getIngestedDocument().getDocument().getId();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String objectKey = String.format("%s/%s/%s.json", keyPrefix, timestamp, docID);
        
        // Upload to S3
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("application/json");
        metadata.setContentLength(jsonData.getBytes().length);
        metadata.addUserMetadata("document-id", docID);
        metadata.addUserMetadata("document-name", doc.getInferredDocument().getPreprocessedDocument()
                               .getIngestedDocument().getDocument().getName());
        metadata.addUserMetadata("processed-at", LocalDateTime.now().toString());
        metadata.addUserMetadata("quality-score", String.format("%.2f", doc.getQualityScore()));
        
        PutObjectRequest putRequest = new PutObjectRequest(
                bucket, objectKey, 
                new ByteArrayInputStream(jsonData.getBytes()), 
                metadata);
        
        s3Client.putObject(putRequest);
        
        return objectKey;
    }
    
    private Map<String, Object> prepareStorageDocument(PostprocessedDocument doc) {
        Map<String, Object> storageDoc = new HashMap<>();
        
        // Document metadata
        Map<String, Object> documentInfo = new HashMap<>();
        documentInfo.put("id", doc.getInferredDocument().getPreprocessedDocument()
                       .getIngestedDocument().getDocument().getId());
        documentInfo.put("name", doc.getInferredDocument().getPreprocessedDocument()
                       .getIngestedDocument().getDocument().getName());
        documentInfo.put("type", doc.getInferredDocument().getPreprocessedDocument()
                       .getIngestedDocument().getDocument().getType());
        documentInfo.put("url", doc.getInferredDocument().getPreprocessedDocument()
                       .getIngestedDocument().getDocument().getUrl());
        documentInfo.put("metadata", doc.getInferredDocument().getPreprocessedDocument()
                       .getIngestedDocument().getDocument().getMetadata());
        documentInfo.put("language", doc.getInferredDocument().getPreprocessedDocument().getLanguage());
        documentInfo.put("size", doc.getInferredDocument().getPreprocessedDocument()
                       .getIngestedDocument().getSize());
        documentInfo.put("content_type", doc.getInferredDocument().getPreprocessedDocument()
                       .getIngestedDocument().getContentType());
        storageDoc.put("document", documentInfo);
        
        // Processing results
        Map<String, Object> processingInfo = new HashMap<>();
        processingInfo.put("summary", doc.getInferredDocument().getInferenceResults().getSummary());
        processingInfo.put("entities", doc.getInferredDocument().getInferenceResults().getEntities());
        processingInfo.put("chunk_count", doc.getInferredDocument().getPreprocessedDocument()
                         .getChunks().size());
        processingInfo.put("quality_score", doc.getQualityScore());
        processingInfo.put("search_index", doc.getSearchIndex());
        processingInfo.put("metadata", doc.getMetadata());
        storageDoc.put("processing", processingInfo);
        
        // Chunks (without embeddings to save space)
        List<Map<String, Object>> chunks = new ArrayList<>();
        for (TextChunk chunk : doc.getInferredDocument().getPreprocessedDocument().getChunks()) {
            Map<String, Object> chunkInfo = new HashMap<>();
            chunkInfo.put("id", chunk.getId());
            chunkInfo.put("text", chunk.getText());
            chunkInfo.put("position", chunk.getPosition());
            chunkInfo.put("length", chunk.getLength());
            chunks.add(chunkInfo);
        }
        storageDoc.put("chunks", chunks);
        
        // Timestamps
        Map<String, String> timestamps = new HashMap<>();
        timestamps.put("downloaded", doc.getInferredDocument().getPreprocessedDocument()
                     .getIngestedDocument().getDownloadedAt().toString());
        timestamps.put("preprocessed", doc.getInferredDocument().getPreprocessedDocument()
                     .getProcessedAt().toString());
        timestamps.put("inference", doc.getInferredDocument().getInferenceResults()
                     .getProcessedAt().toString());
        timestamps.put("stored", LocalDateTime.now().toString());
        storageDoc.put("timestamps", timestamps);
        
        return storageDoc;
    }
    
    private String storePipelineMetadata(MetadataStorageInput input) {
        String sql = "INSERT INTO pipeline_runs (id, pipeline_id, started_at, completed_at, " +
                     "total_documents, successful_documents, failed_documents, configuration) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)";
        
        String metadataID = UUID.randomUUID().toString();
        String configJSON;
        try {
            configJSON = objectMapper.writeValueAsString(input.getConfig());
        } catch (Exception e) {
            configJSON = "{}";
        }
        
        jdbcTemplate.update(sql,
                metadataID,
                input.getPipelineID(),
                input.getStartTime(),
                input.getEndTime(),
                input.getDocuments().size(),
                input.getDocuments().size(), // Assuming all successful for now
                input.getProcessingErrors().size(),
                configJSON
        );
        
        return metadataID;
    }
    
    private void storeDocumentMetadata(String pipelineRunID, PostprocessedDocument doc) {
        String sql = "INSERT INTO document_metadata (id, pipeline_run_id, document_id, document_name, " +
                     "document_type, document_url, language, quality_score, summary, entity_count, " +
                     "chunk_count, s3_location, vector_ids, metadata, processed_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)";
        
        String docID = doc.getInferredDocument().getPreprocessedDocument()
                         .getIngestedDocument().getDocument().getId();
        
        // Prepare data
        List<String> vectorIDs = new ArrayList<>();
        if (doc.getInferredDocument().getInferenceResults().getEmbeddings() != null) {
            for (ChunkEmbedding embedding : doc.getInferredDocument().getInferenceResults().getEmbeddings()) {
                vectorIDs.add(embedding.getChunkID());
            }
        }
        
        String vectorIDsJSON;
        String metadataJSON;
        try {
            vectorIDsJSON = objectMapper.writeValueAsString(vectorIDs);
            metadataJSON = objectMapper.writeValueAsString(doc.getMetadata());
        } catch (Exception e) {
            vectorIDsJSON = "[]";
            metadataJSON = "{}";
        }
        
        jdbcTemplate.update(sql,
                UUID.randomUUID().toString(),
                pipelineRunID,
                docID,
                doc.getInferredDocument().getPreprocessedDocument().getIngestedDocument()
                   .getDocument().getName(),
                doc.getInferredDocument().getPreprocessedDocument().getIngestedDocument()
                   .getDocument().getType(),
                doc.getInferredDocument().getPreprocessedDocument().getIngestedDocument()
                   .getDocument().getUrl(),
                doc.getInferredDocument().getPreprocessedDocument().getLanguage(),
                doc.getQualityScore(),
                doc.getInferredDocument().getInferenceResults().getSummary(),
                doc.getInferredDocument().getInferenceResults().getEntities() != null ?
                        doc.getInferredDocument().getInferenceResults().getEntities().size() : 0,
                doc.getInferredDocument().getPreprocessedDocument().getChunks().size(),
                "", // S3 location will be updated later
                vectorIDsJSON,
                metadataJSON,
                LocalDateTime.now()
        );
    }
    
    private void storeProcessingErrors(String pipelineRunID, List<ProcessingError> errors) {
        String sql = "INSERT INTO processing_errors (id, pipeline_run_id, document_id, stage, " +
                     "error_message, occurred_at) VALUES (?, ?, ?, ?, ?, ?)";
        
        for (ProcessingError error : errors) {
            jdbcTemplate.update(sql,
                    UUID.randomUUID().toString(),
                    pipelineRunID,
                    error.getDocumentID(),
                    error.getStage(),
                    error.getError(),
                    error.getTimestamp()
            );
        }
    }
}