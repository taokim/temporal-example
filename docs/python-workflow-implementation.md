# Python Workflow Implementation for Document Processing Pipeline

## Overview

This document provides a complete Python implementation of the document processing pipeline using Temporal's Python SDK. The implementation demonstrates best practices, error handling, and integration patterns for building production-ready workflows.

## Prerequisites

```bash
# Install Temporal Python SDK
pip install temporalio
pip install aiofiles aiohttp pandas numpy
pip install openai chromadb boto3 psycopg2-binary
pip install pdfplumber python-docx pillow pytesseract
```

## Project Structure

```
python/
├── activities/
│   ├── __init__.py
│   ├── ingestion.py
│   ├── preprocessing.py
│   ├── inference.py
│   ├── postprocessing.py
│   └── storage.py
├── workflows/
│   ├── __init__.py
│   └── document_pipeline.py
├── models/
│   ├── __init__.py
│   └── data_models.py
├── utils/
│   ├── __init__.py
│   └── document_parser.py
├── worker.py
├── starter.py
└── requirements.txt
```

## Data Models

```python
# models/data_models.py
from dataclasses import dataclass, field
from typing import List, Dict, Optional
from datetime import datetime
from enum import Enum

class DocumentType(Enum):
    PDF = "pdf"
    DOCX = "docx"
    TXT = "txt"
    PPTX = "pptx"
    XLSX = "xlsx"

@dataclass
class Document:
    id: str
    name: str
    url: str
    type: DocumentType
    metadata: Dict[str, str] = field(default_factory=dict)
    metadata_raw: str = ""

@dataclass
class IngestedDocument:
    document: Document
    content: bytes
    content_type: str
    size: int
    downloaded_at: datetime = field(default_factory=datetime.now)

@dataclass
class TextChunk:
    id: str
    document_id: str
    content: str
    start_index: int
    end_index: int
    metadata: Dict[str, str] = field(default_factory=dict)

@dataclass
class PreprocessedDocument:
    document: Document
    chunks: List[TextChunk]
    total_chunks: int
    language: str
    pii_removed: bool
    quality_score: float
    preprocessed_at: datetime = field(default_factory=datetime.now)

@dataclass
class ChunkEmbedding:
    chunk_id: str
    embedding: List[float]
    model: str
    dimensions: int

@dataclass
class InferenceResults:
    embeddings: List[ChunkEmbedding]
    summary: str
    entities: List[Dict[str, str]]
    sentiment: Dict[str, float]
    model_info: Dict[str, str]

@dataclass
class ProcessingError:
    document_id: str
    stage: str
    error: str
    timestamp: datetime = field(default_factory=datetime.now)
    retryable: bool = True

@dataclass
class PipelineInput:
    csv_path: str
    batch_size: int = 10
    max_size_mb: int = 50
    preprocessing_options: Dict[str, any] = field(default_factory=dict)
    model_config: Dict[str, any] = field(default_factory=dict)
    chroma_collection: str = "documents"
    s3_bucket: str = "documents"

@dataclass
class PipelineResult:
    total_processed: int
    success_count: int
    error_count: int
    avg_quality_score: float
    processing_time_seconds: float
    metadata_id: str
    vector_storage_ids: List[str]
    s3_object_keys: List[str]
    errors: List[ProcessingError]
```

## Activity Implementations

### Ingestion Activities

