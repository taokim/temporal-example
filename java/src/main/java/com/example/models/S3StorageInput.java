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
public class S3StorageInput {
    private String bucket;
    private String keyPrefix;
    private List<PostprocessedDocument> documents;
}