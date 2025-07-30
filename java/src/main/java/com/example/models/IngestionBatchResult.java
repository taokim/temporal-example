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
public class IngestionBatchResult {
    private String batchID;
    private List<IngestedDocument> success;
    private List<ProcessingError> errors;
}