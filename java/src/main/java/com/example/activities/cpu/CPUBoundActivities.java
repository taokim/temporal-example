package com.example.activities.cpu;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import com.example.models.*;

/**
 * CPU-bound activities that require high CPU utilization.
 * These activities should run on CPU-optimized workers.
 */
@ActivityInterface
public interface CPUBoundActivities {
    
    @ActivityMethod
    TextProcessingResult preprocessText(PreprocessingInput input);
    
    @ActivityMethod
    ValidationResult validateDocumentStructure(Document document);
    
    @ActivityMethod
    TextExtractionResult extractTextFromDocument(Document document);
    
    @ActivityMethod
    TokenizationResult tokenizeText(String text, String documentId);
    
    @ActivityMethod
    CompressionResult compressDocument(Document document);
}