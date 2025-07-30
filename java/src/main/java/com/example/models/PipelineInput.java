package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineInput {
    private String csvPath;
    private int batchSize;
    private int maxSizeMB;
    private PreprocessingOptions preprocessingOptions;
    private ModelConfig modelConfig;
    private String chromaCollection;
    private String s3Bucket;
}