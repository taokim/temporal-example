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
public class DatabaseResult {
    private String documentId;
    private Map<String, Object> data;
    private long queryTimeMs;
    private boolean success;
    private String error;
}