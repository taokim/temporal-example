package com.example.activities.io;

import com.example.models.*;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class IOBoundActivitiesImpl implements IOBoundActivities {
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
    
    @Override
    public DownloadResult downloadDocument(String url) {
        long startTime = System.currentTimeMillis();
        ActivityExecutionContext context = Activity.getExecutionContext();
        
        log.info("IO-bound download starting for URL: {}", url);
        context.heartbeat("Starting download from: " + url);
        
        try {
            // Async IO operation
            CompletableFuture<byte[]> downloadFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    URL documentUrl = new URL(url);
                    ReadableByteChannel rbc = Channels.newChannel(documentUrl.openStream());
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytes = 0;
                    
                    try (InputStream is = Channels.newInputStream(rbc)) {
                        while ((bytesRead = is.read(buffer)) != -1) {
                            baos.write(buffer, 0, bytesRead);
                            totalBytes += bytesRead;
                            
                            // Report progress for large downloads
                            if (totalBytes % (1024 * 1024) == 0) {
                                context.heartbeat("Downloaded " + (totalBytes / 1024 / 1024) + " MB");
                            }
                        }
                    }
                    
                    return baos.toByteArray();
                } catch (Exception e) {
                    throw new RuntimeException("Download failed", e);
                }
            }, ioExecutor);
            
            byte[] content = downloadFuture.get();
            
            return DownloadResult.builder()
                .url(url)
                .content(content)
                .contentLength(content.length)
                .contentType(detectContentType(url))
                .downloadTimeMs(System.currentTimeMillis() - startTime)
                .success(true)
                .build();
                
        } catch (Exception e) {
            log.error("Download failed for URL: {}", url, e);
            return DownloadResult.builder()
                .url(url)
                .success(false)
                .error(e.getMessage())
                .downloadTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }
    
    @Override
    public UploadResult uploadToS3(Document document, byte[] content) {
        long startTime = System.currentTimeMillis();
        
        log.info("IO-bound S3 upload for document: {}", document.getId());
        
        try {
            // Simulate S3 upload via MinIO
            String s3Key = "documents/" + document.getId() + "/" + document.getName();
            
            CompletableFuture<String> uploadFuture = CompletableFuture.supplyAsync(() -> {
                // Simulate async upload
                simulateNetworkDelay();
                return "s3://document-bucket/" + s3Key;
            }, ioExecutor);
            
            String s3Url = uploadFuture.get();
            
            return UploadResult.builder()
                .documentId(document.getId())
                .s3Url(s3Url)
                .bucket("document-bucket")
                .key(s3Key)
                .size(content.length)
                .uploadTimeMs(System.currentTimeMillis() - startTime)
                .success(true)
                .build();
                
        } catch (Exception e) {
            log.error("S3 upload failed", e);
            return UploadResult.builder()
                .documentId(document.getId())
                .success(false)
                .error(e.getMessage())
                .uploadTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }
    
    @Override
    public DatabaseResult queryMetadataDatabase(String documentId) {
        long startTime = System.currentTimeMillis();
        
        log.info("IO-bound database query for document: {}", documentId);
        
        try {
            // Simulate async database query
            CompletableFuture<Map<String, Object>> queryFuture = CompletableFuture.supplyAsync(() -> {
                simulateNetworkDelay();
                
                Map<String, Object> result = new HashMap<>();
                result.put("documentId", documentId);
                result.put("status", "processed");
                result.put("processedAt", new Date());
                result.put("metadata", Map.of(
                    "pages", 10,
                    "words", 5000,
                    "language", "en"
                ));
                
                return result;
            }, ioExecutor);
            
            Map<String, Object> queryResult = queryFuture.get();
            
            return DatabaseResult.builder()
                .documentId(documentId)
                .data(queryResult)
                .queryTimeMs(System.currentTimeMillis() - startTime)
                .success(true)
                .build();
                
        } catch (Exception e) {
            log.error("Database query failed", e);
            return DatabaseResult.builder()
                .documentId(documentId)
                .success(false)
                .error(e.getMessage())
                .queryTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }
    
    @Override
    public ChromaDBResult storeVectorEmbeddings(List<VectorEmbedding> embeddings) {
        long startTime = System.currentTimeMillis();
        
        log.info("IO-bound ChromaDB storage for {} embeddings", embeddings.size());
        
        try {
            // Simulate async ChromaDB storage
            CompletableFuture<List<String>> storageFuture = CompletableFuture.supplyAsync(() -> {
                List<String> ids = new ArrayList<>();
                
                for (VectorEmbedding embedding : embeddings) {
                    simulateNetworkDelay();
                    String id = UUID.randomUUID().toString();
                    ids.add(id);
                }
                
                return ids;
            }, ioExecutor);
            
            List<String> embeddingIds = storageFuture.get();
            
            return ChromaDBResult.builder()
                .embeddingIds(embeddingIds)
                .collection("document-embeddings")
                .storedCount(embeddingIds.size())
                .storageTimeMs(System.currentTimeMillis() - startTime)
                .success(true)
                .build();
                
        } catch (Exception e) {
            log.error("ChromaDB storage failed", e);
            return ChromaDBResult.builder()
                .success(false)
                .error(e.getMessage())
                .storageTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }
    
    @Override
    public ExternalAPIResult callExternalAPI(String endpoint, String payload) {
        long startTime = System.currentTimeMillis();
        
        log.info("IO-bound external API call to: {}", endpoint);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> request = new HttpEntity<>(payload, headers);
            
            // Async REST call
            CompletableFuture<ResponseEntity<String>> apiFuture = CompletableFuture.supplyAsync(() -> 
                restTemplate.exchange(endpoint, HttpMethod.POST, request, String.class),
                ioExecutor
            );
            
            ResponseEntity<String> response = apiFuture.get();
            
            return ExternalAPIResult.builder()
                .endpoint(endpoint)
                .statusCode(response.getStatusCode().value())
                .responseBody(response.getBody())
                .headers(response.getHeaders().toSingleValueMap())
                .callTimeMs(System.currentTimeMillis() - startTime)
                .success(response.getStatusCode().is2xxSuccessful())
                .build();
                
        } catch (Exception e) {
            log.error("External API call failed", e);
            return ExternalAPIResult.builder()
                .endpoint(endpoint)
                .success(false)
                .error(e.getMessage())
                .callTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }
    
    // Helper methods
    private String detectContentType(String url) {
        if (url.endsWith(".pdf")) return "application/pdf";
        if (url.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (url.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }
    
    private void simulateNetworkDelay() {
        try {
            // Simulate network latency
            Thread.sleep(50 + new Random().nextInt(150));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}