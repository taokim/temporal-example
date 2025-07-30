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
public class TokenizationResult {
    private String documentId;
    private List<String> tokens;
    private List<Integer> tokenIds;
    private int totalTokens;
    private String tokenizer;
    private long tokenizationTimeMs;
}