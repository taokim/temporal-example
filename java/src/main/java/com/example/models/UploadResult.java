package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResult {
    private String documentId;
    private String s3Url;
    private String bucket;
    private String key;
    private long size;
    private long uploadTimeMs;
    private boolean success;
    private String error;
}