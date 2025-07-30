import asyncio
import logging
import os
import multiprocessing
import sys

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from temporalio.client import Client
from temporalio.worker import Worker

from activities.cpu import CPUBoundActivities


async def main():
    """Run the CPU-bound activity worker"""
    logging.basicConfig(level=logging.INFO)
    logger = logging.getLogger(__name__)

    # Create Temporal client
    temporal_host = os.environ.get("TEMPORAL_HOST", "localhost:7233")
    client = await Client.connect(temporal_host)

    # Create CPU activities instance
    cpu_activities = CPUBoundActivities()

    # Create worker for CPU-bound queue
    worker = Worker(
        client,
        task_queue="cpu-bound-queue",
        activities=[
            cpu_activities.preprocess_text,
            cpu_activities.validate_document_structure,
            cpu_activities.extract_text_from_document,
            cpu_activities.tokenize_text,
            cpu_activities.compress_document,
        ],
        max_concurrent_activities=multiprocessing.cpu_count() * 2,
        activity_executor=None,  # Uses default executor
    )

    logger.info(f"Starting CPU Worker on queue: cpu-bound-queue with {multiprocessing.cpu_count()} cores")
    logger.info("Optimized for CPU-intensive text processing and validation")

    # Run the worker
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())