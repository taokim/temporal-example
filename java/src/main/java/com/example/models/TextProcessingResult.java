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
public class TextProcessingResult {
    private String documentId;
    private String processedText;
    private String language;
    private List<String> sentences;
    private Map<String, Integer> wordFrequency;
    private int wordCount;
    private int sentenceCount;
    private long processingTimeMs;
    private String workerId;
}