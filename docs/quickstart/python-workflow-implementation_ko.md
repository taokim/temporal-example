# 문서 처리 파이프라인을 위한 Python 워크플로우 구현

## 개요

이 문서는 Temporal의 Python SDK를 사용하여 문서 처리 파이프라인의 완전한 Python 구현을 제공합니다. 이 구현은 프로덕션 준비가 된 워크플로우 구축을 위한 모범 사례, 오류 처리 및 통합 패턴을 보여줍니다.

## 사전 요구사항

```bash
# Temporal Python SDK 설치
pip install temporalio
pip install aiofiles aiohttp pandas numpy
pip install openai chromadb boto3 psycopg2-binary
pip install pdfplumber python-docx pillow pytesseract
```

## 프로젝트 구조

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

## 데이터 모델

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

## 액티비티 구현

### 수집 액티비티

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
        """CSV 파일을 파싱하고 문서 목록을 반환"""
        activity.logger.info(f"CSV 파일 파싱 중: {csv_path}")
        documents = []
        
        async with aiofiles.open(csv_path, mode='r') as file:
            content = await file.read()
            reader = csv.DictReader(content.splitlines())
            
            for idx, row in enumerate(reader):
                # 100개 문서마다 하트비트 보고
                if idx % 100 == 0:
                    activity.heartbeat(f"{idx}개 문서 처리됨")
                
                try:
                    doc_type = DocumentType(row['type'])
                    if doc_type not in self.allowed_types:
                        activity.logger.warning(f"지원되지 않는 타입: {row['type']}")
                        continue
                    
                    doc = Document(
                        id=f"doc-{idx}-{row['name'].replace(' ', '-')}",
                        name=row['name'],
                        url=row['url'],
                        type=doc_type,
                        metadata_raw=row.get('metadata', '{}')
                    )
                    
                    # 메타데이터 JSON 파싱
                    if doc.metadata_raw:
                        import json
                        try:
                            doc.metadata = json.loads(doc.metadata_raw)
                        except json.JSONDecodeError:
                            doc.metadata = {"raw": doc.metadata_raw}
                    
                    documents.append(doc)
                    
                except Exception as e:
                    activity.logger.error(f"{idx}번째 줄의 문서 파싱 실패: {e}")
        
        activity.logger.info(f"CSV 파싱 완료. {len(documents)}개 문서 발견")
        return documents

    @activity.defn
    async def download_and_validate_batch(self, documents: List[Document]) -> tuple[List[IngestedDocument], List[ProcessingError]]:
        """문서 배치를 다운로드하고 검증"""
        activity.logger.info(f"문서 다운로드 배치 시작: {len(documents)}개 문서")
        
        ingested = []
        errors = []
        
        async with aiohttp.ClientSession() as session:
            for idx, doc in enumerate(documents):
                # 하트비트 보고
                if idx % 5 == 0:
                    activity.heartbeat(f"문서 다운로드 중 {idx + 1}/{len(documents)}")
                
                try:
                    content = await self._download_document(session, doc)
                    
                    # 크기 검증
                    if len(content) > self.max_file_size:
                        raise ValueError(f"파일 크기 {len(content)}가 최대값 {self.max_file_size}를 초과")
                    
                    ingested_doc = IngestedDocument(
                        document=doc,
                        content=content,
                        content_type=self._get_content_type(doc.type),
                        size=len(content)
                    )
                    ingested.append(ingested_doc)
                    
                except Exception as e:
                    activity.logger.error(f"문서 {doc.id} 다운로드 실패: {e}")
                    errors.append(ProcessingError(
                        document_id=doc.id,
                        stage="ingestion",
                        error=str(e),
                        retryable=not isinstance(e, ValueError)
                    ))
        
        activity.logger.info(f"다운로드 배치 완료. 성공: {len(ingested)}, 오류: {len(errors)}")
        return ingested, errors

    async def _download_document(self, session: aiohttp.ClientSession, doc: Document) -> bytes:
        """URL 또는 파일 시스템에서 문서 다운로드"""
        if doc.url.startswith("file://"):
            # 로컬 파일 처리
            path = doc.url[7:]
            async with aiofiles.open(path, 'rb') as file:
                return await file.read()
        else:
            # HTTP 다운로드 처리
            async with session.get(doc.url) as response:
                response.raise_for_status()
                return await response.read()

    def _get_content_type(self, doc_type: DocumentType) -> str:
        """문서 타입에 대한 MIME 타입 가져오기"""
        type_map = {
            DocumentType.PDF: "application/pdf",
            DocumentType.DOCX: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            DocumentType.TXT: "text/plain",
            DocumentType.PPTX: "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            DocumentType.XLSX: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }
        return type_map.get(doc_type, "application/octet-stream")
```

### 전처리 액티비티

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
        """문서 배치를 전처리"""
        activity.logger.info(f"전처리 배치 시작: {len(ingested_docs)}개 문서")
        
        preprocessed = []
        errors = []
        
        for idx, ingested_doc in enumerate(ingested_docs):
            # 하트비트 보고
            if idx % 3 == 0:
                activity.heartbeat(f"문서 전처리 중 {idx + 1}/{len(ingested_docs)}")
            
            try:
                # 문서 타입에 따라 텍스트 추출
                text = await self._extract_text(ingested_doc)
                
                # 텍스트 청크 생성
                chunks = self._create_chunks(text, ingested_doc.document.id)
                
                # 구성된 경우 PII 제거
                if self.should_remove_pii:
                    chunks = self._remove_pii_from_chunks(chunks)
                
                # 품질 점수 계산
                quality_score = self._calculate_quality_score(text, chunks)
                
                # 언어 감지
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
                    activity.logger.warning(f"문서 {ingested_doc.document.id}가 품질 임계값 미달: {quality_score}")
                    
            except Exception as e:
                activity.logger.error(f"문서 {ingested_doc.document.id} 전처리 실패: {e}")
                errors.append(ProcessingError(
                    document_id=ingested_doc.document.id,
                    stage="preprocessing",
                    error=str(e)
                ))
        
        activity.logger.info(f"전처리 배치 완료. 성공: {len(preprocessed)}, 오류: {len(errors)}")
        return preprocessed, errors

    async def _extract_text(self, ingested_doc: IngestedDocument) -> str:
        """타입에 따라 문서에서 텍스트 추출"""
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
            raise ValueError(f"지원되지 않는 문서 타입: {doc_type}")

    def _extract_pdf_text(self, content: bytes) -> str:
        """PDF에서 텍스트 추출"""
        text = ""
        with pdfplumber.open(io.BytesIO(content)) as pdf:
            for page in pdf.pages:
                page_text = page.extract_text()
                if page_text:
                    text += page_text + "\n"
        return text

    def _extract_docx_text(self, content: bytes) -> str:
        """DOCX에서 텍스트 추출"""
        doc = DocxDocument(io.BytesIO(content))
        return "\n".join([paragraph.text for paragraph in doc.paragraphs])

    def _extract_xlsx_text(self, content: bytes) -> str:
        """Excel에서 텍스트 추출"""
        df = pd.read_excel(io.BytesIO(content))
        return df.to_string()

    def _create_chunks(self, text: str, document_id: str) -> List[TextChunk]:
        """중첩되는 텍스트 청크 생성"""
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
        """텍스트 청크에서 PII 제거"""
        for chunk in chunks:
            for pii_type, pattern in self.pii_patterns.items():
                chunk.content = re.sub(pattern, f"[{pii_type.upper()}_REMOVED]", chunk.content)
        return chunks

    def _calculate_quality_score(self, text: str, chunks: List[TextChunk]) -> float:
        """문서 품질 점수 계산"""
        # 간단한 품질 메트릭
        word_count = len(text.split())
        avg_chunk_length = sum(len(c.content.split()) for c in chunks) / len(chunks) if chunks else 0
        
        # 품질 요소
        has_content = word_count > 50
        reasonable_chunks = 0.5 < avg_chunk_length / self.chunk_size < 1.5
        not_too_repetitive = len(set(text.split())) / word_count > 0.3 if word_count > 0 else False
        
        score = 0.0
        if has_content: score += 0.4
        if reasonable_chunks: score += 0.3
        if not_too_repetitive: score += 0.3
        
        return min(score, 1.0)

    def _detect_language(self, text: str) -> str:
        """간단한 언어 감지"""
        # 프로덕션에서는 langdetect 또는 유사한 라이브러리 사용
        # 현재는 기본값 반환
        return "en"

    @property
    def should_remove_pii(self) -> bool:
        """PII 제거가 활성화되었는지 확인"""
        return True  # 구성 가능할 수 있음
```

### 추론 액티비티

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
        """전처리된 문서 배치에 대한 추론 실행"""
        activity.logger.info(f"추론 배치 시작: {len(preprocessed_docs)}개 문서")
        
        results = []
        errors = []
        
        for idx, doc in enumerate(preprocessed_docs):
            # 하트비트 보고
            if idx % 2 == 0:
                activity.heartbeat(f"문서 {idx + 1}/{len(preprocessed_docs)}에 대한 추론 실행 중")
            
            try:
                # 모든 청크에 대한 임베딩 생성
                embeddings = await self._generate_embeddings(doc.chunks)
                
                # 요약 생성
                summary = await self._generate_summary(doc.chunks)
                
                # 엔티티 추출
                entities = await self._extract_entities(doc.chunks)
                
                # 감정 분석
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
                activity.logger.error(f"문서 {doc.document.id}에 대한 추론 실패: {e}")
                errors.append(ProcessingError(
                    document_id=doc.document.id,
                    stage="inference",
                    error=str(e)
                ))
        
        activity.logger.info(f"추론 배치 완료. 성공: {len(results)}, 오류: {len(errors)}")
        return results, errors

    async def _generate_embeddings(self, chunks: List[TextChunk]) -> List[ChunkEmbedding]:
        """텍스트 청크에 대한 임베딩 생성"""
        if self.use_mock:
            return self._generate_mock_embeddings(chunks)
        
        embeddings = []
        
        # 효율성을 위해 배치로 처리
        batch_size = 10
        for i in range(0, len(chunks), batch_size):
            batch = chunks[i:i + batch_size]
            texts = [chunk.content for chunk in batch]
            
            # OpenAI API 호출
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
        """테스트를 위한 모의 임베딩 생성"""
        embeddings = []
        dimensions = 1536  # text-embedding-3-small의 기본값
        
        for chunk in chunks:
            # 청크 콘텐츠에 기반한 결정적 임베딩 생성
            np.random.seed(hash(chunk.content) % 2**32)
            embedding_vector = np.random.randn(dimensions).astype(float)
            # 정규화
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
        """문서 요약 생성"""
        if self.use_mock:
            return self._generate_mock_summary(chunks)
        
        # 요약을 위해 청크 결합
        full_text = " ".join([chunk.content for chunk in chunks[:5]])  # 처음 5개 청크 사용
        
        prompt = f"{self.max_summary_length}단어 이하로 다음 문서를 요약하세요:\n\n{full_text}"
        
        response = await asyncio.to_thread(
            openai.ChatCompletion.create,
            model=self.summary_model,
            messages=[
                {"role": "system", "content": "간결한 문서 요약을 생성하는 유용한 어시스턴트입니다."},
                {"role": "user", "content": prompt}
            ],
            max_tokens=self.max_summary_length
        )
        
        return response['choices'][0]['message']['content']

    def _generate_mock_summary(self, chunks: List[TextChunk]) -> str:
        """테스트를 위한 모의 요약 생성"""
        if not chunks:
            return "빈 문서"
        
        # 콘텐츠에 기반한 간단한 모의 요약
        text_preview = chunks[0].content[:200]
        word_count = sum(len(chunk.content.split()) for chunk in chunks)
        
        return (f"이 문서는 약 {word_count}개의 단어로 구성된 {len(chunks)}개의 섹션을 포함합니다. "
                f"문서는 다음으로 시작합니다: '{text_preview}...' 그리고 주제와 관련된 다양한 "
                f"주제를 다룹니다.")

    async def _extract_entities(self, chunks: List[TextChunk]) -> List[Dict[str, str]]:
        """텍스트에서 개체명 추출"""
        # 프로덕션에서는 spaCy 또는 유사한 NER 라이브러리 사용
        # 현재는 모의 엔티티 반환
        entities = []
        
        # 시연을 위한 간단한 패턴 매칭
        for chunk in chunks[:3]:  # 처음 3개 청크 처리
            # 모의 엔티티 추출
            words = chunk.content.split()
            for i, word in enumerate(words):
                if word[0].isupper() and len(word) > 3:
                    if i > 0 and words[i-1][0].isupper():
                        # 사람 이름일 수 있음
                        entities.append({
                            "type": "PERSON",
                            "text": f"{words[i-1]} {word}",
                            "chunk_id": chunk.id
                        })
        
        return entities

    async def _analyze_sentiment(self, chunks: List[TextChunk]) -> Dict[str, float]:
        """문서 감정 분석"""
        # 간단한 모의 감정 분석
        # 프로덕션에서는 TextBlob, VADER 또는 트랜스포머 모델 사용
        
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

### 저장소 액티비티

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
        
        # ChromaDB 클라이언트
        self.chroma_client = chromadb.HttpClient(host=chroma_url)
        
        # S3 클라이언트
        self.s3_client = boto3.client(
            's3',
            endpoint_url=s3_endpoint,
            aws_access_key_id=s3_access_key,
            aws_secret_access_key=s3_secret_key
        )
        
        # PostgreSQL 구성
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
        """ChromaDB에 임베딩 저장"""
        activity.logger.info(f"ChromaDB 컬렉션에 임베딩 저장 중: {collection_name}")
        
        # 컬렉션 생성 또는 가져오기
        collection = self.chroma_client.get_or_create_collection(name=collection_name)
        
        stored_ids = []
        
        for doc, result in zip(documents, results):
            # ChromaDB를 위한 데이터 준비
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
            
            # ChromaDB에 저장
            collection.add(
                ids=ids,
                embeddings=embeddings,
                metadatas=metadatas,
                documents=documents_text
            )
            
            stored_ids.extend(ids)
            
            activity.heartbeat(f"{len(stored_ids)}개 임베딩 저장됨")
        
        activity.logger.info(f"{len(stored_ids)}개 임베딩 성공적으로 저장됨")
        return stored_ids

    @activity.defn
    async def store_in_s3(self,
                         documents: List[PreprocessedDocument],
                         bucket_name: str) -> List[str]:
        """S3에 처리된 문서 저장"""
        activity.logger.info(f"S3 버킷에 문서 저장 중: {bucket_name}")
        
        # 버킷 존재 확인
        try:
            self.s3_client.head_bucket(Bucket=bucket_name)
        except:
            self.s3_client.create_bucket(Bucket=bucket_name)
        
        s3_keys = []
        
        for doc in documents:
            # 문서 데이터 준비
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
            
            # S3 키 생성
            s3_key = f"documents/{doc.document.id}/{doc.document.id}_processed.json"
            
            # S3에 업로드
            self.s3_client.put_object(
                Bucket=bucket_name,
                Key=s3_key,
                Body=json.dumps(doc_data),
                ContentType='application/json'
            )
            
            s3_keys.append(s3_key)
            
            activity.heartbeat(f"S3에 {len(s3_keys)}개 문서 저장됨")
        
        activity.logger.info(f"S3에 {len(s3_keys)}개 문서 성공적으로 저장됨")
        return s3_keys

    @activity.defn
    async def store_metadata(self,
                           pipeline_id: str,
                           documents: List[Document],
                           processing_results: Dict,
                           errors: List[ProcessingError]) -> str:
        """PostgreSQL에 파이프라인 메타데이터 저장"""
        activity.logger.info(f"파이프라인 메타데이터 저장 중: {pipeline_id}")
        
        conn = psycopg2.connect(**self.postgres_config)
        cursor = conn.cursor()
        
        try:
            # 파이프라인 실행 삽입
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
            
            # 문서 레코드 삽입
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
            
            # 오류 삽입
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
            activity.logger.info(f"메타데이터가 ID로 성공적으로 저장됨: {metadata_id}")
            return metadata_id
            
        except Exception as e:
            conn.rollback()
            activity.logger.error(f"메타데이터 저장 실패: {e}")
            raise
        finally:
            cursor.close()
            conn.close()
```

## 워크플로우 구현

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
    """문서 처리 파이프라인을 위한 메인 워크플로우"""
    
    @workflow.run
    async def run_pipeline(self, input_params: PipelineInput) -> PipelineResult:
        workflow.logger.info(f"입력과 함께 문서 파이프라인 시작: {input_params}")
        
        # 액티비티 옵션 구성
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
        
        # 액티비티 스텁 생성
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
        
        # 1단계: CSV 파싱 및 문서 가져오기
        documents = await workflow.execute_activity(
            ingestion.parse_csv_activity,
            input_params.csv_path,
            schedule_to_close_timeout=timedelta(minutes=10)
        )
        
        workflow.logger.info(f"처리할 {len(documents)}개 문서 발견")
        
        # 2단계: 배치로 문서 처리
        batch_results = await self._process_batches(
            documents, 
            input_params,
            ingestion,
            preprocessing,
            inference,
            storage
        )
        
        # 3단계: 최종 메타데이터 저장
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
        
        # 최종 메트릭 계산
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
        """배치로 문서 처리"""
        
        all_errors = []
        all_vector_ids = []
        all_s3_keys = []
        quality_scores = []
        success_count = 0
        
        # 배치 생성
        batches = [
            documents[i:i + input_params.batch_size]
            for i in range(0, len(documents), input_params.batch_size)
        ]
        
        workflow.logger.info(f"{input_params.batch_size} 크기의 {len(batches)}개 배치 처리 중")
        
        for batch_idx, batch in enumerate(batches):
            batch_id = f"{workflow.info().workflow_id}-batch-{batch_idx}"
            
            try:
                # 문서 수집
                ingested, ingest_errors = await workflow.execute_activity(
                    ingestion.download_and_validate_batch,
                    batch
                )
                all_errors.extend(ingest_errors)
                
                if not ingested:
                    workflow.logger.warning(f"배치 {batch_id}에서 수집된 문서 없음")
                    continue
                
                # 문서 전처리
                preprocessed, preprocess_errors = await workflow.execute_activity(
                    preprocessing.preprocess_batch,
                    ingested
                )
                all_errors.extend(preprocess_errors)
                
                if not preprocessed:
                    workflow.logger.warning(f"배치 {batch_id}에서 전처리된 문서 없음")
                    continue
                
                # 추론 실행
                inference_results, inference_errors = await workflow.execute_activity(
                    inference.run_inference_batch,
                    preprocessed
                )
                all_errors.extend(inference_errors)
                
                if not inference_results:
                    workflow.logger.warning(f"배치 {batch_id}에서 추론 결과 없음")
                    continue
                
                # 결과 저장
                # 벡터 DB에 저장
                vector_ids = await workflow.execute_activity(
                    storage.store_in_vector_db,
                    preprocessed,
                    inference_results,
                    input_params.chroma_collection
                )
                all_vector_ids.extend(vector_ids)
                
                # S3에 저장
                s3_keys = await workflow.execute_activity(
                    storage.store_in_s3,
                    preprocessed,
                    input_params.s3_bucket
                )
                all_s3_keys.extend(s3_keys)
                
                # 메트릭 수집
                for doc in preprocessed:
                    quality_scores.append(doc.quality_score)
                    success_count += 1
                
                workflow.logger.info(f"배치 {batch_id} 성공적으로 처리됨")
                
            except ActivityError as e:
                workflow.logger.error(f"배치 {batch_id} 처리 실패: {e}")
                # 전체 배치에 대한 오류 생성
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

## 워커 구현

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

# 로깅 구성
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

async def main():
    # Temporal 클라이언트 생성
    client = await Client.connect("localhost:7233")
    
    # 액티비티 인스턴스 생성
    ingestion_activities = IngestionActivities()
    preprocessing_activities = PreprocessingActivities()
    inference_activities = InferenceActivities(use_mock=True)
    postprocessing_activities = PostprocessingActivities()
    storage_activities = StorageActivities()
    
    # 워커 생성
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
    
    logger.info("문서 파이프라인 워커 시작 중...")
    logger.info("작업 큐 리스닝 중: document-pipeline-queue")
    
    # 워커 실행
    await worker.run()

if __name__ == "__main__":
    asyncio.run(main())
```

## 스타터 구현

```python
# starter.py
import asyncio
import argparse
import uuid
from temporalio.client import Client
from workflows.document_pipeline import DocumentPipelineWorkflow
from models.data_models import PipelineInput

async def main():
    parser = argparse.ArgumentParser(description="문서 처리 파이프라인 시작")
    parser.add_argument("--csv-file", required=True, help="CSV 파일 경로")
    parser.add_argument("--batch-size", type=int, default=10, help="배치 크기")
    parser.add_argument("--max-size-mb", type=int, default=50, help="최대 파일 크기 (MB)")
    parser.add_argument("--s3-bucket", default="documents", help="S3 버킷 이름")
    parser.add_argument("--vector-db-collection", default="documents", help="벡터 DB 컬렉션")
    parser.add_argument("--embedding-model", default="text-embedding-3-small", help="임베딩 모델")
    parser.add_argument("--summary-model", default="gpt-3.5-turbo", help="요약 모델")
    parser.add_argument("--wait", action="store_true", help="워크플로우 완료 대기")
    
    args = parser.parse_args()
    
    # Temporal 클라이언트 생성
    client = await Client.connect("localhost:7233")
    
    # 워크플로우 ID 생성
    workflow_id = f"doc-pipeline-{uuid.uuid4()}"
    
    # 입력 준비
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
    
    # 워크플로우 시작
    handle = await client.start_workflow(
        DocumentPipelineWorkflow.run_pipeline,
        pipeline_input,
        id=workflow_id,
        task_queue="document-pipeline-queue"
    )
    
    print(f"워크플로우 시작됨: {workflow_id}")
    print(f"입력 매개변수:")
    print(f"  CSV 경로: {args.csv_file}")
    print(f"  배치 크기: {args.batch_size}")
    print(f"  최대 크기 MB: {args.max_size_mb}")
    print(f"  Chroma 컬렉션: {args.vector_db_collection}")
    print(f"  S3 버킷: {args.s3_bucket}")
    
    if args.wait:
        print("워크플로우 완료 대기 중...")
        result = await handle.result()
        
        print("\n워크플로우 완료!")
        print(f"결과:")
        print(f"  총 처리됨: {result.total_processed}")
        print(f"  성공 수: {result.success_count}")
        print(f"  오류 수: {result.error_count}")
        print(f"  평균 품질 점수: {result.avg_quality_score:.2f}")
        print(f"  처리 시간: {result.processing_time_seconds:.2f}초")
        print(f"  메타데이터 ID: {result.metadata_id}")
        print(f"  벡터 저장소 ID: {len(result.vector_storage_ids)}개 저장됨")
        print(f"  S3 객체: {len(result.s3_object_keys)}개 저장됨")
        
        if result.errors:
            print("\n발생한 오류:")
            for error in result.errors[:5]:  # 처음 5개 오류 표시
                print(f"  - 문서: {error.document_id}, 단계: {error.stage}")
                print(f"    오류: {error.error}")

if __name__ == "__main__":
    asyncio.run(main())
```

## Python 구현 실행

### 의존성 설치

```bash
pip install -r requirements.txt
```

### 워커 시작

```bash
python worker.py
```

### 파이프라인 실행

```bash
# 기본 실행
python starter.py --csv-file ../testdata/documents.csv

# 사용자 정의 매개변수 사용
python starter.py \
    --csv-file ../testdata/documents.csv \
    --batch-size 5 \
    --max-size-mb 100 \
    --s3-bucket my-documents \
    --vector-db-collection my-collection \
    --embedding-model text-embedding-3-large \
    --wait

# 완료 대기 및 결과 확인
python starter.py --csv-file ../testdata/documents.csv --wait
```

## 성능 비교: Python vs Java vs Go

| 측면 | Python | Java | Go |
|--------|--------|------|-----|
| **시작 시간** | ~2초 | ~5초 | ~0.5초 |
| **메모리 사용량** | 중간 | 높음 | 낮음 |
| **CPU 효율성** | 낮음 | 높음 | 최고 |
| **비동기 지원** | 네이티브 (asyncio) | CompletableFuture | 고루틴 |
| **타입 안전성** | 런타임 | 컴파일 타임 | 컴파일 타임 |
| **라이브러리 생태계** | 우수 (ML/AI) | 양호 | 성장 중 |
| **개발 속도** | 가장 빠름 | 보통 | 빠름 |

## Python Temporal을 위한 모범 사례

1. **Async/Await 사용**: 더 나은 성능을 위해 Python의 네이티브 비동기 기능 활용
2. **타입 힌트**: 더 나은 코드 명확성을 위해 데이터클래스와 타입 힌트 사용
3. **오류 처리**: 사용자 정의 예외 타입으로 적절한 오류 처리 구현
4. **액티비티 하트비트**: 장시간 실행 액티비티에 대한 하트비트 사용
5. **배치 처리**: 리소스 사용을 최적화하기 위해 문서를 배치로 처리
6. **구성**: 환경 변수와 구성 파일 사용
7. **테스팅**: 액티비티에 대한 단위 테스트와 워크플로우에 대한 통합 테스트 작성

## 요약

Python 구현은 Temporal 워크플로우를 구축하는 깨끗하고 읽기 쉬우며 효율적인 방법을 제공합니다. 특히 다음에 적합합니다:
- 네이티브 라이브러리 지원을 통한 ML/AI 워크로드
- 빠른 프로토타이핑 및 개발
- Python 전문성을 가진 팀
- 데이터 과학 도구와의 통합

Go의 원시 성능과 일치하지 않을 수 있지만, Python의 광범위한 생태계와 개발 용이성은 문서 처리 파이프라인에 탁월한 선택입니다.