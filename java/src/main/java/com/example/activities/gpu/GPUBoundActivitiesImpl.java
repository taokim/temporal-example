package com.example.activities.gpu;

import com.example.models.*;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class GPUBoundActivitiesImpl implements GPUBoundActivities {
    
    // Simulate GPU resource management
    private final GPUResourceManager gpuManager = new GPUResourceManager();
    
    @Override
    public EmbeddingResult generateEmbeddings(List<TextChunk> chunks) {
        long startTime = System.currentTimeMillis();
        ActivityExecutionContext context = Activity.getExecutionContext();
        
        String gpuDevice = gpuManager.acquireGPU();
        log.info("Generating embeddings on GPU: {} for {} chunks", gpuDevice, chunks.size());
        
        try {
            context.heartbeat("Processing on GPU: " + gpuDevice);
            
            // Optimal batch size for GPU memory
            int batchSize = calculateOptimalBatchSize(chunks.size());
            List<float[]> embeddings = new ArrayList<>();
            
            // Process in batches for GPU efficiency
            for (int i = 0; i < chunks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, chunks.size());
                List<TextChunk> batch = chunks.subList(i, end);
                
                context.heartbeat(String.format("Processing batch %d-%d of %d", 
                    i, end, chunks.size()));
                
                // Simulate GPU processing
                List<float[]> batchEmbeddings = processBatchOnGPU(batch, gpuDevice);
                embeddings.addAll(batchEmbeddings);
            }
            
            return EmbeddingResult.builder()
                .documentId(chunks.get(0).getDocumentId())
                .embeddings(embeddings)
                .model("text-embedding-ada-002")
                .dimension(1536)
                .gpuDevice(gpuDevice)
                .inferenceTimeMs(System.currentTimeMillis() - startTime)
                .batchSize(batchSize)
                .build();
                
        } finally {
            gpuManager.releaseGPU(gpuDevice);
        }
    }
    
    @Override
    public ClassificationResult classifyDocument(Document document) {
        long startTime = System.currentTimeMillis();
        String gpuDevice = gpuManager.acquireGPU();
        
        log.info("Classifying document {} on GPU: {}", document.getId(), gpuDevice);
        
        try {
            // Simulate GPU-based classification
            Map<String, Double> categoryScores = runClassificationModel(document, gpuDevice);
            
            String primaryCategory = categoryScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
            
            return ClassificationResult.builder()
                .documentId(document.getId())
                .primaryCategory(primaryCategory)
                .categoryScores(categoryScores)
                .model("bert-classifier")
                .gpuDevice(gpuDevice)
                .inferenceTimeMs(System.currentTimeMillis() - startTime)
                .confidence(categoryScores.get(primaryCategory).floatValue())
                .build();
                
        } finally {
            gpuManager.releaseGPU(gpuDevice);
        }
    }
    
    @Override
    public OCRResult performOCR(Document document) {
        long startTime = System.currentTimeMillis();
        String gpuDevice = gpuManager.acquireGPU();
        
        log.info("Performing OCR on document {} using GPU: {}", document.getId(), gpuDevice);
        
        try {
            // Simulate GPU-accelerated OCR
            String extractedText = "GPU-extracted text from " + document.getName();
            List<OCRResult.BoundingBox> textRegions = detectTextRegions(document, gpuDevice);
            
            return OCRResult.builder()
                .documentId(document.getId())
                .extractedText(extractedText)
                .textRegions(textRegions)
                .language("en")
                .confidence(0.95f)
                .gpuDevice(gpuDevice)
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();
                
        } finally {
            gpuManager.releaseGPU(gpuDevice);
        }
    }
    
    @Override
    public ImageAnalysisResult analyzeImages(List<String> imageUrls) {
        long startTime = System.currentTimeMillis();
        String gpuDevice = gpuManager.acquireGPU();
        
        log.info("Analyzing {} images on GPU: {}", imageUrls.size(), gpuDevice);
        
        try {
            List<ImageAnalysisResult.ImageInfo> imageInfos = imageUrls.stream()
                .map(url -> analyzeImageOnGPU(url, gpuDevice))
                .collect(Collectors.toList());
            
            return ImageAnalysisResult.builder()
                .images(imageInfos)
                .gpuDevice(gpuDevice)
                .totalProcessingTimeMs(System.currentTimeMillis() - startTime)
                .build();
                
        } finally {
            gpuManager.releaseGPU(gpuDevice);
        }
    }
    
    @Override
    public InferenceResult runLLMInference(InferenceInput input) {
        long startTime = System.currentTimeMillis();
        String gpuDevice = gpuManager.acquireGPU();
        
        log.info("Running LLM inference on GPU: {} for document: {}", 
                gpuDevice, input.getDocumentId());
        
        try {
            // Simulate LLM inference on GPU
            String generatedText = performLLMInference(input, gpuDevice);
            
            return InferenceResult.builder()
                .documentId(input.getDocumentId())
                .requestId(UUID.randomUUID().toString())
                .generatedText(generatedText)
                .model(input.getModel())
                .promptTokens(input.getPrompt().length() / 4)
                .completionTokens(generatedText.length() / 4)
                .totalTokens((input.getPrompt().length() + generatedText.length()) / 4)
                .inferenceTimeMs(System.currentTimeMillis() - startTime)
                .build();
                
        } finally {
            gpuManager.releaseGPU(gpuDevice);
        }
    }
    
    // Helper methods
    private int calculateOptimalBatchSize(int totalItems) {
        // Calculate batch size based on GPU memory
        // Assume 8GB GPU can handle 32 items per batch
        return Math.min(32, totalItems);
    }
    
    private List<float[]> processBatchOnGPU(List<TextChunk> batch, String gpuDevice) {
        // Simulate GPU processing
        List<float[]> embeddings = new ArrayList<>();
        for (TextChunk chunk : batch) {
            embeddings.add(generateEmbedding(chunk.getContent()));
        }
        return embeddings;
    }
    
    private float[] generateEmbedding(String text) {
        // Simulate embedding generation
        float[] embedding = new float[1536];
        Random random = new Random(text.hashCode());
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = random.nextFloat();
        }
        return embedding;
    }
    
    private Map<String, Double> runClassificationModel(Document document, String gpuDevice) {
        // Simulate classification scores
        Map<String, Double> scores = new HashMap<>();
        scores.put("technical", 0.85);
        scores.put("business", 0.10);
        scores.put("legal", 0.03);
        scores.put("other", 0.02);
        return scores;
    }
    
    private List<OCRResult.BoundingBox> detectTextRegions(Document document, String gpuDevice) {
        // Simulate text region detection
        List<OCRResult.BoundingBox> regions = new ArrayList<>();
        regions.add(OCRResult.BoundingBox.builder()
            .x(10).y(10).width(200).height(50)
            .text("Title Text")
            .confidence(0.98f)
            .build());
        return regions;
    }
    
    private ImageAnalysisResult.ImageInfo analyzeImageOnGPU(String url, String gpuDevice) {
        // Simulate image analysis
        return ImageAnalysisResult.ImageInfo.builder()
            .url(url)
            .objects(Arrays.asList("person", "document", "computer"))
            .objectConfidence(Map.of("person", 0.95f, "document", 0.88f, "computer", 0.72f))
            .caption("A person working with documents on a computer")
            .tags(Arrays.asList("office", "work", "technology"))
            .build();
    }
    
    private String performLLMInference(InferenceInput input, String gpuDevice) {
        // Simulate LLM response
        return "Based on the document analysis: " + input.getPrompt() + 
               "\n\nThe document appears to contain relevant information about " +
               "the topic. Further analysis is recommended.";
    }
    
    // Simulated GPU Resource Manager
    private static class GPUResourceManager {
        private final List<String> availableGPUs = Arrays.asList("GPU-0", "GPU-1");
        private final Set<String> inUse = Collections.synchronizedSet(new HashSet<>());
        
        public synchronized String acquireGPU() {
            for (String gpu : availableGPUs) {
                if (!inUse.contains(gpu)) {
                    inUse.add(gpu);
                    return gpu;
                }
            }
            // Wait for available GPU
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return acquireGPU(); // Retry
        }
        
        public synchronized void releaseGPU(String gpu) {
            inUse.remove(gpu);
        }
    }
}