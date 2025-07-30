package com.example.utils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class DocumentParser {
    
    private final Tika tika;
    
    public DocumentParser() {
        this.tika = new Tika();
    }
    
    public String extractText(byte[] content, String contentType) throws IOException {
        if (contentType == null) {
            contentType = tika.detect(content);
        }
        
        switch (contentType.toLowerCase()) {
            case "application/pdf":
                return extractPDFText(content);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                return extractDOCXText(content);
            case "text/plain":
            case "text/csv":
            case "text/markdown":
                return new String(content, StandardCharsets.UTF_8);
            default:
                // Use Tika for other formats
                try {
                    return tika.parseToString(new ByteArrayInputStream(content));
                } catch (TikaException e) {
                    throw new IOException("Failed to parse document with Tika: " + e.getMessage(), e);
                }
        }
    }
    
    private String extractPDFText(byte[] content) throws IOException {
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
    
    private String extractDOCXText(byte[] content) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(content);
             XWPFDocument document = new XWPFDocument(bais)) {
            
            StringBuilder text = new StringBuilder();
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            
            for (XWPFParagraph paragraph : paragraphs) {
                String paragraphText = paragraph.getText();
                if (paragraphText != null && !paragraphText.trim().isEmpty()) {
                    text.append(paragraphText).append("\n");
                }
            }
            
            return text.toString();
        }
    }
}