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
public class VectorEmbedding {
    private String id;
    private String documentId;
    private String chunkId;
    private float[] vector;
    private Map<String, String> metadata;
    private String text;
}