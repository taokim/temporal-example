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
public class OCRResult {
    private String documentId;
    private String extractedText;
    private List<BoundingBox> textRegions;
    private String language;
    private float confidence;
    private String gpuDevice;
    private long processingTimeMs;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoundingBox {
        private int x;
        private int y;
        private int width;
        private int height;
        private String text;
        private float confidence;
    }
}