package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineResult {
    private String workflowId;
    private int totalProcessed;
    private int successCount;
    private int errorCount;
    private double avgQualityScore;
    private String metadataID;
    private List<String> vectorStorageIDs;
    private List<String> s3ObjectKeys;
    private List<ProcessingError> errors;
    
    // Additional fields for resource-optimized workflow
    private int totalDocuments;
    private int successfulDocuments;
    private int failedDocuments;
    private List<DocumentResult> results;
    
    // Compatibility methods
    public int getTotalDocuments() {
        return totalDocuments > 0 ? totalDocuments : totalProcessed;
    }
    
    public int getSuccessfulDocuments() {
        return successfulDocuments > 0 ? successfulDocuments : successCount;
    }
    
    public int getFailedDocuments() {
        return failedDocuments > 0 ? failedDocuments : errorCount;
    }
}