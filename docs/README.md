# Documentation

This directory contains detailed documentation for the Temporal-based document processing and RAG pipeline project.

## Contents

### Workflow Orchestration
- [Workflow Orchestration Comparison](./workflow-orchestration-comparison.md) - Detailed comparison of Temporal, Airflow, LangChain, and other workflow orchestration approaches
- [Temporal vs Airflow: Scalability Deep Dive](./temporal-vs-airflow-scalability.md) - Real-world scalability comparison and migration strategies
- [Temporal vs Kafka: Architectural Considerations](./temporal-vs-kafka-architectural-considerations.md) - **NEW** Why complex Kafka flows become anti-patterns and when Temporal excels
- [Temporal SDK Comparison: Go vs Java vs Python](./temporal-sdk-comparison.md) - Performance benchmarks, use cases, and selection guide
- [AI Workflow Selection Guide](./ai-workflow-selection-guide.md) - Decision criteria, selection matrix, and use case examples
- [AI Workflow Evolution](./ai-workflow-evolution.md) - Understanding the evolution from static orchestration to autonomous AI systems
- [Document RAG Pipeline Recommendation](./document-rag-pipeline-recommendation.md) - Specific implementation recommendations for document processing to RAG pipeline
- [Workflow Implementation Examples](./workflow-implementation-examples.md) - Complete workflow and activity implementations in Java and Go
- [GPU vs CPU-Bound Job Patterns](./gpu-cpu-bound-job-patterns.md) - **NEW** Resource optimization strategies for ML inference and compute-intensive workloads

## Quick Links

### For Decision Makers
- Start with the [AI Workflow Selection Guide](./ai-workflow-selection-guide.md) to understand which approach fits your needs
- Review the [Document RAG Pipeline Recommendation](./document-rag-pipeline-recommendation.md) for specific implementation guidance

### For Developers
- Check the [Workflow Orchestration Comparison](./workflow-orchestration-comparison.md) for technical details
- Study the [AI Workflow Evolution](./ai-workflow-evolution.md) to prepare for future changes
- **Essential**: [GPU vs CPU-Bound Job Patterns](./gpu-cpu-bound-job-patterns.md) for ML and compute workload optimization

### For Architects
- Review all documents to understand the full landscape
- Pay special attention to the migration strategies in [AI Workflow Evolution](./ai-workflow-evolution.md)
- **Critical Read**: [Temporal vs Kafka: Architectural Considerations](./temporal-vs-kafka-architectural-considerations.md) for avoiding complex Kafka anti-patterns
- **Performance Critical**: [GPU vs CPU-Bound Job Patterns](./gpu-cpu-bound-job-patterns.md) for resource allocation and infrastructure planning