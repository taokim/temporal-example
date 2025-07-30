package com.example.activities.storage;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import com.example.models.*;

@ActivityInterface
public interface StorageActivities {
    
    @ActivityMethod
    VectorDBStorageResult storeInVectorDB(VectorDBStorageInput input);
    
    @ActivityMethod
    S3StorageResult storeInS3(S3StorageInput input);
    
    @ActivityMethod
    MetadataStorageResult storeMetadata(MetadataStorageInput input);
}