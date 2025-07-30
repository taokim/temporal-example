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
public class ValidationResult {
    private String documentId;
    private boolean isValid;
    private List<String> errors;
    private List<String> warnings;
    private DocumentStructure structure;
    private long validationTimeMs;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentStructure {
        private boolean hasTitle;
        private boolean hasContent;
        private boolean hasMetadata;
        private int pageCount;
        private String format;
    }
}