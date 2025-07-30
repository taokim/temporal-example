package com.example.workflows;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import com.example.models.PipelineInput;
import com.example.models.PipelineResult;

@WorkflowInterface
public interface ResourceOptimizedWorkflow {
    
    @WorkflowMethod
    PipelineResult runOptimizedPipeline(PipelineInput input);
}