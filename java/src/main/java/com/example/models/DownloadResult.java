package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadResult {
    private String url;
    private byte[] content;
    private long contentLength;
    private String contentType;
    private long downloadTimeMs;
    private boolean success;
    private String error;
}