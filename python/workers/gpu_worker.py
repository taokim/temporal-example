import asyncio
import logging
import os
import sys

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from temporalio.client import Client
from temporalio.worker import Worker

from activities.gpu import GPUBoundActivities


async def main():
    """Run the GPU-bound activity worker"""
    logging.basicConfig(level=logging.INFO)
    logger = logging.getLogger(__name__)

    # Create Temporal client
    temporal_host = os.environ.get("TEMPORAL_HOST", "localhost:7233")
    client = await Client.connect(temporal_host)

    # Get GPU count from environment
    gpu_count = int(os.environ.get("GPU_COUNT", "2"))

    # Create GPU activities instance
    gpu_activities = GPUBoundActivities(gpu_count=gpu_count)

    # Create worker for GPU-bound queue
    worker = Worker(
        client,
        task_queue="gpu-bound-queue",
        activities=[
            gpu_activities.generate_embeddings,
            gpu_activities.classify_document,
            gpu_activities.perform_ocr,
            gpu_activities.analyze_images,
            gpu_activities.run_llm_inference,
        ],
        max_concurrent_activities=gpu_count * 4,  # 4 concurrent tasks per GPU
    )

    logger.info(f"Starting GPU Worker on queue: gpu-bound-queue with {gpu_count} GPUs")
    logger.info("Optimized for GPU-accelerated ML inference and image processing")

    # Run the worker
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())