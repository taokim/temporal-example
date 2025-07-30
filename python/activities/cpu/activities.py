import os
import re
import time
import multiprocessing
from dataclasses import dataclass
from datetime import datetime
from typing import List, Optional

from temporalio import activity


@dataclass
class TextProcessingResult:
    processed_text: str
    word_count: int
    language: str
    processing_time: float


@dataclass
class ValidationResult:
    is_valid: bool
    issues: List[str]
    structure: str
    validated_at: datetime


class CPUBoundActivities:
    def __init__(self):
        self.cpu_cores = multiprocessing.cpu_count()
        activity.logger.info(f"CPU activities initialized with {self.cpu_cores} cores")

    @activity.defn
    async def preprocess_text(self, document_id: str, text: str) -> TextProcessingResult:
        """Performs CPU-intensive text preprocessing"""
        activity.logger.info(
            f"CPU-bound text preprocessing started for document {document_id}, "
            f"using {self.cpu_cores} cores"
        )
        
        start_time = time.time()
        
        # CPU-intensive text processing
        processed_text = text.strip()
        processed_text = processed_text.replace("\r\n", "\n")
        processed_text = processed_text.replace("\t", " ")
        
        # Remove multiple spaces
        processed_text = re.sub(r'\s+', ' ', processed_text)
        
        # Count words (CPU-intensive)
        words = processed_text.split()
        word_count = len(words)
        
        # Simple language detection (CPU-intensive pattern matching)
        language = "en"  # Default
        if any(word in processed_text.lower() for word in ["der", "die", "das", "und", "ist"]):
            language = "de"
        elif any(word in processed_text.lower() for word in ["le", "la", "les", "et", "est"]):
            language = "fr"
        elif any(word in processed_text.lower() for word in ["el", "la", "los", "las", "es"]):
            language = "es"
        
        processing_time = time.time() - start_time
        
        return TextProcessingResult(
            processed_text=processed_text,
            word_count=word_count,
            language=language,
            processing_time=processing_time
        )

    @activity.defn
    async def validate_document_structure(
        self, document_id: str, content: str
    ) -> ValidationResult:
        """Validates document structure using CPU"""
        activity.logger.info(f"Validating document structure for: {document_id}")
        
        issues = []
        is_valid = True
        
        # CPU-intensive validation checks
        if len(content) < 10:
            issues.append("Content too short")
            is_valid = False
        
        if not content.strip():
            issues.append("Content is empty")
            is_valid = False
        
        if not any(c.isalpha() for c in content):
            issues.append("No alphabetic characters found")
            is_valid = False
        
        # Detect structure
        structure = "plain"
        if "{" in content and "}" in content:
            # Check for valid JSON structure
            try:
                import json
                json.loads(content)
                structure = "json"
            except:
                if content.count("{") == content.count("}"):
                    structure = "json-like"
        elif "<" in content and ">" in content:
            if content.count("<") == content.count(">"):
                structure = "xml"
        elif content.startswith("#") or "\n#" in content:
            structure = "markdown"
        elif content.count("\t") > len(content.split("\n")) * 0.5:
            structure = "tsv"
        elif content.count(",") > len(content.split("\n")) * 0.5:
            structure = "csv"
        
        return ValidationResult(
            is_valid=is_valid,
            issues=issues,
            structure=structure,
            validated_at=datetime.now()
        )

    @activity.defn
    async def extract_text_from_document(
        self, document_id: str, document_data: bytes
    ) -> str:
        """Extracts text from binary document data"""
        activity.logger.info(
            f"Extracting text from document: {document_id} using {self.cpu_cores} CPU cores"
        )
        
        # Simulate CPU-intensive text extraction
        # In real implementation, this would use libraries like pdfplumber, python-docx, etc.
        
        # Simple file type detection
        if len(document_data) > 4 and document_data[:4] == b"%PDF":
            # PDF document
            extracted_text = f"Extracted text from PDF document {document_id} (simulated)"
        elif len(document_data) > 2 and document_data[0:2] == b"PK":
            # Likely DOCX or other zip-based format
            extracted_text = f"Extracted text from DOCX document {document_id} (simulated)"
        else:
            # Try to decode as text
            try:
                extracted_text = document_data.decode('utf-8', errors='ignore')
            except:
                extracted_text = f"Extracted text from binary document {document_id} (simulated)"
        
        return extracted_text

    @activity.defn
    async def tokenize_text(self, document_id: str, text: str) -> List[str]:
        """Performs text tokenization"""
        activity.logger.info(f"Tokenizing text for document: {document_id}")
        
        # Simple word-based tokenization (CPU-intensive for large texts)
        # Remove punctuation and convert to lowercase
        import string
        
        # Remove punctuation
        translator = str.maketrans('', '', string.punctuation)
        text_no_punct = text.translate(translator)
        
        # Split into words and convert to lowercase
        tokens = text_no_punct.lower().split()
        
        # Remove empty tokens
        tokens = [token for token in tokens if token]
        
        activity.logger.info(f"Tokenized {len(tokens)} tokens for document {document_id}")
        
        return tokens

    @activity.defn
    async def compress_document(self, document_id: str, document_data: bytes) -> bytes:
        """Compresses document data using CPU"""
        activity.logger.info(
            f"Compressing document: {document_id}, original size: {len(document_data)} bytes"
        )
        
        import zlib
        
        # CPU-intensive compression
        compressed = zlib.compress(document_data, level=9)  # Maximum compression
        
        compression_ratio = len(compressed) / len(document_data)
        activity.logger.info(
            f"Document compressed: {document_id}, "
            f"compressed size: {len(compressed)} bytes, "
            f"ratio: {compression_ratio:.2f}"
        )
        
        return compressed