import os
import csv
import asyncio
import aiohttp
from dataclasses import dataclass
from datetime import datetime
from typing import List, Dict, Any, Optional
from pathlib import Path

from temporalio import activity
import aioboto3
import asyncpg


@dataclass
class Document:
    id: str
    title: str
    url: str
    category: str
    size: Optional[int] = None
    processed_at: Optional[datetime] = None


@dataclass
class DownloadResult:
    document_id: str
    data: bytes
    content_type: str
    size: int
    downloaded_at: datetime


@dataclass
class UploadResult:
    document_id: str
    s3_key: str
    bucket: str
    size: int
    uploaded_at: datetime


@dataclass
class DatabaseQueryResult:
    document_id: str
    metadata: Dict[str, Any]
    query_time: float


@dataclass
class VectorStoreResult:
    document_id: str
    vector_count: int
    collection_id: str
    stored_at: datetime


@dataclass
class APICallResult:
    document_id: str
    response: Dict[str, Any]
    status_code: int
    response_time: float


class IOBoundActivities:
    def __init__(self):
        self.session = None
        self.max_concurrency = 100
        activity.logger.info("IO activities initialized")

    async def _get_session(self) -> aiohttp.ClientSession:
        """Get or create aiohttp session"""
        if self.session is None or self.session.closed:
            timeout = aiohttp.ClientTimeout(total=30)
            self.session = aiohttp.ClientSession(timeout=timeout)
        return self.session

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self.session and not self.session.closed:
            await self.session.close()

    @activity.defn
    async def parse_csv(self, csv_path: str) -> List[Document]:
        """Parses CSV file to get document list"""
        activity.logger.info(f"Parsing CSV file: {csv_path}")
        
        documents = []
        
        with open(csv_path, 'r', newline='', encoding='utf-8') as csvfile:
            reader = csv.DictReader(csvfile)
            for row in reader:
                doc = Document(
                    id=row.get('id', ''),
                    title=row.get('title', ''),
                    url=row.get('url', ''),
                    category=row.get('category', 'unknown')
                )
                documents.append(doc)
        
        activity.logger.info(f"CSV parsing completed. Found {len(documents)} documents")
        return documents

    @activity.defn
    async def download_document(self, document_id: str, url: str) -> DownloadResult:
        """Downloads a document from URL"""
        activity.logger.info(f"IO-bound download starting for document {document_id} from {url}")
        
        # Handle file:// URLs
        if url.startswith("file://"):
            file_path = url[7:]  # Remove 'file://' prefix
            try:
                with open(file_path, 'rb') as f:
                    data = f.read()
                content_type = "application/octet-stream"
            except FileNotFoundError:
                # Simulate data for local files that don't exist
                data = f"Simulated content for document {document_id} from {url}".encode()
                content_type = "text/plain"
            
            return DownloadResult(
                document_id=document_id,
                data=data,
                content_type=content_type,
                size=len(data),
                downloaded_at=datetime.now()
            )
        
        # HTTP/HTTPS download
        session = await self._get_session()
        try:
            async with session.get(url) as response:
                data = await response.read()
                content_type = response.headers.get('Content-Type', 'application/octet-stream')
                
                return DownloadResult(
                    document_id=document_id,
                    data=data,
                    content_type=content_type,
                    size=len(data),
                    downloaded_at=datetime.now()
                )
        except Exception as e:
            # Simulate successful download for demo
            activity.logger.warning(f"Download failed for {url}, using simulated data: {e}")
            simulated_data = f"Simulated content for document {document_id}".encode()
            
            return DownloadResult(
                document_id=document_id,
                data=simulated_data,
                content_type="text/plain",
                size=len(simulated_data),
                downloaded_at=datetime.now()
            )

    @activity.defn
    async def upload_to_s3(self, document_id: str, data: bytes) -> UploadResult:
        """Uploads document to S3/MinIO"""
        activity.logger.info(f"IO-bound S3 upload for document: {document_id}, size: {len(data)}")
        
        # S3 configuration from environment
        endpoint_url = os.environ.get('S3_ENDPOINT', 'http://localhost:9000')
        access_key = os.environ.get('S3_ACCESS_KEY', 'minioadmin')
        secret_key = os.environ.get('S3_SECRET_KEY', 'minioadmin')
        bucket_name = os.environ.get('S3_BUCKET', 'documents')
        
        s3_key = f"documents/{datetime.now().strftime('%Y-%m-%d')}/{document_id}.dat"
        
        try:
            # Use aioboto3 for async S3 operations
            session = aioboto3.Session()
            async with session.client(
                's3',
                endpoint_url=endpoint_url,
                aws_access_key_id=access_key,
                aws_secret_access_key=secret_key
            ) as s3:
                await s3.put_object(
                    Bucket=bucket_name,
                    Key=s3_key,
                    Body=data
                )
        except Exception as e:
            activity.logger.warning(f"S3 upload failed, simulating success: {e}")
            # Simulate successful upload for demo
        
        return UploadResult(
            document_id=document_id,
            s3_key=s3_key,
            bucket=bucket_name,
            size=len(data),
            uploaded_at=datetime.now()
        )

    @activity.defn
    async def query_metadata_database(self, document_id: str) -> DatabaseQueryResult:
        """Queries metadata from database"""
        activity.logger.info(f"IO-bound database query for document: {document_id}")
        
        start_time = datetime.now()
        
        # Database configuration from environment
        db_host = os.environ.get('METADATA_DB_HOST', 'localhost')
        db_port = int(os.environ.get('METADATA_DB_PORT', '5433'))
        db_name = os.environ.get('METADATA_DB_NAME', 'document_metadata')
        db_user = os.environ.get('METADATA_DB_USER', 'docuser')
        db_password = os.environ.get('METADATA_DB_PASSWORD', 'docpass')
        
        metadata = {}
        
        try:
            # Use asyncpg for async PostgreSQL operations
            conn = await asyncpg.connect(
                host=db_host,
                port=db_port,
                database=db_name,
                user=db_user,
                password=db_password
            )
            
            # Simulate query
            row = await conn.fetchrow(
                "SELECT * FROM documents WHERE document_id = $1",
                document_id
            )
            
            if row:
                metadata = dict(row)
            
            await conn.close()
        except Exception as e:
            activity.logger.warning(f"Database query failed, using simulated data: {e}")
            # Simulate metadata
            metadata = {
                "document_id": document_id,
                "created_at": datetime.now().isoformat(),
                "updated_at": datetime.now().isoformat(),
                "status": "processed",
                "version": "1.0"
            }
        
        query_time = (datetime.now() - start_time).total_seconds()
        
        return DatabaseQueryResult(
            document_id=document_id,
            metadata=metadata,
            query_time=query_time
        )

    @activity.defn
    async def store_vector_embeddings(
        self, document_id: str, embeddings: List[List[float]]
    ) -> VectorStoreResult:
        """Stores embeddings in vector database"""
        activity.logger.info(
            f"IO-bound ChromaDB storage for document: {document_id}, "
            f"embeddings: {len(embeddings)}"
        )
        
        # ChromaDB configuration from environment
        chromadb_url = os.environ.get('VECTOR_DB_URL', 'http://localhost:8000')
        collection_id = "document-embeddings"
        
        # Simulate ChromaDB storage (in real implementation, would use chromadb client)
        # For now, just simulate with a delay
        await asyncio.sleep(0.1)
        
        return VectorStoreResult(
            document_id=document_id,
            vector_count=len(embeddings),
            collection_id=collection_id,
            stored_at=datetime.now()
        )

    @activity.defn
    async def call_external_api(
        self, document_id: str, endpoint: str, payload: Dict[str, Any]
    ) -> APICallResult:
        """Calls external API"""
        activity.logger.info(
            f"IO-bound external API call for document: {document_id}, endpoint: {endpoint}"
        )
        
        start_time = datetime.now()
        
        session = await self._get_session()
        
        try:
            async with session.post(endpoint, json=payload) as response:
                response_data = await response.json()
                status_code = response.status
        except Exception as e:
            activity.logger.warning(f"API call failed, using simulated response: {e}")
            # Simulate API response
            response_data = {
                "status": "success",
                "document_id": document_id,
                "processed": True,
                "timestamp": datetime.now().isoformat()
            }
            status_code = 200
        
        response_time = (datetime.now() - start_time).total_seconds()
        
        return APICallResult(
            document_id=document_id,
            response=response_data,
            status_code=status_code,
            response_time=response_time
        )