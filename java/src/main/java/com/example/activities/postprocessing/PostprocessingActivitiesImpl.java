package com.example.activities.postprocessing;

import com.example.models.*;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class PostprocessingActivitiesImpl implements PostprocessingActivities {
    
    @Value("${postprocessing.min-quality-score:0.7}")
    private double minQualityScore;
    
    @Override
    public PostprocessedBatchResult postprocessDocuments(PostprocessBatchInput input) {
        log.info("Starting postprocessing, document count: {}", input.getDocuments().size());
        
        PostprocessedBatchResult result = new PostprocessedBatchResult();
        result.setDocuments(new ArrayList<>());
        
        double totalQualityScore = 0.0;
        LocalDateTime startTime = LocalDateTime.now();
        
        // Process each document
        for (int i = 0; i < input.getDocuments().size(); i++) {
            InferredDocument doc = input.getDocuments().get(i);
            
            // Report heartbeat
            if (i % 5 == 0) {
                Activity.getExecutionContext().heartbeat(
                    String.format("Postprocessing document %d/%d", i + 1, input.getDocuments().size())
                );
            }
            
            PostprocessedDocument postprocessedDoc = enhanceDocument(doc);
            result.getDocuments().add(postprocessedDoc);
            totalQualityScore += postprocessedDoc.getQualityScore();
        }
        
        // Calculate summary statistics
        PipelineSummary summary = new PipelineSummary();
        summary.setTotalDocuments(input.getDocuments().size());
        summary.setAvgQualityScore(totalQualityScore / input.getDocuments().size());
        summary.setProcessingTime(Duration.between(startTime, LocalDateTime.now()).toString());
        result.setSummary(summary);
        
        log.info("Postprocessing completed. Documents processed: {}, Avg quality score: {}",
                result.getDocuments().size(), summary.getAvgQualityScore());
        
        return result;
    }
    
    private PostprocessedDocument enhanceDocument(InferredDocument doc) {
        // Calculate quality score
        double qualityScore = calculateQualityScore(doc);
        
        // Build search index metadata
        Map<String, Object> searchIndex = buildSearchIndex(doc);
        
        // Enrich metadata
        Map<String, Object> enrichedMetadata = enrichMetadata(doc, qualityScore);
        
        return PostprocessedDocument.builder()
                .inferredDocument(doc)
                .searchIndex(searchIndex)
                .metadata(enrichedMetadata)
                .qualityScore(qualityScore)
                .build();
    }
    
    private double calculateQualityScore(InferredDocument doc) {
        double score = 0.0;
        double weights = 0.0;
        
        // Content length score (20% weight)
        int contentLength = doc.getPreprocessedDocument().getChunks().stream()
                .mapToInt(chunk -> chunk.getText().length())
                .sum();
        if (contentLength > 0) {
            // Normalize to 0-1 scale (assuming ideal length is 5000 chars)
            double lengthScore = Math.min(contentLength / 5000.0, 1.0);
            score += lengthScore * 0.2;
            weights += 0.2;
        }
        
        // Embedding coverage score (30% weight)
        if (doc.getInferenceResults().getEmbeddings() != null && 
            !doc.getInferenceResults().getEmbeddings().isEmpty()) {
            double coverage = (double) doc.getInferenceResults().getEmbeddings().size() / 
                            doc.getPreprocessedDocument().getChunks().size();
            score += coverage * 0.3;
            weights += 0.3;
        }
        
        // Summary quality score (20% weight)
        if (doc.getInferenceResults().getSummary() != null && 
            !doc.getInferenceResults().getSummary().isEmpty()) {
            // Simple heuristic: good summaries are 50-200 words
            String[] words = doc.getInferenceResults().getSummary().split("\\s+");
            int wordCount = words.length;
            double summaryScore = 0.0;
            if (wordCount >= 50 && wordCount <= 200) {
                summaryScore = 1.0;
            } else if (wordCount < 50) {
                summaryScore = wordCount / 50.0;
            } else {
                summaryScore = Math.max(0, 1.0 - (wordCount - 200) / 200.0);
            }
            score += summaryScore * 0.2;
            weights += 0.2;
        }
        
        // Entity extraction score (15% weight)
        if (doc.getInferenceResults().getEntities() != null && 
            !doc.getInferenceResults().getEntities().isEmpty()) {
            // More entities generally means better extraction
            double entityScore = Math.min(doc.getInferenceResults().getEntities().size() / 10.0, 1.0);
            score += entityScore * 0.15;
            weights += 0.15;
        }
        
        // Language detection confidence (15% weight)
        if (!"unknown".equals(doc.getPreprocessedDocument().getLanguage())) {
            score += 1.0 * 0.15;
            weights += 0.15;
        }
        
        // Normalize score
        if (weights > 0) {
            return score / weights;
        }
        return 0.5; // Default middle score
    }
    
    private Map<String, Object> buildSearchIndex(InferredDocument doc) {
        Map<String, Object> index = new HashMap<>();
        
        // Basic document info
        index.put("doc_id", doc.getPreprocessedDocument().getIngestedDocument().getDocument().getId());
        index.put("doc_name", doc.getPreprocessedDocument().getIngestedDocument().getDocument().getName());
        index.put("doc_type", doc.getPreprocessedDocument().getIngestedDocument().getDocument().getType());
        index.put("language", doc.getPreprocessedDocument().getLanguage());
        
        // Content metadata
        index.put("chunk_count", doc.getPreprocessedDocument().getChunks().size());
        index.put("has_embeddings", doc.getInferenceResults().getEmbeddings() != null && 
                                   !doc.getInferenceResults().getEmbeddings().isEmpty());
        index.put("has_summary", doc.getInferenceResults().getSummary() != null && 
                                !doc.getInferenceResults().getSummary().isEmpty());
        
        // Entity index
        if (doc.getInferenceResults().getEntities() != null && 
            !doc.getInferenceResults().getEntities().isEmpty()) {
            Map<String, List<String>> entityIndex = new HashMap<>();
            for (Entity entity : doc.getInferenceResults().getEntities()) {
                entityIndex.computeIfAbsent(entity.getType(), k -> new ArrayList<>())
                          .add(entity.getText());
            }
            index.put("entities", entityIndex);
        }
        
        // Timestamps
        index.put("downloaded_at", doc.getPreprocessedDocument().getIngestedDocument().getDownloadedAt());
        index.put("processed_at", doc.getPreprocessedDocument().getProcessedAt());
        index.put("inference_at", doc.getInferenceResults().getProcessedAt());
        
        // Custom metadata from original document
        if (doc.getPreprocessedDocument().getIngestedDocument().getDocument().getMetadata() != null) {
            doc.getPreprocessedDocument().getIngestedDocument().getDocument().getMetadata()
                    .forEach((k, v) -> index.put("custom_" + k, v));
        }
        
        return index;
    }
    
    private Map<String, Object> enrichMetadata(InferredDocument doc, double qualityScore) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Quality metrics
        metadata.put("quality_score", qualityScore);
        metadata.put("quality_tier", getQualityTier(qualityScore));
        
        // Processing statistics
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_chunks", doc.getPreprocessedDocument().getChunks().size());
        stats.put("total_embeddings", doc.getInferenceResults().getEmbeddings() != null ? 
                  doc.getInferenceResults().getEmbeddings().size() : 0);
        stats.put("total_entities", doc.getInferenceResults().getEntities() != null ? 
                  doc.getInferenceResults().getEntities().size() : 0);
        stats.put("content_size", doc.getPreprocessedDocument().getIngestedDocument().getSize());
        stats.put("language", doc.getPreprocessedDocument().getLanguage());
        metadata.put("statistics", stats);
        
        // Summary info
        if (doc.getInferenceResults().getSummary() != null && 
            !doc.getInferenceResults().getSummary().isEmpty()) {
            Map<String, Object> summaryInfo = new HashMap<>();
            summaryInfo.put("length", doc.getInferenceResults().getSummary().length());
            summaryInfo.put("word_count", doc.getInferenceResults().getSummary().split("\\s+").length);
            metadata.put("summary_info", summaryInfo);
        }
        
        // Entity summary
        if (doc.getInferenceResults().getEntities() != null && 
            !doc.getInferenceResults().getEntities().isEmpty()) {
            Map<String, Integer> entitySummary = new HashMap<>();
            for (Entity entity : doc.getInferenceResults().getEntities()) {
                entitySummary.merge(entity.getType(), entity.getCount(), Integer::sum);
            }
            metadata.put("entity_summary", entitySummary);
        }
        
        // Processing timestamps
        Map<String, String> timestamps = new HashMap<>();
        timestamps.put("downloaded", doc.getPreprocessedDocument().getIngestedDocument()
                     .getDownloadedAt().toString());
        timestamps.put("preprocessed", doc.getPreprocessedDocument().getProcessedAt().toString());
        timestamps.put("inference_completed", doc.getInferenceResults().getProcessedAt().toString());
        timestamps.put("postprocessed", LocalDateTime.now().toString());
        metadata.put("timestamps", timestamps);
        
        // Recommendations
        List<String> recommendations = generateRecommendations(doc, qualityScore);
        if (!recommendations.isEmpty()) {
            metadata.put("recommendations", recommendations);
        }
        
        return metadata;
    }
    
    private String getQualityTier(double score) {
        if (score >= 0.9) {
            return "excellent";
        } else if (score >= 0.8) {
            return "good";
        } else if (score >= 0.7) {
            return "acceptable";
        } else if (score >= 0.5) {
            return "needs_improvement";
        }
        return "poor";
    }
    
    private List<String> generateRecommendations(InferredDocument doc, double qualityScore) {
        List<String> recommendations = new ArrayList<>();
        
        // Low quality score
        if (qualityScore < minQualityScore) {
            recommendations.add("Consider reprocessing with different parameters");
        }
        
        // Missing embeddings
        if (doc.getInferenceResults().getEmbeddings() == null || 
            doc.getInferenceResults().getEmbeddings().isEmpty()) {
            recommendations.add("No embeddings generated - check model configuration");
        }
        
        // Missing summary
        if (doc.getInferenceResults().getSummary() == null || 
            doc.getInferenceResults().getSummary().isEmpty()) {
            recommendations.add("No summary generated - document may be too short or model unavailable");
        }
        
        // Language issues
        if ("unknown".equals(doc.getPreprocessedDocument().getLanguage())) {
            recommendations.add("Language detection failed - consider manual language setting");
        }
        
        // Small content
        int totalContent = doc.getPreprocessedDocument().getChunks().stream()
                .mapToInt(chunk -> chunk.getText().length())
                .sum();
        if (totalContent < 500) {
            recommendations.add("Document content is very short - verify extraction quality");
        }
        
        return recommendations;
    }
}