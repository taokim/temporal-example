package com.example.activities.postprocessing;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import com.example.models.*;

@ActivityInterface
public interface PostprocessingActivities {
    
    @ActivityMethod
    PostprocessedBatchResult postprocessDocuments(PostprocessBatchInput input);
}