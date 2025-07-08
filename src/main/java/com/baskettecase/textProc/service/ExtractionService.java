package com.baskettecase.textProc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private static final long LARGE_FILE_THRESHOLD = 50 * 1024 * 1024; // 50MB threshold for streaming

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
            
            // For large files, use streaming approach to avoid memory issues
            if (fileSize > LARGE_FILE_THRESHOLD) {
                logger.info("File is large ({} MB), using streaming extraction", String.format("%.2f", fileSize / (1024.0 * 1024.0)));
                return extractTextInChunksStreaming(filePath, chunkSize);
            }
            
            // Use Spring AI's TikaDocumentReader to read the document with file path
            logger.debug("Reading document from file path: {}", filePath);
            
            // Read the file into a byte array and use ByteArrayResource
            byte[] fileBytes = Files.readAllBytes(filePath);
            ByteArrayResource resource = new ByteArrayResource(fileBytes);
            DocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.get();
            
            if (documents.isEmpty()) {
                logger.warn("No content extracted from the document");
                return Stream.empty();
            }
            
            logger.debug("Successfully extracted document with {} pages", documents.size());

            // Diagnostic: log the length and a snippet of each page's text
            int pageNum = 0;
            for (Document doc : documents) {
                String pageText = doc.getText();
                int len = (pageText != null) ? pageText.length() : 0;
                logger.debug("[DIAG] Page {} extracted text length: {}", pageNum, len);
                if (pageText != null && !pageText.isEmpty()) {
                    logger.debug("[DIAG] Page {} first 300 chars: {}", pageNum, pageText.substring(0, Math.min(300, pageText.length())).replaceAll("\n", " "));
                }
                pageNum++;
            }

            // Process each page as a separate document
            List<Document> allChunks = new ArrayList<>();
            
            // Configure the splitter with appropriate token-based settings
            // Convert byte-based chunkSize to appropriate token count
            // Typical ratio: 1 token ≈ 3-4 characters, so we use a conservative approach
            int tokenChunkSize = Math.max(1000, Math.min(2000, chunkSize / 4)); // 1000-2000 tokens
            int minChunkSize = Math.max(100, tokenChunkSize / 10); // 10% of chunk size
            int minChunkLengthToEmbed = Math.max(20, tokenChunkSize / 50); // 2% of chunk size
            
            logger.debug("[DIAG] Using TokenTextSplitter with {} tokens per chunk (converted from {} bytes)", 
                       tokenChunkSize, chunkSize);
            
            TokenTextSplitter splitter = new TokenTextSplitter(
                tokenChunkSize,           // Appropriate token-based chunk size (1000-2000 tokens)
                minChunkSize,             // Minimum chunk size to avoid losing content
                minChunkLengthToEmbed,    // Minimum length to embed to avoid very small chunks
                1000,                     // Maximum number of chunks to prevent memory issues
                true                      // Keep separator to maintain context
            );
            
            // Process each page separately to maintain page boundaries
            pageNum = 0;
            for (Document doc : documents) {
                String pageText = doc.getText();
                if (pageText != null && !pageText.trim().isEmpty()) {
                    // Split each page's content into chunks
                    List<Document> pageChunks = splitter.apply(List.of(doc));
                    allChunks.addAll(pageChunks);
                    logger.debug("[DIAG] Page {} split into {} chunks", pageNum, pageChunks.size());
                    for (int i = 0; i < pageChunks.size(); i++) {
                        String chunkText = pageChunks.get(i).getText();
                        int chunkLen = (chunkText != null) ? chunkText.length() : 0;
                        logger.debug("[DIAG] Page {} Chunk {} length: {} | First 150 chars: {}", pageNum, i, chunkLen, (chunkText != null ? chunkText.substring(0, Math.min(150, chunkText.length())).replaceAll("\n", " ") : ""));
                    }
                }
                pageNum++;
            }
            
            logger.debug("Document split into {} total chunks across all pages", allChunks.size());
            logger.debug("[DIAG] Total chunks after splitting: {}", allChunks.size());
            
            // Return the text content of all chunks
            return allChunks.stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.trim().isEmpty());
                
        } catch (Exception e) {
            return handleExtractionError(filePath, e);
        }
    }
    
    /**
     * Extracts text from large files using streaming approach to avoid memory issues.
     * Processes the file in smaller chunks to prevent OutOfMemoryError.
     *
     * @param filePath The path to the file to extract text from
     * @param chunkSize The target size for each text chunk in tokens
     * @return A stream of text chunks
     */
    private Stream<String> extractTextInChunksStreaming(Path filePath, int chunkSize) {
        try {
            logger.info("Using streaming extraction for large file: {}", filePath);
            
            // Use InputStreamResource to avoid loading entire file into memory
            try (InputStream inputStream = Files.newInputStream(filePath)) {
                InputStreamResource resource = new InputStreamResource(inputStream);
                DocumentReader reader = new TikaDocumentReader(resource);
                List<Document> documents = reader.get();
                
                if (documents.isEmpty()) {
                    logger.warn("No content extracted from the large document");
                    return Stream.empty();
                }
                
                logger.info("Successfully extracted {} pages from large document", documents.size());
                
                // Configure the splitter with conservative settings for large files
                int tokenChunkSize = Math.max(500, Math.min(1000, chunkSize / 8)); // Smaller chunks for large files
                int minChunkSize = Math.max(50, tokenChunkSize / 10);
                int minChunkLengthToEmbed = Math.max(10, tokenChunkSize / 50);
                
                logger.info("Using conservative TokenTextSplitter for large file: {} tokens per chunk", tokenChunkSize);
                
                TokenTextSplitter splitter = new TokenTextSplitter(
                    tokenChunkSize,
                    minChunkSize,
                    minChunkLengthToEmbed,
                    500, // Limit chunks for large files
                    true
                );
                
                // Process each page separately
                List<Document> allChunks = new ArrayList<>();
                int pageNum = 0;
                
                for (Document doc : documents) {
                    String pageText = doc.getText();
                    if (pageText != null && !pageText.trim().isEmpty()) {
                        List<Document> pageChunks = splitter.apply(List.of(doc));
                        allChunks.addAll(pageChunks);
                        logger.info("Large file - Page {} split into {} chunks", pageNum, pageChunks.size());
                    }
                    pageNum++;
                }
                
                logger.info("Large file split into {} total chunks", allChunks.size());
                
                return allChunks.stream()
                    .map(Document::getText)
                    .filter(text -> text != null && !text.trim().isEmpty());
            }
        } catch (Exception e) {
            logger.error("Error during streaming extraction of large file: {}", filePath, e);
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
        try {
            long fileSize = Files.size(filePath);
            
            // For large files, use streaming approach
            if (fileSize > LARGE_FILE_THRESHOLD) {
                logger.info("File is large ({} MB), using streaming extraction", String.format("%.2f", fileSize / (1024.0 * 1024.0)));
                return extractTextFromFileStreaming(filePath);
            }
            
            // Read the file into a byte array and use ByteArrayResource
            byte[] fileBytes = Files.readAllBytes(filePath);
            ByteArrayResource resource = new ByteArrayResource(fileBytes);
            DocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.get();
            
            if (documents.isEmpty()) {
                logger.warn("No content extracted from the document");
                return "";
            }
            
            // Combine all document pages into a single string
            return documents.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a + "\n\n" + b);
        } catch (RuntimeException e) {
            // Handle the same error types but return empty string instead of stream
            handleExtractionError(filePath, e);
            return ""; // Return empty string for graceful degradation
        }
    }
    
    /**
     * Extracts all text from large files using streaming approach to avoid memory issues.
     *
     * @param filePath The path to the file to extract text from
     * @return The extracted text content
     * @throws IOException If an I/O error occurs
     */
    private String extractTextFromFileStreaming(Path filePath) throws IOException {
        logger.info("Using streaming extraction for large file: {}", filePath);
        
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            InputStreamResource resource = new InputStreamResource(inputStream);
            DocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.get();
            
            if (documents.isEmpty()) {
                logger.warn("No content extracted from the large document");
                return "";
            }
            
            logger.info("Successfully extracted {} pages from large document", documents.size());
            
            // Combine all document pages into a single string
            return documents.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a + "\n\n" + b);
        } catch (Exception e) {
            logger.error("Error during streaming extraction of large file: {}", filePath, e);
            return "";
        }
    }
    
    /**
     * Handles extraction errors with specific categorization and appropriate responses.
     * 
     * @param filePath The path of the file that failed processing
     * @param e The exception that occurred
     * @return An empty stream (graceful degradation)
     */
    private Stream<String> handleExtractionError(Path filePath, Exception e) {
        String fileName = filePath != null ? filePath.getFileName().toString() : "unknown";
        String errorMessage = e.getMessage();
        
        // Categorize the error type for better logging and potential recovery
        if (errorMessage != null) {
            if (errorMessage.contains("Missing root object specification in trailer") ||
                errorMessage.contains("TIKA-198") ||
                errorMessage.contains("Illegal IOException from org.apache.tika.parser.pdf.PDFParser")) {
                
                logger.error("CORRUPTED PDF detected: File '{}' appears to be corrupted or incomplete. " +
                           "Error: Missing root object specification in trailer. " +
                           "This typically indicates the PDF file was not completely downloaded or is malformed. " +
                           "Consider re-downloading the file or checking the source.", fileName);
                logger.debug("Full PDF corruption error details for file '{}':", fileName, e);
                
            } else if (errorMessage.contains("TikaException") || errorMessage.contains("org.apache.tika")) {
                logger.error("TIKA PARSING ERROR: File '{}' could not be parsed by Apache Tika. " +
                           "This may indicate an unsupported file format, corruption, or parsing limitations. " +
                           "Error: {}", fileName, errorMessage);
                logger.debug("Full Tika parsing error details for file '{}':", fileName, e);
                
            } else if (errorMessage.contains("IOException")) {
                logger.error("FILE I/O ERROR: Unable to read file '{}'. " +
                           "This may indicate file system issues, permission problems, or file corruption. " +
                           "Error: {}", fileName, errorMessage);
                logger.debug("Full I/O error details for file '{}':", fileName, e);
                
            } else if (errorMessage.contains("OutOfMemoryError") || errorMessage.contains("memory")) {
                logger.error("MEMORY ERROR: Insufficient memory to process file '{}'. " +
                           "Consider increasing heap size or processing smaller chunks. " +
                           "Error: {}", fileName, errorMessage);
                logger.debug("Full memory error details for file '{}':", fileName, e);
                
            } else {
                logger.error("UNKNOWN EXTRACTION ERROR: Unexpected error processing file '{}'. " +
                           "Error: {} - {}", fileName, e.getClass().getSimpleName(), errorMessage);
                logger.debug("Full unknown error details for file '{}':", fileName, e);
            }
        } else {
            logger.error("EXTRACTION ERROR: Error processing file '{}' - {}", fileName, e.getClass().getSimpleName());
            logger.debug("Full error details for file '{}':", fileName, e);
        }
        
        // Always return empty stream for graceful degradation
        return Stream.empty();
    }

    /**
     * Writes extracted text to a temporary file for UI display.
     * Files are stored in /tmp with a standardized naming convention.
     *
     * @param filename The original filename
     * @param extractedText The extracted text content
     * @return The path to the created temporary file
     * @throws IOException If an I/O error occurs
     */
    public Path writeExtractedTextToTempFile(String filename, String extractedText) throws IOException {
        // Create a safe filename for the temp file
        String safeFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path tempFile = Paths.get("/tmp", "textproc_" + safeFilename + "_extracted.txt");
        
        // Write the extracted text to the temp file
        Files.writeString(tempFile, extractedText, StandardCharsets.UTF_8);
        
        logger.info("Wrote extracted text to temporary file: {} ({} characters)", 
                   tempFile, extractedText.length());
        
        return tempFile;
    }
    
    /**
     * Writes chunked text to a temporary file for UI display.
     * Files are stored in /tmp with a standardized naming convention.
     *
     * @param filename The original filename
     * @param chunks The text chunks
     * @return The path to the created temporary file
     * @throws IOException If an I/O error occurs
     */
    public Path writeChunkedTextToTempFile(String filename, List<String> chunks) throws IOException {
        // Create a safe filename for the temp file
        String safeFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path tempFile = Paths.get("/tmp", "textproc_" + safeFilename + "_chunked.txt");
        
        // Combine chunks with separators
        StringBuilder combinedText = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            combinedText.append("=== CHUNK ").append(i + 1).append(" OF ").append(chunks.size()).append(" ===\n");
            combinedText.append(chunks.get(i));
            combinedText.append("\n\n");
        }
        
        // Write the combined text to the temp file
        Files.writeString(tempFile, combinedText.toString(), StandardCharsets.UTF_8);
        
        logger.info("Wrote chunked text to temporary file: {} ({} chunks, {} total characters)", 
                   tempFile, chunks.size(), combinedText.length());
        
        return tempFile;
    }
}
