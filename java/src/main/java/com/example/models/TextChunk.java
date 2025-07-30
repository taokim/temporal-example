package com.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextChunk {
    private String id;
    private String documentId;
    private String chunkId;
    private String text;
    private String content;  // Alias for text
    private int position;
    private int length;
    
    // Helper methods for compatibility
    public String getContent() {
        return content != null ? content : text;
    }
    
    public String getChunkId() {
        return chunkId != null ? chunkId : id;
    }
}