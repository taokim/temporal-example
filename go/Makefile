.PHONY: all build test run-worker run-starter docker-up docker-down clean deps fmt lint test-integration

# Variables
PROJECT_NAME=temporal-rag-pipeline
DOCKER_COMPOSE=docker-compose -f ../docker-compose.yml
GO_FILES=$(shell find . -name '*.go' -not -path "./vendor/*")

# Default target
all: deps fmt lint test build

# Build binaries
build:
	@echo "Building original worker..."
	go build -o bin/worker ./cmd/worker
	@echo "Building starter..."
	go build -o bin/starter ./cmd/starter
	@echo "Building resource-optimized components..."
	go build -o bin/workflow-worker ./cmd/workflow-worker
	go build -o bin/cpu-worker ./cmd/cpu-worker
	go build -o bin/gpu-worker ./cmd/gpu-worker
	go build -o bin/io-worker ./cmd/io-worker
	go build -o bin/resource-optimized-starter ./cmd/resource-optimized-starter
	@echo "Build complete!"

# Run tests
test:
	@echo "Running unit tests..."
	go test -v -race -short ./...

# Run integration tests
test-integration: docker-up
	@echo "Waiting for services to be ready..."
	@sleep 10
	@echo "Running integration tests..."
	go test -v -race -tags=integration ./...
	@make docker-down

# Run worker
run-worker: build
	@echo "Starting worker..."
	./bin/worker

# Run workflow starter
run-starter: build
	@echo "Starting workflow..."
	./bin/starter --csv-file ../testdata/documents.csv

# Run resource-optimized workers
run-workflow-worker: build
	@echo "Starting Workflow Worker..."
	./bin/workflow-worker

run-cpu-worker: build
	@echo "Starting CPU Worker..."
	./bin/cpu-worker

run-gpu-worker: build
	@echo "Starting GPU Worker..."
	GPU_COUNT=2 ./bin/gpu-worker

run-io-worker: build
	@echo "Starting IO Worker..."
	./bin/io-worker

# Run resource-optimized starter
run-optimized-starter: build
	@echo "Starting resource-optimized workflow..."
	./bin/resource-optimized-starter --csv-file ../testdata/documents.csv

# Docker commands
docker-up:
	@echo "Starting docker services..."
	$(DOCKER_COMPOSE) up -d
	@echo "Waiting for services to be healthy..."
	@sleep 5

docker-down:
	@echo "Stopping docker services..."
	$(DOCKER_COMPOSE) down

docker-logs:
	$(DOCKER_COMPOSE) logs -f

# Development commands
dev: docker-up
	@echo "Starting development mode..."
	@which air > /dev/null || go install github.com/cosmtrek/air@latest
	air -c .air.toml

# Install dependencies
deps:
	@echo "Installing dependencies..."
	go mod download
	go mod tidy

# Format code
fmt:
	@echo "Formatting code..."
	go fmt ./...
	@which gofumpt > /dev/null || go install mvdan.cc/gofumpt@latest
	gofumpt -w $(GO_FILES)

# Lint code
lint:
	@echo "Linting code..."
	@which golangci-lint > /dev/null || curl -sSfL https://raw.githubusercontent.com/golangci/golangci-lint/master/install.sh | sh -s -- -b $(go env GOPATH)/bin
	golangci-lint run ./...

# Generate mocks
mocks:
	@echo "Generating mocks..."
	@which mockgen > /dev/null || go install github.com/golang/mock/mockgen@latest
	go generate ./...

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	rm -rf bin/
	rm -rf tmp/
	go clean -cache

# Run worker with local config
run-local-worker: docker-up build
	@echo "Running worker with local configuration..."
	TEMPORAL_HOST_URL=localhost:7233 \
	S3_ENDPOINT=http://localhost:9000 \
	S3_ACCESS_KEY=minioadmin \
	S3_SECRET_KEY=minioadmin \
	S3_BUCKET=documents \
	VECTOR_DB_URL=http://localhost:8000 \
	METADATA_DB_HOST=localhost \
	METADATA_DB_PORT=5433 \
	METADATA_DB_NAME=document_metadata \
	METADATA_DB_USER=docuser \
	METADATA_DB_PASSWORD=docpass \
	REDIS_URL=localhost:6379 \
	LLM_SERVICE_URL=http://localhost:8081 \
	USE_MOCK_SERVICES=true \
	./bin/worker

# Run starter with local config
run-local-starter: build
	@echo "Running starter with local configuration..."
	TEMPORAL_HOST_URL=localhost:7233 \
	./bin/starter \
		--csv-file ../testdata/documents.csv \
		--s3-bucket documents \
		--vector-db-collection documents \
		--embedding-model text-embedding-3-small \
		--summary-model gpt-3.5-turbo

# Create S3 bucket in MinIO
setup-minio:
	@echo "Setting up MinIO bucket..."
	@which mc > /dev/null || (echo "Installing MinIO client..." && brew install minio/stable/mc)
	mc alias set localminio http://localhost:9000 minioadmin minioadmin
	mc mb localminio/documents --ignore-existing
	mc anonymous set download localminio/documents
	@echo "MinIO setup complete!"

# Initialize database
setup-db:
	@echo "Setting up PostgreSQL database..."
	PGPASSWORD=docpass psql -h localhost -p 5433 -U docuser -d document_metadata -f ../init-scripts/schema.sql
	@echo "Database setup complete!"

# Full local setup
setup: docker-up setup-minio setup-db
	@echo "Local environment setup complete!"
	@echo "Temporal UI: http://localhost:8080"
	@echo "MinIO Console: http://localhost:9001 (minioadmin/minioadmin)"
	@echo "ChromaDB: http://localhost:8000"

# Run complete demo
demo: setup build
	@echo "Running demo..."
	@make run-local-worker &
	@sleep 5
	@make run-local-starter
	@echo "Check Temporal UI at http://localhost:8080 to see the workflow execution"

# Help
help:
	@echo "Available targets:"
	@echo "  make build          - Build worker and starter binaries"
	@echo "  make test           - Run unit tests"
	@echo "  make test-integration - Run integration tests"
	@echo "  make run-worker     - Run the worker"
	@echo "  make run-starter    - Run the workflow starter"
	@echo "  make docker-up      - Start Docker services"
	@echo "  make docker-down    - Stop Docker services"
	@echo "  make setup          - Setup local environment"
	@echo "  make demo           - Run complete demo"
	@echo "  make clean          - Clean build artifacts"