```python
# activities/ingestion.py
import csv
import aiohttp
import aiofiles
from typing import List
from temporalio import activity
from models.data_models import Document, IngestedDocument, DocumentType, ProcessingError
import os
from urllib.parse import urlparse

class IngestionActivities:
    def __init__(self, temp_dir: str = "/tmp/documents"):
        self.temp_dir = temp_dir
        self.max_file_size = 100 * 1024 * 1024  # 100MB
        self.allowed_types = {DocumentType.PDF, DocumentType.DOCX, DocumentType.TXT, 
                             DocumentType.PPTX, DocumentType.XLSX}
        os.makedirs(self.temp_dir, exist_ok=True)

    @activity.defn
    async def parse_csv_activity(self, csv_path: str) -> List[Document]:
        """Parse CSV file and return list of documents"""
        activity.logger.info(f"Parsing CSV file: {csv_path}")
        documents = []
        
        async with aiofiles.open(csv_path, mode='r') as file:
            content = await file.read()
            reader = csv.DictReader(content.splitlines())
            
            for idx, row in enumerate(reader):
                # Report heartbeat every 100 documents
                if idx % 100 == 0:
                    activity.heartbeat(f"Processed {idx} documents")
                
                try:
                    doc_type = DocumentType(row['type'])
                    if doc_type not in self.allowed_types:
                        activity.logger.warning(f"Unsupported type: {row['type']}")
                        continue
                    
                    doc = Document(
                        id=f"doc-{idx}-{row['name'].replace(' ', '-')}",
                        name=row['name'],
                        url=row['url'],
                        type=doc_type,
                        metadata_raw=row.get('metadata', '{}')
                    )
                    
                    # Parse metadata JSON
                    if doc.metadata_raw:
                        import json
                        try:
                            doc.metadata = json.loads(doc.metadata_raw)
                        except json.JSONDecodeError:
                            doc.metadata = {"raw": doc.metadata_raw}
                    
                    documents.append(doc)
                    
                except Exception as e:
                    activity.logger.error(f"Failed to parse document at line {idx}: {e}")
        
        activity.logger.info(f"CSV parsing completed. Found {len(documents)} documents")
        return documents

    @activity.defn
    async def download_and_validate_batch(self, documents: List[Document]) -> tuple[List[IngestedDocument], List[ProcessingError]]:
        """Download and validate a batch of documents"""
        activity.logger.info(f"Starting document download batch: {len(documents)} documents")
        
        ingested = []
        errors = []
        
        async with aiohttp.ClientSession() as session:
            for idx, doc in enumerate(documents):
                # Report heartbeat
                if idx % 5 == 0:
                    activity.heartbeat(f"Downloading document {idx + 1}/{len(documents)}")
                
                try:
                    content = await self._download_document(session, doc)
                    
                    # Validate size
                    if len(content) > self.max_file_size:
                        raise ValueError(f"File size {len(content)} exceeds maximum {self.max_file_size}")
                    
                    ingested_doc = IngestedDocument(
                        document=doc,
                        content=content,
                        content_type=self._get_content_type(doc.type),
                        size=len(content)
                    )
                    ingested.append(ingested_doc)
                    
                except Exception as e:
                    activity.logger.error(f"Failed to download document {doc.id}: {e}")
                    errors.append(ProcessingError(
                        document_id=doc.id,
                        stage="ingestion",
                        error=str(e),
                        retryable=not isinstance(e, ValueError)
                    ))
        
        activity.logger.info(f"Download batch completed. Success: {len(ingested)}, Errors: {len(errors)}")
        return ingested, errors

    async def _download_document(self, session: aiohttp.ClientSession, doc: Document) -> bytes:
        """Download document from URL or file system"""
        if doc.url.startswith("file://"):
            # Handle local files
            path = doc.url[7:]
            async with aiofiles.open(path, 'rb') as file:
                return await file.read()
        else:
            # Handle HTTP downloads
            async with session.get(doc.url) as response:
                response.raise_for_status()
                return await response.read()

    def _get_content_type(self, doc_type: DocumentType) -> str:
        """Get MIME type for document type"""
        type_map = {
            DocumentType.PDF: "application/pdf",
            DocumentType.DOCX: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            DocumentType.TXT: "text/plain",
            DocumentType.PPTX: "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            DocumentType.XLSX: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }
        return type_map.get(doc_type, "application/octet-stream")
```

### Preprocessing Activities

