package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextExtractionResult {
    private String documentId;
    private String extractedText;
    private List<String> pages;
    private Map<String, String> metadata;
    private String mimeType;
    private long extractionTimeMs;
    private int cpuCoresUsed;
}