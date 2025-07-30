import os
import time
import random
import asyncio
from dataclasses import dataclass
from datetime import datetime
from typing import List, Optional
from concurrent.futures import ThreadPoolExecutor

from temporalio import activity
import numpy as np


@dataclass
class EmbeddingResult:
    document_id: str
    embeddings: List[List[float]]
    model: str
    dimensions: int
    processed_at: datetime


@dataclass
class ClassificationResult:
    document_id: str
    category: str
    sub_category: str
    confidence: float
    processed_at: datetime


@dataclass
class OCRResult:
    document_id: str
    extracted_text: str
    confidence: float
    language: str
    processed_at: datetime


@dataclass
class ImageAnalysisResult:
    document_id: str
    has_images: bool
    image_count: int
    features: List[str]
    processed_at: datetime


@dataclass
class LLMInferenceResult:
    document_id: str
    summary: str
    keywords: List[str]
    sentiment: str
    processed_at: datetime


class GPUBoundActivities:
    def __init__(self, gpu_count: int = None):
        self.gpu_count = gpu_count or int(os.environ.get("GPU_COUNT", "2"))
        self.gpu_index = 0
        self.gpu_lock = asyncio.Lock()
        self.executor = ThreadPoolExecutor(max_workers=self.gpu_count)
        activity.logger.info(f"GPU activities initialized with {self.gpu_count} GPUs")

    async def _get_next_gpu(self) -> str:
        """Returns the next GPU to use (round-robin)"""
        async with self.gpu_lock:
            gpu = f"GPU-{self.gpu_index % self.gpu_count}"
            self.gpu_index += 1
            return gpu

    @activity.defn
    async def generate_embeddings(
        self, document_id: str, chunks: List[str]
    ) -> EmbeddingResult:
        """Generates vector embeddings using GPU"""
        gpu = await self._get_next_gpu()
        activity.logger.info(
            f"Generating embeddings on {gpu} for document {document_id}, "
            f"chunks: {len(chunks)}"
        )
        
        # Simulate GPU-accelerated embedding generation
        embeddings = []
        dimensions = 768  # Standard BERT embedding size
        
        for i, chunk in enumerate(chunks):
            # Simulate embeddings (in real implementation, would use GPU-accelerated model)
            # Using numpy for slightly more realistic simulation
            embedding = np.random.randn(dimensions).astype(np.float32)
            # Normalize the embedding
            embedding = embedding / np.linalg.norm(embedding)
            embeddings.append(embedding.tolist())
            
            # Report heartbeat for long operations
            activity.heartbeat(f"Generated embedding {i+1}/{len(chunks)}")
            
            # Simulate GPU processing time
            await asyncio.sleep(0.1)
        
        return EmbeddingResult(
            document_id=document_id,
            embeddings=embeddings,
            model="sentence-transformers/all-MiniLM-L6-v2",
            dimensions=dimensions,
            processed_at=datetime.now()
        )

    @activity.defn
    async def classify_document(
        self, document_id: str, content: str
    ) -> ClassificationResult:
        """Classifies document using GPU-accelerated ML model"""
        gpu = await self._get_next_gpu()
        activity.logger.info(f"Classifying document on {gpu}: {document_id}")
        
        # Simulate GPU-accelerated classification
        categories = ["Technical", "Business", "Legal", "Medical", "Educational"]
        sub_categories = ["Documentation", "Report", "Contract", "Research", "Manual"]
        
        # Simulate classification (in real implementation, would use GPU ML model)
        # Simple heuristic based on content
        category_scores = {}
        for category in categories:
            score = random.random() * 0.5
            if category.lower() in content.lower():
                score += 0.5
            category_scores[category] = score
        
        # Select category with highest score
        category = max(category_scores, key=category_scores.get)
        sub_category = random.choice(sub_categories)
        confidence = category_scores[category]
        
        # Simulate GPU processing time
        await asyncio.sleep(0.2)
        
        return ClassificationResult(
            document_id=document_id,
            category=category,
            sub_category=sub_category,
            confidence=confidence,
            processed_at=datetime.now()
        )

    @activity.defn
    async def perform_ocr(
        self, document_id: str, image_data: bytes
    ) -> OCRResult:
        """Performs optical character recognition using GPU"""
        gpu = await self._get_next_gpu()
        activity.logger.info(
            f"Performing OCR on {gpu} for document {document_id}, "
            f"image size: {len(image_data)} bytes"
        )
        
        # Simulate GPU-accelerated OCR
        extracted_text = f"OCR extracted text from document {document_id} using {gpu}"
        confidence = 0.85 + random.random() * 0.15
        
        # Simulate GPU processing time
        await asyncio.sleep(0.3)
        
        return OCRResult(
            document_id=document_id,
            extracted_text=extracted_text,
            confidence=confidence,
            language="en",
            processed_at=datetime.now()
        )

    @activity.defn
    async def analyze_images(
        self, document_id: str, document_data: bytes
    ) -> ImageAnalysisResult:
        """Analyzes images in document using GPU"""
        gpu = await self._get_next_gpu()
        activity.logger.info(f"Analyzing images on {gpu} for document {document_id}")
        
        # Simulate GPU-accelerated image analysis
        has_images = random.random() > 0.3
        image_count = 0
        features = []
        
        if has_images:
            image_count = random.randint(1, 5)
            possible_features = ["charts", "diagrams", "photos", "illustrations", "tables", "graphs"]
            # Randomly select some features
            num_features = random.randint(1, 4)
            features = random.sample(possible_features, num_features)
        
        # Simulate GPU processing time
        await asyncio.sleep(0.2)
        
        return ImageAnalysisResult(
            document_id=document_id,
            has_images=has_images,
            image_count=image_count,
            features=features,
            processed_at=datetime.now()
        )

    @activity.defn
    async def run_llm_inference(
        self, document_id: str, text: str
    ) -> LLMInferenceResult:
        """Runs large language model inference on GPU"""
        gpu = await self._get_next_gpu()
        activity.logger.info(
            f"Running LLM inference on {gpu} for document {document_id}, "
            f"text length: {len(text)}"
        )
        
        # Simulate GPU-accelerated LLM inference
        summary = (
            f"AI-generated summary for document {document_id}: "
            f"This document contains important information processed by {gpu}. "
            f"The content appears to be related to document processing pipelines."
        )
        
        # Extract some keywords (simulated)
        possible_keywords = [
            "temporal", "workflow", "document", "processing", "pipeline",
            "embeddings", "analysis", "data", "automation", "orchestration"
        ]
        num_keywords = random.randint(3, 6)
        keywords = random.sample(possible_keywords, num_keywords)
        
        # Determine sentiment
        sentiments = ["positive", "neutral", "negative"]
        sentiment_weights = [0.4, 0.5, 0.1]  # Bias towards positive/neutral
        sentiment = random.choices(sentiments, weights=sentiment_weights)[0]
        
        # Simulate GPU processing time (longer for LLM)
        await asyncio.sleep(0.5)
        
        return LLMInferenceResult(
            document_id=document_id,
            summary=summary,
            keywords=keywords,
            sentiment=sentiment,
            processed_at=datetime.now()
        )