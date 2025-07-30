package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InferenceResult {
    private String documentId;
    private String requestId;
    private String generatedText;
    private String model;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private long inferenceTimeMs;
    private String error;
}