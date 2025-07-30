.PHONY: all install test lint format clean run-workflow-worker run-cpu-worker run-gpu-worker run-io-worker run-optimized help

# Variables
PYTHON := python3
PIP := $(PYTHON) -m pip
WORKERS_DIR := workers
VENV := venv

# Default target
all: install lint test

# Create virtual environment
$(VENV):
	$(PYTHON) -m venv $(VENV)

# Install dependencies
install: $(VENV)
	$(VENV)/bin/pip install --upgrade pip
	$(VENV)/bin/pip install -r requirements.txt

# Run linting
lint: $(VENV)
	$(VENV)/bin/ruff check . --fix
	$(VENV)/bin/black --check .

# Format code
format: $(VENV)
	$(VENV)/bin/black .
	$(VENV)/bin/ruff check . --fix

# Run tests
test: $(VENV)
	$(VENV)/bin/pytest tests/ -v

# Clean build artifacts
clean:
	rm -rf $(VENV)
	rm -rf __pycache__
	rm -rf .pytest_cache
	rm -rf .ruff_cache
	find . -type d -name "__pycache__" -exec rm -rf {} +
	find . -type f -name "*.pyc" -delete

# Run resource-optimized workers
run-workflow-worker: $(VENV)
	@echo "Starting Workflow Worker..."
	$(VENV)/bin/python $(WORKERS_DIR)/workflow_worker.py

run-cpu-worker: $(VENV)
	@echo "Starting CPU Worker..."
	$(VENV)/bin/python $(WORKERS_DIR)/cpu_worker.py

run-gpu-worker: $(VENV)
	@echo "Starting GPU Worker..."
	GPU_COUNT=2 $(VENV)/bin/python $(WORKERS_DIR)/gpu_worker.py

run-io-worker: $(VENV)
	@echo "Starting IO Worker..."
	$(VENV)/bin/python $(WORKERS_DIR)/io_worker.py

# Run resource-optimized starter
run-optimized: $(VENV)
	@echo "Starting resource-optimized workflow..."
	$(VENV)/bin/python start_resource_optimized.py --csv-file ../testdata/documents.csv

# Run with custom parameters
run-optimized-custom: $(VENV)
	@echo "Starting resource-optimized workflow with custom parameters..."
	$(VENV)/bin/python start_resource_optimized.py \
		--csv-file ../testdata/documents.csv \
		--batch-size 5 \
		--timeout 30 \
		--workflow-id custom-pipeline

# Development helpers
dev-install: install
	$(VENV)/bin/pip install -r requirements-dev.txt

# Help
help:
	@echo "Available targets:"
	@echo "  make install              - Install dependencies"
	@echo "  make lint                 - Run linting"
	@echo "  make format               - Format code"
	@echo "  make test                 - Run tests"
	@echo "  make clean                - Clean build artifacts"
	@echo "  make run-workflow-worker  - Run workflow worker"
	@echo "  make run-cpu-worker       - Run CPU worker"
	@echo "  make run-gpu-worker       - Run GPU worker"
	@echo "  make run-io-worker        - Run IO worker"
	@echo "  make run-optimized        - Start resource-optimized workflow"
	@echo "  make run-optimized-custom - Start workflow with custom parameters"

# Environment setup helpers
setup-env:
	@echo "Setting up environment variables..."
	@echo "export TEMPORAL_HOST=localhost:7233"
	@echo "export S3_ENDPOINT=http://localhost:9000"
	@echo "export S3_ACCESS_KEY=minioadmin"
	@echo "export S3_SECRET_KEY=minioadmin"
	@echo "export S3_BUCKET=documents"
	@echo "export VECTOR_DB_URL=http://localhost:8000"
	@echo "export METADATA_DB_HOST=localhost"
	@echo "export METADATA_DB_PORT=5433"
	@echo "export METADATA_DB_NAME=document_metadata"
	@echo "export METADATA_DB_USER=docuser"
	@echo "export METADATA_DB_PASSWORD=docpass"
	@echo "export GPU_COUNT=2"