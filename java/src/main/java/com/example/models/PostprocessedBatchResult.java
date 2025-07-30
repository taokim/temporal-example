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
public class PostprocessedBatchResult {
    private String batchID;
    private List<PostprocessedDocument> documents;
    private PipelineSummary summary;
}