import asyncio
from datetime import datetime, timedelta
from typing import List, Optional
from dataclasses import dataclass, field

from temporalio import workflow
from temporalio.common import RetryPolicy

# Activities are not imported in workflows - they are called by name


@dataclass
class ProcessingError:
    document_id: str
    stage: str
    error: str
    timestamp: datetime


@dataclass
class PipelineResult:
    workflow_id: str
    total_documents: int
    successful_documents: int
    failed_documents: int
    processing_errors: List[ProcessingError] = field(default_factory=list)
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None
    duration_seconds: Optional[float] = None


@dataclass
class DocumentResult:
    document_id: str
    success: bool
    failed_stage: Optional[str] = None
    error: Optional[str] = None
    processing_time_seconds: Optional[float] = None


@workflow.defn
class ResourceOptimizedWorkflow:
    """Resource-optimized document processing workflow"""

    @workflow.run
    async def run(self, csv_path: str, batch_size: int = 10, timeout_minutes: int = 60) -> PipelineResult:
        """Execute the resource-optimized document pipeline"""
        workflow.logger.info(
            f"Starting resource-optimized document pipeline workflow: "
            f"csv_path={csv_path}, batch_size={batch_size}, timeout={timeout_minutes}min"
        )

        # Activity options for different task queues
        io_activity_options = {
            "task_queue": "io-bound-queue",
            "start_to_close_timeout": timedelta(minutes=5),
            "retry_policy": RetryPolicy(maximum_attempts=3),
        }

        cpu_activity_options = {
            "task_queue": "cpu-bound-queue",
            "start_to_close_timeout": timedelta(minutes=10),
            "retry_policy": RetryPolicy(maximum_attempts=3),
        }

        gpu_activity_options = {
            "task_queue": "gpu-bound-queue",
            "start_to_close_timeout": timedelta(minutes=15),
            "retry_policy": RetryPolicy(maximum_attempts=2),
        }

        # Step 1: Parse CSV (IO-bound)
        documents = await workflow.execute_activity(
            "parse_csv",
            args=[csv_path],
            **io_activity_options,
        )

        workflow.logger.info(f"CSV parsed successfully, found {len(documents)} documents")

        # Initialize result
        result = PipelineResult(
            workflow_id=workflow.info().workflow_id,
            total_documents=len(documents),
            successful_documents=0,
            failed_documents=0,
            start_time=workflow.now(),
        )

        # Process documents in batches
        for i in range(0, len(documents), batch_size):
            batch = documents[i:i + batch_size]
            workflow.logger.info(
                f"Processing batch {i//batch_size + 1}, documents {i} to {i + len(batch)}"
            )

            # Process each document in the batch in parallel
            tasks = []
            for doc in batch:
                task = self._process_document_async(
                    doc,
                    io_activity_options,
                    cpu_activity_options,
                    gpu_activity_options,
                )
                tasks.append(task)

            # Wait for all documents in the batch to complete
            doc_results = await asyncio.gather(*tasks, return_exceptions=True)

            # Process results
            for idx, doc_result in enumerate(doc_results):
                if isinstance(doc_result, Exception):
                    workflow.logger.error(f"Failed to process document: {doc_result}")
                    result.failed_documents += 1
                    result.processing_errors.append(
                        ProcessingError(
                            document_id=batch[idx]['id'],
                            stage="processing",
                            error=str(doc_result),
                            timestamp=workflow.now(),
                        )
                    )
                elif doc_result.success:
                    result.successful_documents += 1
                else:
                    result.failed_documents += 1
                    result.processing_errors.append(
                        ProcessingError(
                            document_id=doc_result.document_id,
                            stage=doc_result.failed_stage,
                            error=doc_result.error,
                            timestamp=workflow.now(),
                        )
                    )

        result.end_time = workflow.now()
        result.duration_seconds = (result.end_time - result.start_time).total_seconds()

        workflow.logger.info(
            f"Resource-optimized workflow completed: "
            f"successful={result.successful_documents}, "
            f"failed={result.failed_documents}, "
            f"duration={result.duration_seconds}s"
        )

        return result

    async def _process_document_async(
        self,
        doc,
        io_activity_options,
        cpu_activity_options,
        gpu_activity_options,
    ) -> DocumentResult:
        """Process a single document asynchronously across different workers"""
        workflow.logger.info(f"Processing document: {doc['id']}")

        result = DocumentResult(document_id=doc['id'], success=True)
        start_time = workflow.now()

        try:
            # Step 1: Download document (IO-bound)
            download_result = await workflow.execute_activity(
                "download_document",
                args=[doc['id'], doc['url']],
                **io_activity_options,
            )
            workflow.logger.info(f"Download result: {type(download_result)} - {download_result}")

            # Step 2: Validate document structure (CPU-bound)
            # Handle bytes data that may be serialized as list
            data = download_result['data']
            if isinstance(data, list):
                data = bytes(data)
            text_data = data.decode('utf-8', errors='ignore')
            
            validation_result = await workflow.execute_activity(
                "validate_document_structure",
                args=[doc['id'], text_data],
                **cpu_activity_options,
            )

            if not validation_result['is_valid']:
                result.success = False
                result.failed_stage = "validation"
                result.error = f"Document validation failed: {', '.join(validation_result['issues'])}"
                return result

            # Parallel execution of CPU and GPU tasks
            cpu_tasks = []
            gpu_tasks = []

            # CPU tasks
            extract_text_task = workflow.execute_activity(
                "extract_text_from_document",
                args=[doc['id'], data],
                **cpu_activity_options,
            )
            cpu_tasks.append(extract_text_task)

            preprocess_text_task = workflow.execute_activity(
                "preprocess_text",
                args=[doc['id'], text_data],
                **cpu_activity_options,
            )
            cpu_tasks.append(preprocess_text_task)

            # GPU tasks
            ocr_task = workflow.execute_activity(
                "perform_ocr",
                args=[doc['id'], data],
                **gpu_activity_options,
            )
            gpu_tasks.append(ocr_task)

            classify_task = workflow.execute_activity(
                "classify_document",
                args=[doc['id'], text_data],
                **gpu_activity_options,
            )
            gpu_tasks.append(classify_task)

            # Wait for CPU tasks
            cpu_results = await asyncio.gather(*cpu_tasks)
            extracted_text = cpu_results[0]
            text_processing_result = cpu_results[1]

            # Wait for GPU tasks (allow failures)
            gpu_results = await asyncio.gather(*gpu_tasks, return_exceptions=True)
            
            # Generate embeddings (GPU-bound)
            if len(text_processing_result['processed_text']) > 1000:
                # Simple chunking
                chunks = [text_processing_result['processed_text'][:1000], text_processing_result['processed_text'][1000:]]
            else:
                chunks = [text_processing_result['processed_text']]

            embedding_result = await workflow.execute_activity(
                "generate_embeddings",
                args=[doc['id'], chunks],
                **gpu_activity_options,
            )

            # Store results in parallel (IO-bound)
            io_tasks = []

            # Store in S3
            upload_task = workflow.execute_activity(
                "upload_to_s3",
                args=[doc['id'], data],
                **io_activity_options,
            )
            io_tasks.append(upload_task)

            # Store embeddings in vector DB
            vector_store_task = workflow.execute_activity(
                "store_vector_embeddings",
                args=[doc['id'], embedding_result['embeddings']],
                **io_activity_options,
            )
            io_tasks.append(vector_store_task)

            # Wait for storage operations (allow failures)
            await asyncio.gather(*io_tasks, return_exceptions=True)

            result.processing_time_seconds = (workflow.now() - start_time).total_seconds()
            workflow.logger.info(f"Document processed successfully: {doc['id']}")

        except Exception as e:
            result.success = False
            result.failed_stage = "processing"
            result.error = str(e)
            workflow.logger.error(f"Error processing document {doc['id']}: {e}")

        return result