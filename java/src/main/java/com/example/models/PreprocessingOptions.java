package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreprocessingOptions {
    private int chunkSize;
    private int chunkOverlap;
    private boolean removePII;
    private boolean detectLanguage;
}