```python
# activities/preprocessing.py
import re
from typing import List, Dict
from temporalio import activity
from models.data_models import IngestedDocument, PreprocessedDocument, TextChunk
import pdfplumber
from docx import Document as DocxDocument
import pandas as pd
from utils.document_parser import DocumentParser

class PreprocessingActivities:
    def __init__(self):
        self.chunk_size = 1000
        self.chunk_overlap = 200
        self.min_quality_score = 0.7
        self.pii_patterns = {
            'ssn': r'\b\d{3}-\d{2}-\d{4}\b',
            'credit_card': r'\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b',
            'email': r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b',
            'phone': r'\b\d{3}[-.]?\d{3}[-.]?\d{4}\b'
        }

    @activity.defn
    async def preprocess_batch(self, ingested_docs: List[IngestedDocument]) -> tuple[List[PreprocessedDocument], List[ProcessingError]]:
        """Preprocess a batch of documents"""
        activity.logger.info(f"Starting preprocessing batch: {len(ingested_docs)} documents")
        
        preprocessed = []
        errors = []
        
        for idx, ingested_doc in enumerate(ingested_docs):
            # Report heartbeat
            if idx % 3 == 0:
                activity.heartbeat(f"Preprocessing document {idx + 1}/{len(ingested_docs)}")
            
            try:
                # Extract text based on document type
                text = await self._extract_text(ingested_doc)
                
                # Create text chunks
                chunks = self._create_chunks(text, ingested_doc.document.id)
                
                # Remove PII if configured
                if self.should_remove_pii:
                    chunks = self._remove_pii_from_chunks(chunks)
                
                # Calculate quality score
                quality_score = self._calculate_quality_score(text, chunks)
                
                # Detect language
                language = self._detect_language(text)
                
                preprocessed_doc = PreprocessedDocument(
                    document=ingested_doc.document,
                    chunks=chunks,
                    total_chunks=len(chunks),
                    language=language,
                    pii_removed=self.should_remove_pii,
                    quality_score=quality_score
                )
                
                if quality_score >= self.min_quality_score:
                    preprocessed.append(preprocessed_doc)
                else:
                    activity.logger.warning(f"Document {ingested_doc.document.id} below quality threshold: {quality_score}")
                    
            except Exception as e:
                activity.logger.error(f"Failed to preprocess document {ingested_doc.document.id}: {e}")
                errors.append(ProcessingError(
                    document_id=ingested_doc.document.id,
                    stage="preprocessing",
                    error=str(e)
                ))
        
        activity.logger.info(f"Preprocessing batch completed. Success: {len(preprocessed)}, Errors: {len(errors)}")
        return preprocessed, errors

    async def _extract_text(self, ingested_doc: IngestedDocument) -> str:
        """Extract text from document based on type"""
        doc_type = ingested_doc.document.type
        
        if doc_type == DocumentType.PDF:
            return self._extract_pdf_text(ingested_doc.content)
        elif doc_type == DocumentType.DOCX:
            return self._extract_docx_text(ingested_doc.content)
        elif doc_type == DocumentType.TXT:
            return ingested_doc.content.decode('utf-8')
        elif doc_type == DocumentType.XLSX:
            return self._extract_xlsx_text(ingested_doc.content)
        else:
            raise ValueError(f"Unsupported document type: {doc_type}")

    def _extract_pdf_text(self, content: bytes) -> str:
        """Extract text from PDF"""
        text = ""
        with pdfplumber.open(io.BytesIO(content)) as pdf:
            for page in pdf.pages:
                page_text = page.extract_text()
                if page_text:
                    text += page_text + "\n"
        return text

    def _extract_docx_text(self, content: bytes) -> str:
        """Extract text from DOCX"""
        doc = DocxDocument(io.BytesIO(content))
        return "\n".join([paragraph.text for paragraph in doc.paragraphs])

    def _extract_xlsx_text(self, content: bytes) -> str:
        """Extract text from Excel"""
        df = pd.read_excel(io.BytesIO(content))
        return df.to_string()

    def _create_chunks(self, text: str, document_id: str) -> List[TextChunk]:
        """Create overlapping text chunks"""
        chunks = []
        words = text.split()
        
        for i in range(0, len(words), self.chunk_size - self.chunk_overlap):
            chunk_words = words[i:i + self.chunk_size]
            chunk_text = " ".join(chunk_words)
            
            chunk = TextChunk(
                id=f"{document_id}-chunk-{len(chunks)}",
                document_id=document_id,
                content=chunk_text,
                start_index=i,
                end_index=min(i + self.chunk_size, len(words)),
                metadata={"chunk_index": len(chunks)}
            )
            chunks.append(chunk)
        
        return chunks

    def _remove_pii_from_chunks(self, chunks: List[TextChunk]) -> List[TextChunk]:
        """Remove PII from text chunks"""
        for chunk in chunks:
            for pii_type, pattern in self.pii_patterns.items():
                chunk.content = re.sub(pattern, f"[{pii_type.upper()}_REMOVED]", chunk.content)
        return chunks

    def _calculate_quality_score(self, text: str, chunks: List[TextChunk]) -> float:
        """Calculate document quality score"""
        # Simple quality metrics
        word_count = len(text.split())
        avg_chunk_length = sum(len(c.content.split()) for c in chunks) / len(chunks) if chunks else 0
        
        # Quality factors
        has_content = word_count > 50
        reasonable_chunks = 0.5 < avg_chunk_length / self.chunk_size < 1.5
        not_too_repetitive = len(set(text.split())) / word_count > 0.3 if word_count > 0 else False
        
        score = 0.0
        if has_content: score += 0.4
        if reasonable_chunks: score += 0.3
        if not_too_repetitive: score += 0.3
        
        return min(score, 1.0)

    def _detect_language(self, text: str) -> str:
        """Simple language detection"""
        # In production, use langdetect or similar library
        # For now, return default
        return "en"

    @property
    def should_remove_pii(self) -> bool:
        """Check if PII removal is enabled"""
        return True  # Could be configurable
```

### Inference Activities

