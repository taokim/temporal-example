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
public class ChromaDBResult {
    private List<String> embeddingIds;
    private String collection;
    private int storedCount;
    private long storageTimeMs;
    private boolean success;
    private String error;
}