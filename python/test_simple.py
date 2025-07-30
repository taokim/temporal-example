#!/usr/bin/env python3
"""Simple test to debug activity execution"""

import asyncio
import logging
import os
import sys

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from temporalio.client import Client
from datetime import timedelta
from temporalio.common import RetryPolicy


async def main():
    """Run simple test"""
    logging.basicConfig(level=logging.INFO)
    logger = logging.getLogger(__name__)

    # Create Temporal client
    temporal_host = os.environ.get("TEMPORAL_HOST", "localhost:7233")
    client = await Client.connect(temporal_host)

    logger.info("Testing download activity directly...")

    # Test download activity
    try:
        result = await client.execute_workflow(
            "test_download_workflow",
            args=["doc1", "https://example.com/test.pdf"],
            id="test-download-1",
            task_queue="io-bound-queue",
            execution_timeout=timedelta(minutes=1),
            run_timeout=timedelta(minutes=1),
        )
        logger.info(f"Download result: {result}")
    except Exception as e:
        logger.error(f"Download test failed: {e}")


@workflow.defn
class TestDownloadWorkflow:
    @workflow.run
    async def run(self, doc_id: str, url: str):
        # Test direct activity call
        result = await workflow.execute_activity(
            "download_document",
            args=[doc_id, url],
            task_queue="io-bound-queue",
            start_to_close_timeout=timedelta(seconds=30),
        )
        return result


if __name__ == "__main__":
    asyncio.run(main())