```python
# activities/inference.py
import numpy as np
from typing import List, Dict
from temporalio import activity
from models.data_models import PreprocessedDocument, InferenceResults, ChunkEmbedding, ProcessingError
import openai
import asyncio

class InferenceActivities:
    def __init__(self, openai_api_key: str = None, use_mock: bool = True):
        self.use_mock = use_mock
        if not use_mock and openai_api_key:
            openai.api_key = openai_api_key
        self.embedding_model = "text-embedding-3-small"
        self.summary_model = "gpt-3.5-turbo"
        self.max_summary_length = 500

    @activity.defn
    async def run_inference_batch(self, preprocessed_docs: List[PreprocessedDocument]) -> tuple[List[InferenceResults], List[ProcessingError]]:
        """Run inference on a batch of preprocessed documents"""
        activity.logger.info(f"Starting inference batch: {len(preprocessed_docs)} documents")
        
        results = []
        errors = []
        
        for idx, doc in enumerate(preprocessed_docs):
            # Report heartbeat
            if idx % 2 == 0:
                activity.heartbeat(f"Running inference on document {idx + 1}/{len(preprocessed_docs)}")
            
            try:
                # Generate embeddings for all chunks
                embeddings = await self._generate_embeddings(doc.chunks)
                
                # Generate summary
                summary = await self._generate_summary(doc.chunks)
                
                # Extract entities
                entities = await self._extract_entities(doc.chunks)
                
                # Analyze sentiment
                sentiment = await self._analyze_sentiment(doc.chunks)
                
                inference_result = InferenceResults(
                    embeddings=embeddings,
                    summary=summary,
                    entities=entities,
                    sentiment=sentiment,
                    model_info={
                        "embedding_model": self.embedding_model,
                        "summary_model": self.summary_model,
                        "mock_mode": self.use_mock
                    }
                )
                results.append(inference_result)
                
            except Exception as e:
                activity.logger.error(f"Failed inference for document {doc.document.id}: {e}")
                errors.append(ProcessingError(
                    document_id=doc.document.id,
                    stage="inference",
                    error=str(e)
                ))
        
        activity.logger.info(f"Inference batch completed. Success: {len(results)}, Errors: {len(errors)}")
        return results, errors

    async def _generate_embeddings(self, chunks: List[TextChunk]) -> List[ChunkEmbedding]:
        """Generate embeddings for text chunks"""
        if self.use_mock:
            return self._generate_mock_embeddings(chunks)
        
        embeddings = []
        
        # Process in batches for efficiency
        batch_size = 10
        for i in range(0, len(chunks), batch_size):
            batch = chunks[i:i + batch_size]
            texts = [chunk.content for chunk in batch]
            
            # Call OpenAI API
            response = await asyncio.to_thread(
                openai.Embedding.create,
                input=texts,
                model=self.embedding_model
            )
            
            for j, embedding_data in enumerate(response['data']):
                chunk = batch[j]
                embedding = ChunkEmbedding(
                    chunk_id=chunk.id,
                    embedding=embedding_data['embedding'],
                    model=self.embedding_model,
                    dimensions=len(embedding_data['embedding'])
                )
                embeddings.append(embedding)
        
        return embeddings

    def _generate_mock_embeddings(self, chunks: List[TextChunk]) -> List[ChunkEmbedding]:
        """Generate mock embeddings for testing"""
        embeddings = []
        dimensions = 1536  # Default for text-embedding-3-small
        
        for chunk in chunks:
            # Generate deterministic embeddings based on chunk content
            np.random.seed(hash(chunk.content) % 2**32)
            embedding_vector = np.random.randn(dimensions).astype(float)
            # Normalize
            embedding_vector = embedding_vector / np.linalg.norm(embedding_vector)
            
            embedding = ChunkEmbedding(
                chunk_id=chunk.id,
                embedding=embedding_vector.tolist(),
                model=self.embedding_model,
                dimensions=dimensions
            )
            embeddings.append(embedding)
        
        return embeddings

    async def _generate_summary(self, chunks: List[TextChunk]) -> str:
        """Generate document summary"""
        if self.use_mock:
            return self._generate_mock_summary(chunks)
        
        # Combine chunks for summary
        full_text = " ".join([chunk.content for chunk in chunks[:5]])  # Use first 5 chunks
        
        prompt = f"Summarize the following document in {self.max_summary_length} words or less:\n\n{full_text}"
        
        response = await asyncio.to_thread(
            openai.ChatCompletion.create,
            model=self.summary_model,
            messages=[
                {"role": "system", "content": "You are a helpful assistant that creates concise document summaries."},
                {"role": "user", "content": prompt}
            ],
            max_tokens=self.max_summary_length
        )
        
        return response['choices'][0]['message']['content']

    def _generate_mock_summary(self, chunks: List[TextChunk]) -> str:
        """Generate mock summary for testing"""
        if not chunks:
            return "Empty document"
        
        # Simple mock summary based on content
        text_preview = chunks[0].content[:200]
        word_count = sum(len(chunk.content.split()) for chunk in chunks)
        
        return (f"This document contains {len(chunks)} sections with approximately {word_count} words. "
                f"The document begins with: '{text_preview}...' and covers various topics "
                f"related to the subject matter.")

    async def _extract_entities(self, chunks: List[TextChunk]) -> List[Dict[str, str]]:
        """Extract named entities from text"""
        # In production, use spaCy or similar NER library
        # For now, return mock entities
        entities = []
        
        # Simple pattern matching for demonstration
        for chunk in chunks[:3]:  # Process first 3 chunks
            # Mock entity extraction
            words = chunk.content.split()
            for i, word in enumerate(words):
                if word[0].isupper() and len(word) > 3:
                    if i > 0 and words[i-1][0].isupper():
                        # Might be a person name
                        entities.append({
                            "type": "PERSON",
                            "text": f"{words[i-1]} {word}",
                            "chunk_id": chunk.id
                        })
        
        return entities

    async def _analyze_sentiment(self, chunks: List[TextChunk]) -> Dict[str, float]:
        """Analyze document sentiment"""
        # Simple mock sentiment analysis
        # In production, use TextBlob, VADER, or transformer models
        
        positive_words = {'good', 'great', 'excellent', 'positive', 'success'}
        negative_words = {'bad', 'poor', 'negative', 'failure', 'problem'}
        
        positive_count = 0
        negative_count = 0
        total_words = 0
        
        for chunk in chunks:
            words = chunk.content.lower().split()
            total_words += len(words)
            positive_count += sum(1 for word in words if word in positive_words)
            negative_count += sum(1 for word in words if word in negative_words)
        
        if total_words == 0:
            return {"positive": 0.0, "negative": 0.0, "neutral": 1.0}
        
        positive_ratio = positive_count / total_words
        negative_ratio = negative_count / total_words
        neutral_ratio = 1.0 - positive_ratio - negative_ratio
        
        return {
            "positive": round(positive_ratio, 3),
            "negative": round(negative_ratio, 3),
            "neutral": round(neutral_ratio, 3)
        }
```

