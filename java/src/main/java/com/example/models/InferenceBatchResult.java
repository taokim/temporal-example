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
public class InferenceBatchResult {
    private String batchID;
    private List<InferredDocument> success;
    private List<ProcessingError> errors;
}