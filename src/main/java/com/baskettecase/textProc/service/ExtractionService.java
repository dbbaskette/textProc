package com.baskettecase.textProc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * ExtractionService provides utilities to extract text content from files using Apache Tika.
 * <p>
 * Supports both advanced (metadata-aware) and simple extraction methods. Used by both standalone and SCDF profiles.
 * Logs all extraction attempts and errors.
 */
@Service
public class ExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(ExtractionService.class);

    /**
     * Extracts text from a file in chunks for streaming.
     *
     * @param filePath The path to the file to extract text from
     * @param chunkSize The maximum size of each text chunk in bytes
     * @return A stream of text chunks
     * @throws IOException If an I/O error occurs
     */
    /**
     * Extracts text from a file in chunks using Spring AI's TikaDocumentReader and TokenTextSplitter.
     *
     * @param filePath The path to the file to extract text from
     * @param chunkSize The target size for each text chunk in tokens
     * @return A stream of text chunks
     * @throws IOException If an I/O error occurs
     */
    public Stream<String> extractTextInChunks(Path filePath, int chunkSize) {
        long fileSize;
        try {
            fileSize = Files.size(filePath);
            logger.info("Starting text extraction from file: {} (size: {} bytes, {} MB)", 
                      filePath, fileSize, String.format("%.2f", fileSize / (1024.0 * 1024.0)));
            
            // Use Spring AI's TikaDocumentReader to read the document with file URI
            String fileUri = filePath.toUri().toString();
            logger.debug("Reading document from URI: {}", fileUri);
            
            DocumentReader reader = new TikaDocumentReader(fileUri);
            List<Document> documents = reader.get();
            
            if (documents.isEmpty()) {
                logger.warn("No content extracted from the document");
                return Stream.empty();
            }
            
            logger.debug("Successfully extracted document with {} pages", documents.size());
            
            // Process each page as a separate document
            List<Document> allChunks = new ArrayList<>();
            
            // Configure the splitter with more appropriate settings for PDF content
            // Using a smaller chunk size and more aggressive splitting for better handling of PDF content
            TokenTextSplitter splitter = new TokenTextSplitter(
                chunkSize / 2,        // Smaller chunk size for PDFs
                100,                   // Smaller min chunk size to avoid losing content
                20,                    // Higher min length to embed to avoid very small chunks
                1000,                  // Lower max chunks to prevent memory issues
                true                   // Keep separator to maintain context
            );
            
            // Process each page separately to maintain page boundaries
            for (Document doc : documents) {
                String pageText = doc.getText();
                if (pageText != null && !pageText.trim().isEmpty()) {
                    // Split each page's content into chunks
                    List<Document> pageChunks = splitter.apply(List.of(doc));
                    allChunks.addAll(pageChunks);
                    
                    logger.trace("Page split into {} chunks", pageChunks.size());
                }
            }
            
            logger.debug("Document split into {} total chunks across all pages", allChunks.size());
            
            // Return the text content of all chunks
            return allChunks.stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.trim().isEmpty());
                
        } catch (Exception e) {
            logger.error("Error processing file: " + e.getMessage(), e);
            return Stream.empty();
        }
    }
    
    /**
     * Extracts text from an input stream in chunks using Spring AI's TikaDocumentReader and TokenTextSplitter.
     *
     * @param inputStream The input stream to extract text from
     * @param chunkSize The target size for each text chunk in tokens (default: 1000 if not specified)
     * @return A stream of text chunks
     */
    public Stream<String> extractTextInChunks(InputStream inputStream, int chunkSize) {
        Path tempFile = null;
        try {
            // Create a temporary file to store the input stream
            tempFile = Files.createTempFile("textproc-", ".dat");
            logger.debug("Created temporary file for processing: {}", tempFile);
            
            // Copy the input stream to a temporary file
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            // Use the file-based extraction method
            return extractTextInChunks(tempFile, chunkSize);
        } catch (Exception e) {
            logger.error("Error processing input stream: " + e.getMessage(), e);
            return Stream.empty();
        } finally {
            // Clean up the temporary file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    logger.warn("Failed to delete temporary file: {}", tempFile, e);
                }
            }
        }
    }
    

    
    /**
     * Extracts all text from a file using Spring AI's TikaDocumentReader.
     *
     * @param filePath The path to the file to extract text from
     * @return The extracted text content
     * @throws IOException If an I/O error occurs
     */
    public String extractTextFromFile(Path filePath) throws IOException {
        logger.info("Extracting text from file: {}", filePath);
        DocumentReader reader = new TikaDocumentReader(filePath.toString());
        List<Document> documents = reader.get();
        
        if (documents.isEmpty()) {
            logger.warn("No content extracted from the document");
            return "";
        }
        
        // Combine all document pages into a single string
        return documents.stream()
            .map(Document::getText)
            .reduce("", (a, b) -> a + "\n\n" + b);
    }
}
