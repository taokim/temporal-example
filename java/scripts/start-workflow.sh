#!/bin/bash

if [ $# -lt 1 ]; then
    echo "Usage: $0 <csv-path> [batch-size] [max-size-mb] [wait]"
    echo "Example: $0 ../data/documents.csv 10 50 wait"
    exit 1
fi

# Check if JAR exists
if ! ls target/temporal-document-pipeline-*.jar 1> /dev/null 2>&1; then
    echo "JAR file not found. Running build first..."
    ./scripts/build.sh
fi

echo "Starting workflow with CSV: $1"
java -cp target/temporal-document-pipeline-*.jar com.example.DocumentPipelineStarter "$@"