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
            
            // Use TokenTextSplitter to split the text into chunks
            // Parameters: defaultChunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, keepSeparator
            TokenTextSplitter splitter = new TokenTextSplitter(
                chunkSize,           // defaultChunkSize
                350,                  // minChunkSizeChars (default)
                5,                    // minChunkLengthToEmbed (default)
                10000,                // maxNumChunks (default)
                true                  // keepSeparator (default)
            );
            
            List<Document> chunks = splitter.apply(documents);
            
            logger.debug("Split document into {} chunks", chunks.size());
            
            return chunks.stream()
                .map(Document::getText)
                .filter(text -> !text.trim().isEmpty());
                
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
