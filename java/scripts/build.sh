#!/bin/bash

echo "Building Java Document Pipeline..."

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Maven is not installed. Please install Maven first."
    exit 1
fi

# Clean and build
echo "Running Maven build..."
mvn clean package

if [ $? -eq 0 ]; then
    echo "Build successful!"
    echo "JAR file created at: target/temporal-document-pipeline-*.jar"
else
    echo "Build failed!"
    exit 1
fi