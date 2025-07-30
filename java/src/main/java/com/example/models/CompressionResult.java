package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompressionResult {
    private String documentId;
    private byte[] compressedData;
    private long originalSize;
    private long compressedSize;
    private double compressionRatio;
    private String algorithm;
    private long compressionTimeMs;
}