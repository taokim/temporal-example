# Temporal Example Documentation

This directory contains comprehensive documentation for building production-ready Temporal workflows, with a focus on AI/ML pipelines and resource-optimized execution patterns.

## 📚 Documentation Structure

### 🏗️ [Architecture](./architecture/)
Core architectural patterns and design decisions for Temporal-based systems.

- **[AI Pipeline Implementation](./architecture/ai-pipeline-implementation.md)** - Building 5-stage AI pipelines with Temporal
- **[Workflow Evolution](./architecture/ai-workflow-evolution.md)** - Evolution from basic to advanced workflow patterns
- **[Workflow Orchestration Comparison](./architecture/workflow-orchestration-comparison.md)** - Comparing different orchestration approaches
- **[Vector Embeddings Architecture](./architecture/temporal-vector-embeddings.md)** - Temporal for vector embedding generation

### 🔄 [Comparisons](./comparisons/)
Detailed comparisons between Temporal and other workflow orchestration systems.

- **[Temporal vs Airflow](./comparisons/temporal-vs-airflow-scalability.md)** - Scalability and architectural differences
- **[Temporal vs Kafka](./comparisons/temporal-vs-kafka-architectural-considerations.md)** - When to use each system
- **[SDK Comparison](./comparisons/temporal-sdk-comparison.md)** - Go vs Java vs Python SDK comparison

### 📖 [Guides](./guides/)
Step-by-step guides and best practices for implementing Temporal workflows.

- **[Workflow Selection Guide](./guides/ai-workflow-selection-guide.md)** - Choosing the right workflow pattern
- **[Resource-Optimized Execution](./guides/resource-optimized-execution-guide.md)** - CPU/GPU/IO task separation
- **[Document RAG Pipeline](./guides/document-rag-pipeline-recommendation.md)** - Building RAG pipelines with Temporal
- **[Documentation Guidelines](./guides/DOCUMENTATION_GUIDELINES.md)** - Standards for writing documentation

### 🎯 [Patterns](./patterns/)
Common patterns and implementation examples for Temporal workflows.

- **[CPU/GPU Bound Job Patterns](./patterns/gpu-cpu-bound-job-patterns.md)** - Patterns for resource-specific activities
- **[Implementation Examples](./patterns/workflow-implementation-examples.md)** - Real-world workflow examples
- **[CPU/GPU/IO Implementation Summary](./patterns/cpu-gpu-io-implementation-summary.md)** - Summary of resource optimization patterns

### 🚀 [Quickstart](./quickstart/)
Quick guides to get started with Temporal in different languages.

- **[Python Implementation Guide](./quickstart/python-workflow-implementation.md)** - Getting started with Python SDK

## 🌐 Language Support

Most documentation is available in both English and Korean:
- English: `filename.md`
- Korean: `filename_ko.md`

## 📋 Quick Links

### By Use Case
- **Building AI Pipelines**: Start with [AI Pipeline Implementation](./architecture/ai-pipeline-implementation.md)
- **Resource Optimization**: See [Resource-Optimized Execution Guide](./guides/resource-optimized-execution-guide.md)
- **Choosing Temporal**: Read [Temporal vs Airflow](./comparisons/temporal-vs-airflow-scalability.md) and [Temporal vs Kafka](./comparisons/temporal-vs-kafka-architectural-considerations.md)
- **Getting Started**: Check language-specific READMEs in `/go`, `/java`, or `/python` directories

### By Technical Level
- **Beginners**: Start with [Quickstart guides](./quickstart/) and [Workflow Selection Guide](./guides/ai-workflow-selection-guide.md)
- **Intermediate**: Explore [Patterns](./patterns/) and [Implementation Examples](./patterns/workflow-implementation-examples.md)
- **Advanced**: Deep dive into [Architecture](./architecture/) and [Comparisons](./comparisons/)

## 🔍 Finding Information

1. **Use Case Based**: If you know what you want to build, start with the guides
2. **Comparison Based**: If evaluating Temporal, check the comparisons section
3. **Pattern Based**: If looking for implementation patterns, see the patterns section
4. **Language Based**: Check language-specific folders for SDK-specific details

## 📝 Contributing

Please follow the [Documentation Guidelines](./guides/DOCUMENTATION_GUIDELINES.md) when contributing to this documentation.