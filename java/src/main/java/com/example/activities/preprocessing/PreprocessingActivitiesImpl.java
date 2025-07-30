package com.example.activities.preprocessing;

import com.example.models.*;
import com.example.utils.DocumentParser;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PreprocessingActivitiesImpl implements PreprocessingActivities {
    
    private final DocumentParser documentParser;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int minChunkSize;
    
    @Value("${preprocessing.remove-pii:false}")
    private boolean removePIIDefault;
    
    public PreprocessingActivitiesImpl() {
        this.documentParser = new DocumentParser();
        this.chunkSize = 1000;      // characters
        this.chunkOverlap = 200;    // characters
        this.minChunkSize = 100;    // minimum chunk size
    }
    
    @Override
    public PreprocessBatchResult preprocessBatch(PreprocessBatchInput input) {
        log.info("Starting preprocessing batch: {}, document count: {}", 
                input.getBatchID(), input.getDocuments().size());
        
        PreprocessBatchResult result = new PreprocessBatchResult();
        result.setBatchID(input.getBatchID());
        result.setSuccess(new ArrayList<>());
        result.setErrors(new ArrayList<>());
        
        // Apply configuration
        boolean removePII = input.getOptions() != null && input.getOptions().isRemovePII() 
                ? input.getOptions().isRemovePII() : removePIIDefault;
        List<String> acceptedLanguages = null; // Not defined in PreprocessingOptions
        
        // Process each document
        for (int i = 0; i < input.getDocuments().size(); i++) {
            IngestedDocument doc = input.getDocuments().get(i);
            
            // Report heartbeat every 5 documents
            if (i % 5 == 0) {
                Activity.getExecutionContext().heartbeat(
                    String.format("Processing document %d/%d", i + 1, input.getDocuments().size())
                );
            }
            
            try {
                PreprocessedDocument preprocessed = processDocument(doc, removePII, acceptedLanguages);
                result.getSuccess().add(preprocessed);
            } catch (Exception e) {
                log.error("Failed to preprocess document {}: {}", 
                        doc.getDocument().getId(), e.getMessage());
                
                result.getErrors().add(ProcessingError.builder()
                        .documentID(doc.getDocument().getId())
                        .stage("preprocessing")
                        .error(e.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build());
            }
        }
        
        log.info("Preprocessing batch completed. Success: {}, Errors: {}", 
                result.getSuccess().size(), result.getErrors().size());
        
        return result;
    }
    
    private PreprocessedDocument processDocument(IngestedDocument doc, 
                                               boolean removePII, 
                                               List<String> acceptedLanguages) throws Exception {
        // Extract text from document
        String text = documentParser.extractText(doc.getContent(), doc.getContentType());
        
        // Clean and normalize text
        text = cleanText(text);
        
        // Remove PII if configured
        if (removePII) {
            text = removePIIFromText(text);
        }
        
        // Detect language
        String language = detectLanguage(text);
        
        // Filter by language if configured
        if (acceptedLanguages != null && !acceptedLanguages.isEmpty() && 
            !acceptedLanguages.contains(language)) {
            throw new IllegalArgumentException(
                String.format("Document language '%s' not in accepted languages", language)
            );
        }
        
        // Create text chunks
        List<TextChunk> chunks = createChunks(doc.getDocument().getId(), text);
        
        return PreprocessedDocument.builder()
                .ingestedDocument(doc)
                .extractedText(text)
                .cleanedText(text)
                .chunks(chunks)
                .language(language)
                .processedAt(LocalDateTime.now())
                .build();
    }
    
    private String cleanText(String text) {
        // Remove excessive whitespace
        text = text.replaceAll("\\s+", " ");
        
        // Remove control characters except newlines and tabs
        text = text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        
        // Normalize line breaks
        text = text.replaceAll("\\r\\n", "\n");
        text = text.replaceAll("\\r", "\n");
        
        // Trim
        text = text.trim();
        
        return text;
    }
    
    private String removePIIFromText(String text) {
        // Email addresses
        Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        text = emailPattern.matcher(text).replaceAll("[EMAIL]");
        
        // Phone numbers (US format)
        Pattern phonePattern = Pattern.compile(
            "\\b(?:\\+?1[-.]?)?\\(?([0-9]{3})\\)?[-.]?([0-9]{3})[-.]?([0-9]{4})\\b"
        );
        text = phonePattern.matcher(text).replaceAll("[PHONE]");
        
        // SSN (US format)
        Pattern ssnPattern = Pattern.compile("\\b[0-9]{3}-[0-9]{2}-[0-9]{4}\\b");
        text = ssnPattern.matcher(text).replaceAll("[SSN]");
        
        // Credit card numbers (basic pattern)
        Pattern ccPattern = Pattern.compile(
            "\\b[0-9]{4}[\\s-]?[0-9]{4}[\\s-]?[0-9]{4}[\\s-]?[0-9]{4}\\b"
        );
        text = ccPattern.matcher(text).replaceAll("[CREDIT_CARD]");
        
        return text;
    }
    
    private String detectLanguage(String text) {
        // Simple language detection based on common words
        // In production, use a proper language detection library
        
        String lowerText = text.toLowerCase();
        
        // English indicators
        String[] englishWords = {"the", "and", "is", "in", "to", "of", "a", "for"};
        int englishCount = 0;
        for (String word : englishWords) {
            if (lowerText.contains(" " + word + " ")) {
                englishCount++;
            }
        }
        
        if (englishCount >= 3) {
            return "en";
        }
        
        // Spanish indicators
        String[] spanishWords = {"el", "la", "de", "que", "y", "en", "un", "es"};
        int spanishCount = 0;
        for (String word : spanishWords) {
            if (lowerText.contains(" " + word + " ")) {
                spanishCount++;
            }
        }
        
        if (spanishCount >= 3) {
            return "es";
        }
        
        return "unknown";
    }
    
    private List<TextChunk> createChunks(String docID, String text) {
        List<TextChunk> chunks = new ArrayList<>();
        int textLen = text.length();
        int chunkID = 0;
        
        for (int start = 0; start < textLen; ) {
            // Calculate end position
            int end = Math.min(start + chunkSize, textLen);
            
            // Try to break at a sentence or paragraph boundary
            if (end < textLen) {
                // Look for sentence ending
                for (int i = end; i > start + minChunkSize && i > end - 100; i--) {
                    char c = text.charAt(i);
                    if ((c == '.' || c == '!' || c == '?') && 
                        i + 1 < textLen && 
                        (text.charAt(i + 1) == ' ' || text.charAt(i + 1) == '\n')) {
                        end = i + 1;
                        break;
                    }
                }
            }
            
            // Extract chunk
            String chunkText = text.substring(start, end).trim();
            
            // Only add non-empty chunks that meet minimum size
            if (chunkText.length() >= minChunkSize) {
                chunks.add(TextChunk.builder()
                        .id(String.format("%s-chunk-%d", docID, chunkID))
                        .text(chunkText)
                        .position(start)
                        .length(chunkText.length())
                        .build());
                chunkID++;
            }
            
            // Move to next chunk with overlap
            if (end >= textLen) {
                break;
            }
            start = end - chunkOverlap;
        }
        
        return chunks;
    }
}