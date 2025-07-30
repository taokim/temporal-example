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
public class PostprocessedDocument {
    private InferredDocument inferredDocument;
    private Map<String, Object> searchIndex;
    private Map<String, Object> metadata;
    private double qualityScore;
}