import asyncio
import logging
import os
import sys

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from temporalio.client import Client
from temporalio.worker import Worker

from activities.io import IOBoundActivities


async def main():
    """Run the IO-bound activity worker"""
    logging.basicConfig(level=logging.INFO)
    logger = logging.getLogger(__name__)

    # Create Temporal client
    temporal_host = os.environ.get("TEMPORAL_HOST", "localhost:7233")
    client = await Client.connect(temporal_host)

    # Create IO activities instance
    io_activities = IOBoundActivities()

    # Create worker for IO-bound queue with high concurrency
    worker = Worker(
        client,
        task_queue="io-bound-queue",
        activities=[
            io_activities.parse_csv,
            io_activities.download_document,
            io_activities.upload_to_s3,
            io_activities.query_metadata_database,
            io_activities.store_vector_embeddings,
            io_activities.call_external_api,
        ],
        max_concurrent_activities=200,  # High concurrency for IO operations
    )

    logger.info("Starting IO Worker on queue: io-bound-queue")
    logger.info("Optimized for high-concurrency IO operations (network, disk, database)")

    # Run the worker
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())