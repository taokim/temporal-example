#!/bin/bash

# Check if JAR exists
if ! ls target/temporal-document-pipeline-*.jar 1> /dev/null 2>&1; then
    echo "JAR file not found. Running build first..."
    ./scripts/build.sh
fi

# Set environment variables for local development
export SPRING_PROFILES_ACTIVE=local

echo "Starting Document Pipeline Worker..."
java -jar target/temporal-document-pipeline-*.jar