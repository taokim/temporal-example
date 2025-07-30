#!/usr/bin/env python3
"""Test client for the resource-optimized workflow"""

import asyncio
import logging
import os
import sys
from datetime import datetime

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from temporalio.client import Client
from workflows.resource_optimized import ResourceOptimizedWorkflow


async def main():
    """Run test workflow"""
    logging.basicConfig(level=logging.INFO)
    logger = logging.getLogger(__name__)

    # Create Temporal client
    temporal_host = os.environ.get("TEMPORAL_HOST", "localhost:7233")
    client = await Client.connect(temporal_host)

    # Generate workflow ID
    workflow_id = f"resource-optimized-test-{datetime.now().strftime('%Y%m%d-%H%M%S')}"

    # Sample CSV path (you would need to create this)
    csv_path = "/tmp/test_documents.csv"
    
    # Create test CSV file
    with open(csv_path, "w") as f:
        f.write("id,url\n")
        f.write("doc1,https://example.com/document1.pdf\n")
        f.write("doc2,https://example.com/document2.pdf\n")
        f.write("doc3,https://example.com/document3.pdf\n")

    logger.info(f"Starting resource-optimized workflow: {workflow_id}")
    logger.info(f"CSV Path: {csv_path}")

    try:
        # Start the workflow
        handle = await client.start_workflow(
            ResourceOptimizedWorkflow.run,
            args=[csv_path, 10, 60],  # csv_path, batch_size, timeout_minutes
            id=workflow_id,
            task_queue="document-pipeline-queue",
        )

        logger.info(f"Workflow started with ID: {workflow_id}")
        logger.info("Waiting for workflow to complete...")

        # Wait for result
        result = await handle.result()

        logger.info("\n=== Workflow Result ===")
        logger.info(f"Workflow ID: {result.workflow_id}")
        logger.info(f"Total Documents: {result.total_documents}")
        logger.info(f"Successful: {result.successful_documents}")
        logger.info(f"Failed: {result.failed_documents}")
        logger.info(f"Duration: {result.duration_seconds}s")

        if result.processing_errors:
            logger.info("\n=== Processing Errors ===")
            for error in result.processing_errors:
                logger.error(f"Document {error.document_id} failed at {error.stage}: {error.error}")

    except Exception as e:
        logger.error(f"Workflow failed: {e}")
        raise


if __name__ == "__main__":
    asyncio.run(main())