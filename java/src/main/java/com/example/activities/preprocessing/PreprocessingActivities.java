package com.example.activities.preprocessing;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import com.example.models.*;

@ActivityInterface
public interface PreprocessingActivities {
    
    @ActivityMethod
    PreprocessBatchResult preprocessBatch(PreprocessBatchInput input);
}