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
public class DocumentResult {
    private String documentId;
    private String documentName;
    private String status;
    private String s3Url;
    private List<String> embeddingIds;
    private long processingTimeMs;
    private String error;
}