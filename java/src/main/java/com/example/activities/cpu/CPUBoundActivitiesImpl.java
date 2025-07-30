package com.example.activities.cpu;

import com.example.models.*;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayOutputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class CPUBoundActivitiesImpl implements CPUBoundActivities {
    
    private final ExecutorService cpuIntensiveExecutor = createCPUOptimizedExecutor();
    
    @Override
    public TextProcessingResult preprocessText(PreprocessingInput input) {
        long startTime = System.currentTimeMillis();
        ActivityExecutionContext context = Activity.getExecutionContext();
        String workerId = context.getInfo().getActivityId();
        
        log.info("CPU-bound text preprocessing started on worker: {}", workerId);
        context.heartbeat("Starting text preprocessing");
        
        try {
            // Use all available CPU cores for parallel processing
            return cpuIntensiveExecutor.submit(() -> {
                String text = input.getProcessedChunks().stream()
                    .map(TextChunk::getContent)
                    .collect(Collectors.joining(" "));
                
                // CPU-intensive operations
                String processedText = normalizeText(text);
                List<String> sentences = splitIntoSentences(processedText);
                Map<String, Integer> wordFrequency = calculateWordFrequency(processedText);
                String language = detectLanguage(processedText);
                
                context.heartbeat("Processed " + sentences.size() + " sentences");
                
                return TextProcessingResult.builder()
                    .documentId(input.getDocumentId())
                    .processedText(processedText)
                    .language(language)
                    .sentences(sentences)
                    .wordFrequency(wordFrequency)
                    .wordCount(wordFrequency.values().stream().mapToInt(Integer::intValue).sum())
                    .sentenceCount(sentences.size())
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .workerId(workerId)
                    .build();
            }).get();
        } catch (Exception e) {
            log.error("Text preprocessing failed", e);
            throw new RuntimeException("Text preprocessing failed", e);
        }
    }
    
    @Override
    public ValidationResult validateDocumentStructure(Document document) {
        long startTime = System.currentTimeMillis();
        log.info("Validating document structure for: {}", document.getId());
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // CPU-intensive validation
        if (document.getName() == null || document.getName().isEmpty()) {
            errors.add("Document name is missing");
        }
        
        if (document.getType() == null || document.getType().isEmpty()) {
            errors.add("Document type is missing");
        }
        
        // Simulate complex validation logic
        boolean hasTitle = document.getMetadata() != null && 
                          document.getMetadata().containsKey("title");
        boolean hasContent = document.getMetadataRaw() != null && 
                           !document.getMetadataRaw().isEmpty();
        
        ValidationResult.DocumentStructure structure = ValidationResult.DocumentStructure.builder()
            .hasTitle(hasTitle)
            .hasContent(hasContent)
            .hasMetadata(document.getMetadata() != null)
            .pageCount(extractPageCount(document))
            .format(document.getType())
            .build();
        
        return ValidationResult.builder()
            .documentId(document.getId())
            .isValid(errors.isEmpty())
            .errors(errors)
            .warnings(warnings)
            .structure(structure)
            .validationTimeMs(System.currentTimeMillis() - startTime)
            .build();
    }
    
    @Override
    public TextExtractionResult extractTextFromDocument(Document document) {
        long startTime = System.currentTimeMillis();
        int cpuCores = Runtime.getRuntime().availableProcessors();
        
        log.info("Extracting text from document: {} using {} CPU cores", 
                document.getId(), cpuCores);
        
        // Simulate CPU-intensive text extraction
        String extractedText = simulateTextExtraction(document);
        List<String> pages = splitIntoPages(extractedText);
        
        return TextExtractionResult.builder()
            .documentId(document.getId())
            .extractedText(extractedText)
            .pages(pages)
            .metadata(document.getMetadata())
            .mimeType(getMimeType(document.getType()))
            .extractionTimeMs(System.currentTimeMillis() - startTime)
            .cpuCoresUsed(cpuCores)
            .build();
    }
    
    @Override
    public TokenizationResult tokenizeText(String text, String documentId) {
        long startTime = System.currentTimeMillis();
        
        log.info("Tokenizing text for document: {}", documentId);
        
        // CPU-intensive tokenization
        List<String> tokens = performTokenization(text);
        List<Integer> tokenIds = tokens.stream()
            .map(this::getTokenId)
            .collect(Collectors.toList());
        
        return TokenizationResult.builder()
            .documentId(documentId)
            .tokens(tokens)
            .tokenIds(tokenIds)
            .totalTokens(tokens.size())
            .tokenizer("custom-cpu-tokenizer")
            .tokenizationTimeMs(System.currentTimeMillis() - startTime)
            .build();
    }
    
    @Override
    public CompressionResult compressDocument(Document document) {
        long startTime = System.currentTimeMillis();
        
        log.info("Compressing document: {}", document.getId());
        
        try {
            String content = document.getMetadataRaw() != null ? 
                           document.getMetadataRaw() : "";
            byte[] originalData = content.getBytes(StandardCharsets.UTF_8);
            
            // CPU-intensive compression
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                gzipOut.write(originalData);
            }
            
            byte[] compressedData = baos.toByteArray();
            
            return CompressionResult.builder()
                .documentId(document.getId())
                .compressedData(compressedData)
                .originalSize(originalData.length)
                .compressedSize(compressedData.length)
                .compressionRatio((double) compressedData.length / originalData.length)
                .algorithm("GZIP")
                .compressionTimeMs(System.currentTimeMillis() - startTime)
                .build();
        } catch (Exception e) {
            log.error("Compression failed", e);
            throw new RuntimeException("Compression failed", e);
        }
    }
    
    // Helper methods
    private ExecutorService createCPUOptimizedExecutor() {
        int coreCount = Runtime.getRuntime().availableProcessors();
        return new ForkJoinPool(coreCount);
    }
    
    private String normalizeText(String text) {
        // CPU-intensive normalization
        return text.toLowerCase()
            .replaceAll("[^a-zA-Z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
    
    private List<String> splitIntoSentences(String text) {
        // Simple sentence splitting
        return Arrays.asList(text.split("\\. "));
    }
    
    private Map<String, Integer> calculateWordFrequency(String text) {
        Map<String, Integer> frequency = new ConcurrentHashMap<>();
        String[] words = text.split("\\s+");
        
        Arrays.stream(words).parallel().forEach(word -> 
            frequency.merge(word, 1, Integer::sum)
        );
        
        return frequency;
    }
    
    private String detectLanguage(String text) {
        // Simplified language detection
        return "en"; // Default to English
    }
    
    private int extractPageCount(Document document) {
        if (document.getMetadata() != null && 
            document.getMetadata().containsKey("pageCount")) {
            return Integer.parseInt(document.getMetadata().get("pageCount"));
        }
        return 1;
    }
    
    private String simulateTextExtraction(Document document) {
        // Simulate extraction based on document type
        return "Extracted text from " + document.getName();
    }
    
    private List<String> splitIntoPages(String text) {
        // Simple page splitting simulation
        List<String> pages = new ArrayList<>();
        int pageSize = 1000;
        for (int i = 0; i < text.length(); i += pageSize) {
            pages.add(text.substring(i, Math.min(i + pageSize, text.length())));
        }
        return pages;
    }
    
    private String getMimeType(String type) {
        Map<String, String> mimeTypes = Map.of(
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "txt", "text/plain"
        );
        return mimeTypes.getOrDefault(type, "application/octet-stream");
    }
    
    private List<String> performTokenization(String text) {
        // Simple word-based tokenization
        return Arrays.asList(text.split("\\s+"));
    }
    
    private int getTokenId(String token) {
        // Simple hash-based token ID
        return Math.abs(token.hashCode() % 10000);
    }
}