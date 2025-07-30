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
public class InferenceBatchInput {
    private String batchID;
    private List<PreprocessedDocument> documents;
    private ModelConfig models;
}