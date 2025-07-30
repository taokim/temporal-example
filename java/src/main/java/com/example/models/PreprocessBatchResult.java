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
public class PreprocessBatchResult {
    private String batchID;
    private List<PreprocessedDocument> success;
    private List<ProcessingError> errors;
}