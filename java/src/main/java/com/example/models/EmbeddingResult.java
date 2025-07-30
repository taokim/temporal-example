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
public class EmbeddingResult {
    private String documentId;
    private List<float[]> embeddings;
    private String model;
    private int dimension;
    private String gpuDevice;
    private long inferenceTimeMs;
    private int batchSize;
}