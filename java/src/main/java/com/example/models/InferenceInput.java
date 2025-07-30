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
public class InferenceInput {
    private String documentId;
    private String prompt;
    private String model;
    private Map<String, Object> parameters;
    private int maxTokens;
    private double temperature;
}