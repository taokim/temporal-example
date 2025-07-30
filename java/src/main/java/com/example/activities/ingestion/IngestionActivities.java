package com.example.activities.ingestion;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import com.example.models.*;
import java.util.List;

@ActivityInterface
public interface IngestionActivities {
    
    @ActivityMethod
    List<Document> parseCSVActivity(String csvPath);
    
    @ActivityMethod
    IngestionBatchResult downloadAndValidateBatch(IngestionBatchInput input);
}