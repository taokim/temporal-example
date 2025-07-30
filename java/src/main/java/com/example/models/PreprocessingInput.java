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
public class PreprocessingInput {
    private String documentId;
    private String documentUrl;
    private String documentType;
    private List<TextChunk> processedChunks;
    private PreprocessingOptions options;
}