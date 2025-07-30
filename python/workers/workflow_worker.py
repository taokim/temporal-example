import asyncio
import logging
import os
import sys

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from temporalio.client import Client
from temporalio.worker import Worker

from workflows import ResourceOptimizedWorkflow


async def main():
    """Run the workflow worker"""
    logging.basicConfig(level=logging.INFO)
    logger = logging.getLogger(__name__)

    # Create Temporal client
    temporal_host = os.environ.get("TEMPORAL_HOST", "localhost:7233")
    client = await Client.connect(temporal_host)

    # Create worker for workflow queue only
    worker = Worker(
        client,
        task_queue="document-pipeline-queue",
        workflows=[ResourceOptimizedWorkflow],
        # No activities - this worker only handles workflows
    )

    logger.info("Starting Workflow Worker on queue: document-pipeline-queue")
    logger.info("This worker only handles workflow orchestration")

    # Run the worker
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())