package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestedDocument {
    private Document document;
    private byte[] content;
    private String contentType;
    private long size;
    private LocalDateTime downloadedAt;
}