### Storage Activities

```python
# activities/storage.py
import json
import boto3
import psycopg2
import chromadb
from typing import List, Dict, Tuple
from temporalio import activity
from models.data_models import (
    PreprocessedDocument, InferenceResults, ProcessingError,
    Document, ChunkEmbedding
)
from datetime import datetime
import uuid

class StorageActivities:
    def __init__(self, 
                 chroma_url: str = "http://localhost:8000",
                 s3_endpoint: str = "http://localhost:9000",
                 s3_access_key: str = "minioadmin",
                 s3_secret_key: str = "minioadmin",
                 postgres_config: Dict = None):
        
        # ChromaDB client
        self.chroma_client = chromadb.HttpClient(host=chroma_url)
        
        # S3 client
        self.s3_client = boto3.client(
            's3',
            endpoint_url=s3_endpoint,
            aws_access_key_id=s3_access_key,
            aws_secret_access_key=s3_secret_key
        )
        
        # PostgreSQL config
        self.postgres_config = postgres_config or {
            'host': 'localhost',
            'port': 5433,
            'database': 'document_metadata',
            'user': 'docuser',
            'password': 'docpass'
        }

    @activity.defn
    async def store_in_vector_db(self, 
                                documents: List[PreprocessedDocument],
                                results: List[InferenceResults],
                                collection_name: str) -> List[str]:
        """Store embeddings in ChromaDB"""
        activity.logger.info(f"Storing embeddings in ChromaDB collection: {collection_name}")
        
        # Create or get collection
        collection = self.chroma_client.get_or_create_collection(name=collection_name)
        
        stored_ids = []
        
        for doc, result in zip(documents, results):
            # Prepare data for ChromaDB
            ids = []
            embeddings = []
            metadatas = []
            documents_text = []
            
            for chunk, chunk_embedding in zip(doc.chunks, result.embeddings):
                ids.append(chunk.id)
                embeddings.append(chunk_embedding.embedding)
                
                metadata = {
                    "document_id": doc.document.id,
                    "document_name": doc.document.name,
                    "chunk_index": chunk.metadata.get("chunk_index", 0),
                    "language": doc.language,
                    "quality_score": doc.quality_score,
                    **doc.document.metadata
                }
                metadatas.append(metadata)
                documents_text.append(chunk.content)
            
            # Store in ChromaDB
            collection.add(
                ids=ids,
                embeddings=embeddings,
                metadatas=metadatas,
                documents=documents_text
            )
            
            stored_ids.extend(ids)
            
            activity.heartbeat(f"Stored {len(stored_ids)} embeddings")
        
        activity.logger.info(f"Successfully stored {len(stored_ids)} embeddings")
        return stored_ids

    @activity.defn
    async def store_in_s3(self,
                         documents: List[PreprocessedDocument],
                         bucket_name: str) -> List[str]:
        """Store processed documents in S3"""
        activity.logger.info(f"Storing documents in S3 bucket: {bucket_name}")
        
        # Ensure bucket exists
        try:
            self.s3_client.head_bucket(Bucket=bucket_name)
        except:
            self.s3_client.create_bucket(Bucket=bucket_name)
        
        s3_keys = []
        
        for doc in documents:
            # Prepare document data
            doc_data = {
                "document": {
                    "id": doc.document.id,
                    "name": doc.document.name,
                    "type": doc.document.type.value,
                    "metadata": doc.document.metadata
                },
                "chunks": [
                    {
                        "id": chunk.id,
                        "content": chunk.content,
                        "start_index": chunk.start_index,
                        "end_index": chunk.end_index
                    }
                    for chunk in doc.chunks
                ],
                "preprocessing_info": {
                    "total_chunks": doc.total_chunks,
                    "language": doc.language,
                    "pii_removed": doc.pii_removed,
                    "quality_score": doc.quality_score,
                    "preprocessed_at": doc.preprocessed_at.isoformat()
                }
            }
            
            # Generate S3 key
            s3_key = f"documents/{doc.document.id}/{doc.document.id}_processed.json"
            
            # Upload to S3
            self.s3_client.put_object(
                Bucket=bucket_name,
                Key=s3_key,
                Body=json.dumps(doc_data),
                ContentType='application/json'
            )
            
            s3_keys.append(s3_key)
            
            activity.heartbeat(f"Stored {len(s3_keys)} documents in S3")
        
        activity.logger.info(f"Successfully stored {len(s3_keys)} documents in S3")
        return s3_keys

    @activity.defn
    async def store_metadata(self,
                           pipeline_id: str,
                           documents: List[Document],
                           processing_results: Dict,
                           errors: List[ProcessingError]) -> str:
        """Store pipeline metadata in PostgreSQL"""
        activity.logger.info(f"Storing metadata for pipeline: {pipeline_id}")
        
        conn = psycopg2.connect(**self.postgres_config)
        cursor = conn.cursor()
        
        try:
            # Insert pipeline run
            metadata_id = str(uuid.uuid4())
            cursor.execute("""
                INSERT INTO pipeline_runs (
                    id, pipeline_id, started_at, completed_at, 
                    total_documents, successful_documents, failed_documents,
                    configuration, status
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
            """, (
                metadata_id,
                pipeline_id,
                processing_results.get('started_at', datetime.now()),
                datetime.now(),
                len(documents),
                processing_results.get('success_count', 0),
                len(errors),
                json.dumps(processing_results.get('configuration', {})),
                'completed'
            ))
            
            # Insert document records
            for doc in documents:
                cursor.execute("""
                    INSERT INTO processed_documents (
                        id, pipeline_run_id, document_id, document_name,
                        document_type, source_url, metadata, processing_status
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                """, (
                    str(uuid.uuid4()),
                    metadata_id,
                    doc.id,
                    doc.name,
                    doc.type.value,
                    doc.url,
                    json.dumps(doc.metadata),
                    'success' if not any(e.document_id == doc.id for e in errors) else 'failed'
                ))
            
            # Insert errors
            for error in errors:
                cursor.execute("""
                    INSERT INTO processing_errors (
                        id, pipeline_run_id, document_id, stage,
                        error_message, error_time, retryable
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s)
                """, (
                    str(uuid.uuid4()),
                    metadata_id,
                    error.document_id,
                    error.stage,
                    error.error,
                    error.timestamp,
                    error.retryable
                ))
            
            conn.commit()
            activity.logger.info(f"Successfully stored metadata with ID: {metadata_id}")
            return metadata_id
            
        except Exception as e:
            conn.rollback()
            activity.logger.error(f"Failed to store metadata: {e}")
            raise
        finally:
            cursor.close()
            conn.close()
```

