package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageAnalysisResult {
    private List<ImageInfo> images;
    private String gpuDevice;
    private long totalProcessingTimeMs;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageInfo {
        private String url;
        private List<String> objects;
        private Map<String, Float> objectConfidence;
        private String caption;
        private List<String> tags;
    }
}