package com.example.activities.gpu;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import com.example.models.*;
import java.util.List;

/**
 * GPU-bound activities that benefit from parallel processing.
 * These activities should run on GPU-equipped workers.
 */
@ActivityInterface
public interface GPUBoundActivities {
    
    @ActivityMethod
    EmbeddingResult generateEmbeddings(List<TextChunk> chunks);
    
    @ActivityMethod
    ClassificationResult classifyDocument(Document document);
    
    @ActivityMethod
    OCRResult performOCR(Document document);
    
    @ActivityMethod
    ImageAnalysisResult analyzeImages(List<String> imageUrls);
    
    @ActivityMethod
    InferenceResult runLLMInference(InferenceInput input);
}