## Workflow Implementation

```python
# workflows/document_pipeline.py
from datetime import timedelta
from typing import List, Dict
from temporalio import workflow
from temporalio.workflow import WorkflowOptions
from temporalio.common import RetryPolicy
from temporalio.exceptions import ActivityError

from models.data_models import (
    PipelineInput, PipelineResult, Document, ProcessingError,
    IngestedDocument, PreprocessedDocument, InferenceResults
)
from activities.ingestion import IngestionActivities
from activities.preprocessing import PreprocessingActivities  
from activities.inference import InferenceActivities
from activities.postprocessing import PostprocessingActivities
from activities.storage import StorageActivities

@workflow.defn
class DocumentPipelineWorkflow:
    """Main workflow for document processing pipeline"""
    
    @workflow.run
    async def run_pipeline(self, input_params: PipelineInput) -> PipelineResult:
        workflow.logger.info(f"Starting document pipeline with input: {input_params}")
        
        # Configure activity options
        activity_options = WorkflowOptions(
            task_queue="document-pipeline-queue",
            start_to_close_timeout=timedelta(minutes=30),
            retry_policy=RetryPolicy(
                maximum_attempts=3,
                initial_interval=timedelta(seconds=1),
                maximum_interval=timedelta(minutes=1),
                backoff_coefficient=2.0
            )
        )
        
        # Create activity stubs
        ingestion = workflow.new_activity_stub(
            IngestionActivities,
            start_to_close_timeout=timedelta(minutes=60)
        )
        
        preprocessing = workflow.new_activity_stub(
            PreprocessingActivities,
            start_to_close_timeout=timedelta(minutes=30)
        )
        
        inference = workflow.new_activity_stub(
            InferenceActivities,
            start_to_close_timeout=timedelta(hours=2)
        )
        
        storage = workflow.new_activity_stub(
            StorageActivities,
            start_to_close_timeout=timedelta(minutes=30)
        )
        
        start_time = workflow.now()
        all_errors: List[ProcessingError] = []
        
        # Step 1: Parse CSV and get documents
        documents = await workflow.execute_activity(
            ingestion.parse_csv_activity,
            input_params.csv_path,
            schedule_to_close_timeout=timedelta(minutes=10)
        )
        
        workflow.logger.info(f"Found {len(documents)} documents to process")
        
        # Step 2: Process documents in batches
        batch_results = await self._process_batches(
            documents, 
            input_params,
            ingestion,
            preprocessing,
            inference,
            storage
        )
        
        # Step 3: Store final metadata
        metadata_id = await workflow.execute_activity(
            storage.store_metadata,
            workflow.info().workflow_id,
            documents,
            {
                "configuration": input_params.__dict__,
                "started_at": start_time,
                "success_count": batch_results['success_count']
            },
            batch_results['all_errors']
        )
        
        # Calculate final metrics
        processing_time = (workflow.now() - start_time).total_seconds()
        avg_quality = (
            sum(batch_results['quality_scores']) / len(batch_results['quality_scores'])
            if batch_results['quality_scores'] else 0.0
        )
        
        return PipelineResult(
            total_processed=len(documents),
            success_count=batch_results['success_count'],
            error_count=len(batch_results['all_errors']),
            avg_quality_score=avg_quality,
            processing_time_seconds=processing_time,
            metadata_id=metadata_id,
            vector_storage_ids=batch_results['vector_ids'],
            s3_object_keys=batch_results['s3_keys'],
            errors=batch_results['all_errors']
        )

    async def _process_batches(self, 
                             documents: List[Document],
                             input_params: PipelineInput,
                             ingestion,
                             preprocessing,
                             inference,
                             storage) -> Dict:
        """Process documents in batches"""
        
        all_errors = []
        all_vector_ids = []
        all_s3_keys = []
        quality_scores = []
        success_count = 0
        
        # Create batches
        batches = [
            documents[i:i + input_params.batch_size]
            for i in range(0, len(documents), input_params.batch_size)
        ]
        
        workflow.logger.info(f"Processing {len(batches)} batches of size {input_params.batch_size}")
        
        for batch_idx, batch in enumerate(batches):
            batch_id = f"{workflow.info().workflow_id}-batch-{batch_idx}"
            
            try:
                # Ingest documents
                ingested, ingest_errors = await workflow.execute_activity(
                    ingestion.download_and_validate_batch,
                    batch
                )
                all_errors.extend(ingest_errors)
                
                if not ingested:
                    workflow.logger.warning(f"No documents ingested in batch {batch_id}")
                    continue
                
                # Preprocess documents
                preprocessed, preprocess_errors = await workflow.execute_activity(
                    preprocessing.preprocess_batch,
                    ingested
                )
                all_errors.extend(preprocess_errors)
                
                if not preprocessed:
                    workflow.logger.warning(f"No documents preprocessed in batch {batch_id}")
                    continue
                
                # Run inference
                inference_results, inference_errors = await workflow.execute_activity(
                    inference.run_inference_batch,
                    preprocessed
                )
                all_errors.extend(inference_errors)
                
                if not inference_results:
                    workflow.logger.warning(f"No inference results in batch {batch_id}")
                    continue
                
                # Store results
                # Store in vector DB
                vector_ids = await workflow.execute_activity(
                    storage.store_in_vector_db,
                    preprocessed,
                    inference_results,
                    input_params.chroma_collection
                )
                all_vector_ids.extend(vector_ids)
                
                # Store in S3
                s3_keys = await workflow.execute_activity(
                    storage.store_in_s3,
                    preprocessed,
                    input_params.s3_bucket
                )
                all_s3_keys.extend(s3_keys)
                
                # Collect metrics
                for doc in preprocessed:
                    quality_scores.append(doc.quality_score)
                    success_count += 1
                
                workflow.logger.info(f"Batch {batch_id} processed successfully")
                
            except ActivityError as e:
                workflow.logger.error(f"Batch {batch_id} processing failed: {e}")
                # Create error for entire batch
                for doc in batch:
                    all_errors.append(ProcessingError(
                        document_id=doc.id,
                        stage="batch_processing",
                        error=str(e),
                        retryable=False
                    ))
        
        return {
            'all_errors': all_errors,
            'vector_ids': all_vector_ids,
            's3_keys': all_s3_keys,
            'quality_scores': quality_scores,
            'success_count': success_count
        }
```

