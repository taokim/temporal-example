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
public class PreprocessedDocument {
    private IngestedDocument ingestedDocument;
    private String extractedText;
    private String cleanedText;
    private List<TextChunk> chunks;
    private String language;
    private LocalDateTime processedAt;
}