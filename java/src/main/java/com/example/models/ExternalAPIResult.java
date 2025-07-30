package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalAPIResult {
    private String endpoint;
    private int statusCode;
    private String responseBody;
    private Map<String, String> headers;
    private long callTimeMs;
    private boolean success;
    private String error;
}