package document

import (
	"bytes"
	"fmt"
	"io"
	"strings"

	"github.com/ledongthuc/pdf"
)

// Parser interface for different document types
type Parser interface {
	Extract(content []byte) (string, error)
}

// GetParser returns the appropriate parser for a document type
func GetParser(docType string) Parser {
	switch strings.ToLower(docType) {
	case "pdf":
		return &PDFParser{}
	case "txt":
		return &TextParser{}
	case "doc", "docx":
		return &WordParser{}
	case "pptx":
		return &PowerPointParser{}
	case "xlsx":
		return &ExcelParser{}
	default:
		return nil
	}
}

// TextParser handles plain text files
type TextParser struct{}

func (p *TextParser) Extract(content []byte) (string, error) {
	return string(content), nil
}

// PDFParser handles PDF files
type PDFParser struct{}

func (p *PDFParser) Extract(content []byte) (string, error) {
	// For mock implementation, return simple text
	// In production, use pdfbox or similar library
	if len(content) < 100 {
		return "", fmt.Errorf("invalid PDF content")
	}
	
	// Mock extraction
	return "This is extracted text from a PDF document. The document contains important information about the subject matter. Lorem ipsum dolor sit amet, consectetur adipiscing elit.", nil
}

// WordParser handles DOC/DOCX files
type WordParser struct{}

func (p *WordParser) Extract(content []byte) (string, error) {
	// For mock implementation, return simple text
	// In production, use Apache POI or similar library
	if len(content) < 50 {
		return "", fmt.Errorf("invalid Word document content")
	}
	
	// Mock extraction
	return "This is extracted text from a Word document. The document provides detailed analysis and recommendations. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", nil
}

// PowerPointParser handles PPTX files
type PowerPointParser struct{}

func (p *PowerPointParser) Extract(content []byte) (string, error) {
	// Mock extraction
	if len(content) < 50 {
		return "", fmt.Errorf("invalid PowerPoint content")
	}
	
	return "Slide 1: Introduction\nThis presentation covers key topics.\n\nSlide 2: Main Points\n- Point 1\n- Point 2\n- Point 3\n\nSlide 3: Conclusion\nThank you for your attention.", nil
}

// ExcelParser handles XLSX files
type ExcelParser struct{}

func (p *ExcelParser) Extract(content []byte) (string, error) {
	// Mock extraction
	if len(content) < 50 {
		return "", fmt.Errorf("invalid Excel content")
	}
	
	return "Sheet1:\nRow1: Header1, Header2, Header3\nRow2: Data1, Data2, Data3\nRow3: Value1, Value2, Value3\n\nSummary: Total values calculated.", nil
}

// OCRParser handles image-based PDFs or scanned documents
type OCRParser struct {
	ocrServiceURL string
}

func NewOCRParser(ocrServiceURL string) *OCRParser {
	return &OCRParser{
		ocrServiceURL: ocrServiceURL,
	}
}

func (p *OCRParser) Extract(content []byte) (string, error) {
	// In production, this would call an OCR service
	// For now, return mock text
	return "This text was extracted using OCR from a scanned document. The quality may vary depending on the source material.", nil
}