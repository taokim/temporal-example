package com.example.config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {
    
    @Value("${temporal.service.target:127.0.0.1:7233}")
    private String temporalServiceAddress;
    
    @Value("${temporal.namespace:default}")
    private String temporalNamespace;
    
    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        WorkflowServiceStubsOptions options = WorkflowServiceStubsOptions.newBuilder()
            .setTarget(temporalServiceAddress)
            .build();
            
        return WorkflowServiceStubs.newServiceStubs(options);
    }
    
    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs service) {
        WorkflowClientOptions options = WorkflowClientOptions.newBuilder()
            .setNamespace(temporalNamespace)
            .build();
            
        return WorkflowClient.newInstance(service, options);
    }
}