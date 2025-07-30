package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataStorageInput {
    private String pipelineID;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<PostprocessedDocument> documents;
    private List<ProcessingError> processingErrors;
    private Map<String, Object> config;
}