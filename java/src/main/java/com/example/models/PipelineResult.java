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
    private int totalProcessed;
    private int successCount;
    private int errorCount;
    private double avgQualityScore;
    private String metadataID;
    private List<String> vectorStorageIDs;
    private List<String> s3ObjectKeys;
    private List<ProcessingError> errors;
}