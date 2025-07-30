import asyncio
import argparse
import logging
import os
from datetime import timedelta

from temporalio.client import Client

from workflows import ResourceOptimizedWorkflow


async def main():
    """Start the resource-optimized workflow"""
    parser = argparse.ArgumentParser(description="Start resource-optimized document pipeline workflow")
    parser.add_argument(
        "--csv-file",
        required=True,
        help="Path to CSV file containing documents"
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=10,
        help="Number of documents to process in parallel (default: 10)"
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=60,
        help="Workflow timeout in minutes (default: 60)"
    )
    parser.add_argument(
        "--workflow-id",
        default="resource-optimized-pipeline",
        help="Workflow ID (default: resource-optimized-pipeline)"
    )

    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO)
    logger = logging.getLogger(__name__)

    # Create Temporal client
    temporal_host = os.environ.get("TEMPORAL_HOST", "localhost:7233")
    client = await Client.connect(temporal_host)

    # Start workflow execution
    logger.info(f"Starting resource-optimized workflow: {args.workflow_id}")
    logger.info(f"Parameters: csv={args.csv_file}, batch_size={args.batch_size}, timeout={args.timeout} minutes")
    logger.info("Workflow will distribute activities across specialized workers:")
    logger.info("  - CPU Worker: Text processing, validation, extraction")
    logger.info("  - GPU Worker: ML inference, embeddings, OCR")
    logger.info("  - IO Worker: Downloads, uploads, database operations")

    handle = await client.start_workflow(
        ResourceOptimizedWorkflow.run,
        args.csv_file,
        args.batch_size,
        args.timeout,
        id=args.workflow_id,
        task_queue="document-pipeline-queue",
        execution_timeout=timedelta(minutes=args.timeout),
    )

    logger.info(f"Started workflow with ID: {handle.id}")
    logger.info(f"Run ID: {handle.result_run_id}")

    # Wait for workflow completion
    result = await handle.result()

    # Print results
    print("\nWorkflow completed successfully!")
    print(f"Total documents: {result.total_documents}")
    print(f"Successful: {result.successful_documents}")
    print(f"Failed: {result.failed_documents}")
    print(f"Duration: {result.duration}")

    if result.processing_errors:
        print("\nProcessing errors:")
        for error in result.processing_errors:
            print(f"  - Document {error.document_id} failed at {error.stage}: {error.error}")


if __name__ == "__main__":
    asyncio.run(main())