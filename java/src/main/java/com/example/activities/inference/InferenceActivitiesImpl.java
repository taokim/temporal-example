package com.example.activities.inference;

import com.example.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class InferenceActivitiesImpl implements InferenceActivities {
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String llmServiceURL;
    private final boolean useMockService;
    
    public InferenceActivitiesImpl(
            @Value("${llm.service.url:http://localhost:8081}") String llmServiceURL,
            @Value("${llm.use-mock:true}") boolean useMockService) {
        this.llmServiceURL = llmServiceURL;
        this.useMockService = useMockService;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.MINUTES)
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public InferenceBatchResult runInferenceBatch(InferenceBatchInput input) {
        log.info("Starting inference batch: {}, document count: {}", 
                input.getBatchID(), input.getDocuments().size());
        
        InferenceBatchResult result = new InferenceBatchResult();
        result.setBatchID(input.getBatchID());
        result.setSuccess(new ArrayList<>());
        result.setErrors(new ArrayList<>());
        
        // Process each document
        for (int i = 0; i < input.getDocuments().size(); i++) {
            PreprocessedDocument doc = input.getDocuments().get(i);
            
            // Report heartbeat every 5 documents
            if (i % 5 == 0) {
                Activity.getExecutionContext().heartbeat(
                    String.format("Processing document %d/%d", i + 1, input.getDocuments().size())
                );
            }
            
            try {
                InferredDocument inferredDoc = processDocument(doc, input.getModels());
                result.getSuccess().add(inferredDoc);
            } catch (Exception e) {
                log.error("Failed to process document {}: {}", 
                        doc.getIngestedDocument().getDocument().getId(), e.getMessage());
                
                result.getErrors().add(ProcessingError.builder()
                        .documentID(doc.getIngestedDocument().getDocument().getId())
                        .stage("inference")
                        .error(e.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build());
            }
        }
        
        log.info("Inference batch completed. Success: {}, Errors: {}", 
                result.getSuccess().size(), result.getErrors().size());
        
        return result;
    }
    
    private InferredDocument processDocument(PreprocessedDocument doc, ModelConfig config) throws Exception {
        InferredDocument inferredDoc = new InferredDocument();
        inferredDoc.setPreprocessedDocument(doc);
        
        InferenceResults results = new InferenceResults();
        
        // Generate embeddings for each chunk
        if (config.isGenerateEmbeddings()) {
            List<ChunkEmbedding> embeddings = new ArrayList<>();
            
            for (int i = 0; i < doc.getChunks().size(); i++) {
                TextChunk chunk = doc.getChunks().get(i);
                float[] embedding = generateEmbedding(chunk.getText(), config.getEmbeddingModel());
                
                embeddings.add(ChunkEmbedding.builder()
                        .chunkID(chunk.getId())
                        .embedding(embedding)
                        .model(config.getEmbeddingModel())
                        .build());
            }
            
            results.setEmbeddings(embeddings);
        }
        
        // Generate summary
        if (config.isGenerateSummary()) {
            StringBuilder fullText = new StringBuilder();
            for (TextChunk chunk : doc.getChunks()) {
                fullText.append(chunk.getText()).append("\n");
            }
            
            String summary = generateSummary(fullText.toString(), config.getSummaryModel());
            results.setSummary(summary);
        }
        
        // Extract entities (mock for now)
        if (config.isExtractEntities()) {
            List<Entity> entities = extractEntities(doc.getChunks());
            results.setEntities(entities);
        }
        
        results.setProcessedAt(LocalDateTime.now());
        inferredDoc.setInferenceResults(results);
        
        return inferredDoc;
    }
    
    private float[] generateEmbedding(String text, String model) throws IOException {
        if (useMockService) {
            return generateMockEmbedding(text, model);
        }
        
        // Real OpenAI API call would go here
        return generateMockEmbedding(text, model);
    }
    
    private float[] generateMockEmbedding(String text, String model) throws IOException {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", text
        );
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        
        Request request = new Request.Builder()
                .url(llmServiceURL + "/v1/embeddings")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Embedding service returned status " + response.code());
            }
            
            String responseBody = response.body().string();
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseMap.get("data");
            if (data == null || data.isEmpty()) {
                throw new IOException("No embeddings returned");
            }
            
            List<Double> embeddingList = (List<Double>) data.get(0).get("embedding");
            float[] embedding = new float[embeddingList.size()];
            for (int i = 0; i < embeddingList.size(); i++) {
                embedding[i] = embeddingList.get(i).floatValue();
            }
            
            return embedding;
        }
    }
    
    private String generateSummary(String text, String model) throws IOException {
        if (useMockService) {
            return generateMockSummary(text, model);
        }
        
        // Real LLM API call would go here
        return generateMockSummary(text, model);
    }
    
    private String generateMockSummary(String text, String model) throws IOException {
        String prompt = "Please summarize the following document in 2-3 sentences:\n\n" + text;
        
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "You are a helpful assistant that creates concise summaries."
                        ),
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                )
        );
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        
        Request request = new Request.Builder()
                .url(llmServiceURL + "/v1/chat/completions")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Summary service returned status " + response.code());
            }
            
            String responseBody = response.body().string();
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IOException("No summary returned");
            }
            
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        }
    }
    
    private List<Entity> extractEntities(List<TextChunk> chunks) {
        // Mock entity extraction
        // In real implementation, this would use spaCy or similar NER service
        return List.of(
                Entity.builder()
                        .text("John Doe")
                        .type("PERSON")
                        .count(3)
                        .build(),
                Entity.builder()
                        .text("Acme Corporation")
                        .type("ORG")
                        .count(5)
                        .build(),
                Entity.builder()
                        .text("San Francisco")
                        .type("LOC")
                        .count(2)
                        .build(),
                Entity.builder()
                        .text("2024-01-15")
                        .type("DATE")
                        .count(1)
                        .build()
        );
    }
}