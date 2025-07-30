package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationResult {
    private String documentId;
    private String primaryCategory;
    private Map<String, Double> categoryScores;
    private String model;
    private String gpuDevice;
    private long inferenceTimeMs;
    private float confidence;
}