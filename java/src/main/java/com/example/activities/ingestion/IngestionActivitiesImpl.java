package com.example.activities.ingestion;

import com.example.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class IngestionActivitiesImpl implements IngestionActivities {
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedTypes;
    private final long maxFileSize;
    
    @Value("${ingestion.temp-dir:/tmp/document-downloads}")
    private String tempDir;
    
    public IngestionActivitiesImpl() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .build();
        this.objectMapper = new ObjectMapper();
        this.allowedTypes = Set.of("pdf", "docx", "doc", "txt", "pptx", "xlsx");
        this.maxFileSize = 100 * 1024 * 1024; // 100MB
    }
    
    @Override
    public List<Document> parseCSVActivity(String csvPath) {
        log.info("Parsing CSV file: {}", csvPath);
        List<Document> documents = new ArrayList<>();
        
        try (CSVReader reader = new CSVReader(new FileReader(csvPath))) {
            String[] headers = reader.readNext();
            if (!validateHeaders(headers)) {
                throw new IllegalArgumentException("Invalid CSV headers. Expected: url,name,type,metadata");
            }
            
            String[] line;
            int lineNum = 1;
            while ((line = reader.readNext()) != null) {
                lineNum++;
                
                // Report heartbeat every 100 documents
                if (lineNum % 100 == 0) {
                    Activity.getExecutionContext().heartbeat("Processed " + lineNum + " lines");
                }
                
                try {
                    Document doc = parseDocument(line, lineNum);
                    documents.add(doc);
                } catch (Exception e) {
                    log.warn("Failed to parse document at line {}: {}", lineNum, e.getMessage());
                }
            }
            
        } catch (IOException | CsvValidationException e) {
            throw new RuntimeException("Failed to read CSV file: " + e.getMessage(), e);
        }
        
        log.info("CSV parsing completed. Found {} documents", documents.size());
        return documents;
    }
    
    @Override
    public IngestionBatchResult downloadAndValidateBatch(IngestionBatchInput input) {
        log.info("Starting document download batch: {}, document count: {}", 
                input.getBatchID(), input.getDocuments().size());
        
        // Create temp directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(tempDir));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory", e);
        }
        
        IngestionBatchResult result = new IngestionBatchResult();
        result.setBatchID(input.getBatchID());
        result.setSuccess(new ArrayList<>());
        result.setErrors(new ArrayList<>());
        
        // Process each document
        for (int i = 0; i < input.getDocuments().size(); i++) {
            Document doc = input.getDocuments().get(i);
            
            // Report heartbeat every 5 documents
            if (i % 5 == 0) {
                Activity.getExecutionContext().heartbeat(
                    String.format("Downloading document %d/%d", i + 1, input.getDocuments().size())
                );
            }
            
            // Check if document type is allowed
            if (!allowedTypes.contains(doc.getType())) {
                result.getErrors().add(ProcessingError.builder()
                        .documentID(doc.getId())
                        .stage("ingestion")
                        .error("Unsupported document type: " + doc.getType())
                        .timestamp(LocalDateTime.now())
                        .build());
                continue;
            }
            
            try {
                IngestedDocument ingestedDoc = downloadDocument(doc);
                result.getSuccess().add(ingestedDoc);
            } catch (Exception e) {
                log.error("Failed to download document {}: {}", doc.getId(), e.getMessage());
                result.getErrors().add(ProcessingError.builder()
                        .documentID(doc.getId())
                        .stage("ingestion")
                        .error(e.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build());
            }
        }
        
        log.info("Download batch completed. Success: {}, Errors: {}", 
                result.getSuccess().size(), result.getErrors().size());
        
        return result;
    }
    
    private boolean validateHeaders(String[] headers) {
        if (headers == null || headers.length < 4) {
            return false;
        }
        return headers[0].trim().equalsIgnoreCase("url") &&
               headers[1].trim().equalsIgnoreCase("name") &&
               headers[2].trim().equalsIgnoreCase("type") &&
               headers[3].trim().equalsIgnoreCase("metadata");
    }
    
    private Document parseDocument(String[] record, int lineNum) {
        if (record.length < 4) {
            throw new IllegalArgumentException("Invalid record: expected 4 fields, got " + record.length);
        }
        
        Document doc = new Document();
        doc.setId("doc-" + lineNum + "-" + record[1].trim().replaceAll(" ", "-"));
        doc.setUrl(record[0].trim());
        doc.setName(record[1].trim());
        doc.setType(record[2].trim());
        doc.setMetadataRaw(record[3].trim());
        
        // Parse metadata JSON if present
        if (!doc.getMetadataRaw().isEmpty()) {
            try {
                Map<String, Object> metadataObj = objectMapper.readValue(
                    doc.getMetadataRaw(), 
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
                );
                // Convert to Map<String, String>
                Map<String, String> metadata = new HashMap<>();
                metadataObj.forEach((k, v) -> metadata.put(k, v != null ? v.toString() : ""));
                doc.setMetadata(metadata);
            } catch (Exception e) {
                // If metadata is not valid JSON, store as string
                Map<String, String> metadata = new HashMap<>();
                metadata.put("raw", doc.getMetadataRaw());
                doc.setMetadata(metadata);
            }
        }
        
        // Validate required fields
        if (doc.getUrl().isEmpty()) {
            throw new IllegalArgumentException("URL is required");
        }
        if (doc.getName().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (doc.getType().isEmpty()) {
            throw new IllegalArgumentException("Type is required");
        }
        
        return doc;
    }
    
    private IngestedDocument downloadDocument(Document doc) throws IOException {
        byte[] content;
        String contentType;
        
        if (doc.getUrl().startsWith("file://")) {
            // Handle local files
            String path = doc.getUrl().substring(7);
            content = Files.readAllBytes(Paths.get(path));
            contentType = getContentTypeFromExtension(doc.getType());
        } else {
            // Handle HTTP downloads
            Request request = new Request.Builder()
                    .url(doc.getUrl())
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Download failed with status " + response.code());
                }
                
                if (response.body() == null) {
                    throw new IOException("Empty response body");
                }
                
                content = response.body().bytes();
                contentType = response.header("Content-Type", getContentTypeFromExtension(doc.getType()));
            }
        }
        
        // Validate file size
        if (content.length > maxFileSize) {
            throw new IOException(String.format("File size %d exceeds maximum allowed size %d", 
                    content.length, maxFileSize));
        }
        
        // Validate content type
        if (!validateContentType(doc.getType(), contentType)) {
            throw new IOException(String.format("Content type mismatch: expected %s, got %s", 
                    doc.getType(), contentType));
        }
        
        return IngestedDocument.builder()
                .document(doc)
                .content(content)
                .contentType(contentType)
                .size((long) content.length)
                .downloadedAt(LocalDateTime.now())
                .build();
    }
    
    private String getContentTypeFromExtension(String ext) {
        return switch (ext.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "txt" -> "text/plain";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }
    
    private boolean validateContentType(String expectedType, String actualType) {
        Map<String, List<String>> validTypes = Map.of(
                "pdf", List.of("application/pdf"),
                "doc", List.of("application/msword"),
                "docx", List.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                "txt", List.of("text/plain"),
                "pptx", List.of("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
                "xlsx", List.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        );
        
        List<String> valid = validTypes.get(expectedType);
        if (valid == null) {
            return false;
        }
        
        for (String vt : valid) {
            if (actualType.contains(vt)) {
                return true;
            }
        }
        
        // Allow octet-stream for any type (generic binary)
        return actualType.contains("application/octet-stream");
    }
}