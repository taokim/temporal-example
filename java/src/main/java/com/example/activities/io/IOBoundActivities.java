package com.example.activities.io;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import com.example.models.*;
import java.util.List;

/**
 * IO-bound activities that wait on external resources.
 * These activities can run on standard workers with async IO.
 */
@ActivityInterface
public interface IOBoundActivities {
    
    @ActivityMethod
    DownloadResult downloadDocument(String url);
    
    @ActivityMethod
    UploadResult uploadToS3(Document document, byte[] content);
    
    @ActivityMethod
    DatabaseResult queryMetadataDatabase(String documentId);
    
    @ActivityMethod
    ChromaDBResult storeVectorEmbeddings(List<VectorEmbedding> embeddings);
    
    @ActivityMethod
    ExternalAPIResult callExternalAPI(String endpoint, String payload);
}