## Worker Implementation

```python
# worker.py
import asyncio
import logging
from temporalio.client import Client
from temporalio.worker import Worker

from workflows.document_pipeline import DocumentPipelineWorkflow
from activities.ingestion import IngestionActivities
from activities.preprocessing import PreprocessingActivities
from activities.inference import InferenceActivities
from activities.postprocessing import PostprocessingActivities
from activities.storage import StorageActivities

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

async def main():
    # Create Temporal client
    client = await Client.connect("localhost:7233")
    
    # Create activity instances
    ingestion_activities = IngestionActivities()
    preprocessing_activities = PreprocessingActivities()
    inference_activities = InferenceActivities(use_mock=True)
    postprocessing_activities = PostprocessingActivities()
    storage_activities = StorageActivities()
    
    # Create worker
    worker = Worker(
        client,
        task_queue="document-pipeline-queue",
        workflows=[DocumentPipelineWorkflow],
        activities=[
            ingestion_activities.parse_csv_activity,
            ingestion_activities.download_and_validate_batch,
            preprocessing_activities.preprocess_batch,
            inference_activities.run_inference_batch,
            storage_activities.store_in_vector_db,
            storage_activities.store_in_s3,
            storage_activities.store_metadata
        ]
    )
    
    logger.info("Starting Document Pipeline Worker...")
    logger.info("Listening on task queue: document-pipeline-queue")
    
    # Run worker
    await worker.run()

if __name__ == "__main__":
    asyncio.run(main())
```

## Starter Implementation

