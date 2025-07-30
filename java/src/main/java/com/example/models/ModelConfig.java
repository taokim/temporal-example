package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfig {
    private boolean generateEmbeddings;
    private String embeddingModel;
    private boolean generateSummary;
    private String summaryModel;
    private boolean extractEntities;
}