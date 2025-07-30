package com.example.activities.inference;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import com.example.models.*;

@ActivityInterface
public interface InferenceActivities {
    
    @ActivityMethod
    InferenceBatchResult runInferenceBatch(InferenceBatchInput input);
}