```python
# starter.py
import asyncio
import argparse
import uuid
from temporalio.client import Client
from workflows.document_pipeline import DocumentPipelineWorkflow
from models.data_models import PipelineInput

async def main():
    parser = argparse.ArgumentParser(description="Start document processing pipeline")
    parser.add_argument("--csv-file", required=True, help="Path to CSV file")
    parser.add_argument("--batch-size", type=int, default=10, help="Batch size")
    parser.add_argument("--max-size-mb", type=int, default=50, help="Max file size in MB")
    parser.add_argument("--s3-bucket", default="documents", help="S3 bucket name")
    parser.add_argument("--vector-db-collection", default="documents", help="Vector DB collection")
    parser.add_argument("--embedding-model", default="text-embedding-3-small", help="Embedding model")
    parser.add_argument("--summary-model", default="gpt-3.5-turbo", help="Summary model")
    parser.add_argument("--wait", action="store_true", help="Wait for workflow completion")
    
    args = parser.parse_args()
    
    # Create Temporal client
    client = await Client.connect("localhost:7233")
    
    # Generate workflow ID
    workflow_id = f"doc-pipeline-{uuid.uuid4()}"
    
    # Prepare input
    pipeline_input = PipelineInput(
        csv_path=args.csv_file,
        batch_size=args.batch_size,
        max_size_mb=args.max_size_mb,
        preprocessing_options={
            "chunk_size": 1000,
            "chunk_overlap": 200,
            "remove_pii": True,
            "detect_language": True
        },
        model_config={
            "generate_embeddings": True,
            "embedding_model": args.embedding_model,
            "generate_summary": True,
            "summary_model": args.summary_model,
            "extract_entities": True
        },
        chroma_collection=args.vector_db_collection,
        s3_bucket=args.s3_bucket
    )
    
    # Start workflow
    handle = await client.start_workflow(
        DocumentPipelineWorkflow.run_pipeline,
        pipeline_input,
        id=workflow_id,
        task_queue="document-pipeline-queue"
    )
    
    print(f"Started workflow: {workflow_id}")
    print(f"Input parameters:")
    print(f"  CSV Path: {args.csv_file}")
    print(f"  Batch Size: {args.batch_size}")
    print(f"  Max Size MB: {args.max_size_mb}")
    print(f"  Chroma Collection: {args.vector_db_collection}")
    print(f"  S3 Bucket: {args.s3_bucket}")
    
    if args.wait:
        print("Waiting for workflow completion...")
        result = await handle.result()
        
        print("\nWorkflow completed!")
        print(f"Results:")
        print(f"  Total Processed: {result.total_processed}")
        print(f"  Success Count: {result.success_count}")
        print(f"  Error Count: {result.error_count}")
        print(f"  Average Quality Score: {result.avg_quality_score:.2f}")
        print(f"  Processing Time: {result.processing_time_seconds:.2f} seconds")
        print(f"  Metadata ID: {result.metadata_id}")
        print(f"  Vector Storage IDs: {len(result.vector_storage_ids)} stored")
        print(f"  S3 Objects: {len(result.s3_object_keys)} stored")
        
        if result.errors:
            print("\nErrors encountered:")
            for error in result.errors[:5]:  # Show first 5 errors
                print(f"  - Document: {error.document_id}, Stage: {error.stage}")
                print(f"    Error: {error.error}")

if __name__ == "__main__":
    asyncio.run(main())
```

## Running the Python Implementation

### Install Dependencies

```bash
pip install -r requirements.txt
```

### Start the Worker

```bash
python worker.py
```

### Run the Pipeline

```bash
# Basic execution
python starter.py --csv-file ../testdata/documents.csv

# With custom parameters
python starter.py \
    --csv-file ../testdata/documents.csv \
    --batch-size 5 \
    --max-size-mb 100 \
    --s3-bucket my-documents \
    --vector-db-collection my-collection \
    --embedding-model text-embedding-3-large \
    --wait

# Wait for completion and see results
python starter.py --csv-file ../testdata/documents.csv --wait
```

## Performance Comparison: Python vs Java vs Go

| Aspect | Python | Java | Go |
|--------|--------|------|-----|
| **Startup Time** | ~2s | ~5s | ~0.5s |
| **Memory Usage** | Medium | High | Low |
| **CPU Efficiency** | Lower | High | Highest |
| **Async Support** | Native (asyncio) | CompletableFuture | Goroutines |
| **Type Safety** | Runtime | Compile-time | Compile-time |
| **Library Ecosystem** | Excellent (ML/AI) | Good | Growing |
| **Development Speed** | Fastest | Medium | Fast |

## Best Practices for Python Temporal

1. **Use Async/Await**: Leverage Python's native async capabilities for better performance
2. **Type Hints**: Use dataclasses and type hints for better code clarity
3. **Error Handling**: Implement proper error handling with custom exception types
4. **Activity Heartbeats**: Use heartbeats for long-running activities
5. **Batch Processing**: Process documents in batches to optimize resource usage
6. **Configuration**: Use environment variables and configuration files
7. **Testing**: Write unit tests for activities and integration tests for workflows

## Summary

The Python implementation provides a clean, readable, and efficient way to build Temporal workflows. It's particularly well-suited for:
- ML/AI workloads with native library support
- Rapid prototyping and development
- Teams with Python expertise
- Integration with data science tools

While it may not match the raw performance of Go, Python's extensive ecosystem and ease of development make it an excellent choice for document processing pipelines.