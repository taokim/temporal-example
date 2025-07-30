package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InferenceResults {
    private List<ChunkEmbedding> embeddings;
    private String summary;
    private List<Entity> entities;
    private LocalDateTime processedAt;
}