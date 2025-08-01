package com.baskettecase.textProc.processor;

import com.baskettecase.textProc.config.FileProcessingProperties;
import com.baskettecase.textProc.model.FileProcessingInfo;
import com.baskettecase.textProc.service.ExtractionService;
import com.baskettecase.textProc.service.FileProcessingService;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.minio.MinioClient;
import io.minio.GetObjectArgs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Spring Cloud Data Flow (SCDF) Stream Processor.
 * <p>
 * This component is activated under the 'scdf' Spring profile and is designed to work as a message-driven processor for SCDF pipelines.
 * It listens for incoming messages (typically from RabbitMQ), expects a JSON payload with S3/MinIO object references,
 * downloads the referenced file, extracts its text using Apache Tika, and outputs the result.
 * <p>
 * Environment Variables:
 * <ul>
 *   <li><b>S3_ENDPOINT</b>: URL for MinIO/S3 (default: http://localhost:9000)</li>
 *   <li><b>S3_ACCESS_KEY</b>: Access key for MinIO/S3 (required)</li>
 *   <li><b>S3_SECRET_KEY</b>: Secret key for MinIO/S3 (required)</li>
 * </ul>
 *
 * Message Structure:
 * <ul>
 *   <li>Input: JSON with fields <code>bucketName</code> and <code>key</code> (object name)</li>
 *   <li>Output: Extracted text as message payload</li>
 * </ul>
 *
 * Error Handling:
 * <ul>
 *   <li>Logs and returns empty payload on JSON parse, download, or extraction errors</li>
 *   <li>Deletes temporary files after processing</li>
 * </ul>
 *
 * See {@link ExtractionService} for extraction logic.
 */
@Component
@Profile("scdf")
public class ScdfStreamProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ScdfStreamProcessor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final ExtractionService extractionService;
    private final FileProcessingService fileProcessingService;
    @SuppressWarnings("unused")
    private final FileProcessingProperties fileProcessingProperties;
    private MinioClient minioClient = null;
    private final Map<String, Boolean> processedFiles = new ConcurrentHashMap<>();
    
    @Autowired
    public ScdfStreamProcessor(ExtractionService extractionService, 
                             FileProcessingService fileProcessingService, 
                             FileProcessingProperties fileProcessingProperties) {
        this.extractionService = extractionService;
        this.fileProcessingService = fileProcessingService;
        this.fileProcessingProperties = fileProcessingProperties;
    }

    public ScdfStreamProcessor(ExtractionService extractionService, FileProcessingService fileProcessingService) {
        this(extractionService, fileProcessingService, new FileProcessingProperties());
    }

    private String getFileKey(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to URL if hashing fails (shouldn't happen with SHA-256)
            return url;
        }
    }

    private MinioClient getMinioClient() {
        if (minioClient == null) {
            String endpoint = System.getenv().getOrDefault("S3_ENDPOINT", "http://localhost:9000");
            String accessKey = System.getenv("S3_ACCESS_KEY");
            String secretKey = System.getenv("S3_SECRET_KEY");
            logger.atDebug().log("MinIO config: endpoint={}, accessKey={}, secretKey={}", endpoint, accessKey, "***");
            if (accessKey == null || secretKey == null) {
                throw new IllegalStateException("S3_ACCESS_KEY and S3_SECRET_KEY environment variables must be set");
            }
            minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
        }
        return minioClient;
    }
    
    private String processS3File(JsonNode root) throws Exception {
        String bucket = root.get("bucket").asText();
        String key = root.get("key").asText();
        logger.info("Processing S3 object: {}/{}", bucket, key);
        
        java.nio.file.Path tempFile = Files.createTempFile("s3-", "-" + key);
        try (InputStream stream = getMinioClient().getObject(
                GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            Files.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            return extractionService.extractTextFromFile(tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    private Path downloadHdfsFile(String webhdfsUrl) throws IOException {
        logger.info("Downloading WebHDFS file: {}", webhdfsUrl);
    
        // Ensure the URL has the op=OPEN parameter
        String url = webhdfsUrl;
        if (!url.contains("op=OPEN")) {
            url += (url.contains("?") ? "&" : "?") + "op=OPEN";
        }
    
        // Defensive: ensure the filename portion is encoded only once
        int lastSlash = url.lastIndexOf('/');
        int queryIdx = url.indexOf('?', lastSlash);
        String filename = (queryIdx != -1) ? url.substring(lastSlash + 1, queryIdx) : url.substring(lastSlash + 1);
        String encodedFilename;
        if (filename.contains("%")) {
            // Already encoded, use as-is
            encodedFilename = filename;
        } else {
            // Not encoded, encode it
            encodedFilename = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8.name());
        }
        String rebuiltUrl = url.substring(0, lastSlash + 1) + encodedFilename + (queryIdx != -1 ? url.substring(queryIdx) : "");
    
        // Decode URL-encoded characters in the filename for local storage
        try {
            filename = java.net.URLDecoder.decode(filename, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            logger.warn("Failed to decode filename: " + filename, e);
            // Continue with the original filename if decoding fails
        }
    
        // Create a temporary file with the original filename in a temp directory
        Path tempDir = Files.createTempDirectory("hdfs-downloads");
        Path tempFile = tempDir.resolve(filename);
    
        logger.debug("Created temporary file: {}", tempFile);
    
        try (InputStream in = java.net.URI.create(rebuiltUrl).toURL().openStream()) {
            // Copy with progress logging
            long bytesCopied = Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Downloaded {} bytes to {}", bytesCopied, tempFile);
            return tempFile;
        } catch (IOException e) {
            // Clean up the temp file and directory if there was an error
            try {
                Files.deleteIfExists(tempFile);
                Files.deleteIfExists(tempDir);
            } catch (IOException deleteEx) {
                logger.warn("Failed to clean up temporary files after download error", deleteEx);
            }
            throw new IOException("Failed to download file from " + rebuiltUrl, e);
        }
    }

    private static final int CHUNK_SIZE = 1024 * 256; // 256KB chunks
    
    private Stream<String> processHdfsFileInChunks(String webhdfsUrl) throws IOException {
        logger.info("Processing WebHDFS file in chunks: {}", webhdfsUrl);
        logger.info("Using chunk size: {} bytes ({} KB)", CHUNK_SIZE, CHUNK_SIZE / 1024);
        
        // Download the file first
        final Path tempFile = downloadHdfsFile(webhdfsUrl);
        
        try {
            // Get file size for logging
            long fileSize = Files.size(tempFile);
            long estimatedChunks = (fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE; // Ceiling division
            
            logger.info("Document size: {} bytes ({} MB), estimated chunks: {}", 
                      fileSize, 
                      String.format("%.2f", fileSize / (1024.0 * 1024.0)),
                      estimatedChunks);
            
            // Process the downloaded file in chunks
            return extractionService.extractTextInChunks(tempFile, CHUNK_SIZE)
                .onClose(() -> cleanupTempFile(tempFile));
        } catch (Exception e) {
            // Ensure temp file is cleaned up on error
            cleanupTempFile(tempFile);
            throw e;
        }
    }
    
    private void cleanupTempFile(Path tempFile) {
        if (tempFile != null) {
            try {
                // Delete the file
                Files.deleteIfExists(tempFile);
                logger.debug("Deleted temporary file: {}", tempFile);
                
                // Try to delete the parent directory if it's empty
                Path parentDir = tempFile.getParent();
                if (parentDir != null && parentDir.getFileName().toString().startsWith("hdfs-downloads")) {
                    try {
                        Files.deleteIfExists(parentDir);
                        logger.debug("Deleted temporary directory: {}", parentDir);
                    } catch (IOException e) {
                        // Ignore if directory is not empty
                        logger.trace("Could not delete directory (may not be empty): {}", parentDir);
                    }
                }
            } catch (IOException e) {
                logger.warn("Failed to delete temporary file: {}", tempFile, e);
            }
        }
    }
    
    /**
     * Processes an HDFS file by downloading and processing it in chunks.
     * Kept for backward compatibility.
     *
     * @param webhdfsUrl The URL of the HDFS file to process
     * @return The processed file content as a single string
     * @throws IOException if an I/O error occurs
     */
    @SuppressWarnings("unused")
    private String processHdfsFile(String webhdfsUrl) throws IOException {
        try (var chunks = processHdfsFileInChunks(webhdfsUrl)) {
            // Combine all chunks into a single string (use with caution for large files)
            return chunks.collect(Collectors.joining());
        }
    }

    /**
     * Writes processed text to HDFS using WebHDFS REST API.
     * 
     * @param hdfsBaseUrl The base WebHDFS URL (e.g., http://namenode:50070/webhdfs/v1)
     * @param filename The filename to write
     * @param content The text content to write
     * @return The HDFS URL of the written file
     * @throws IOException if an I/O error occurs
     */
    private String writeProcessedFileToHdfs(String hdfsBaseUrl, String filename, String content) throws IOException {
        // Extract the base HDFS URL from the input file URL
        String baseUrl = hdfsBaseUrl;
        if (baseUrl.contains("/webhdfs/v1/")) {
            baseUrl = baseUrl.substring(0, baseUrl.indexOf("/webhdfs/v1/") + "/webhdfs/v1".length());
        }
        
        // URL encode the filename to handle spaces and special characters
        String encodedFilename = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8.name());
        
        // Create the processed files directory path with encoded filename
        String processedFilePath = "/processed_files/" + encodedFilename + ".txt";
        
        // Build the WebHDFS CREATE URL
        String createUrl = baseUrl + processedFilePath + "?op=CREATE&overwrite=true";
        
        logger.info("Writing processed file to HDFS: {}", createUrl);
        
        // Create HTTP connection
        java.net.URL url = new java.net.URL(createUrl);
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "text/plain");
        
        try (java.io.OutputStream os = connection.getOutputStream()) {
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            os.write(contentBytes);
            os.flush();
        }
        
        int responseCode = connection.getResponseCode();
        if (responseCode == 201) {
            logger.info("Successfully wrote processed file to HDFS: {}", processedFilePath);
            return baseUrl + processedFilePath;
        } else {
            String errorMessage = "Failed to write to HDFS. Response code: " + responseCode;
            try (java.io.InputStream errorStream = connection.getErrorStream()) {
                if (errorStream != null) {
                    errorMessage += ", Error: " + new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            throw new IOException(errorMessage);
        }
    }
    
    /**
     * Creates a message with the same format as the input message but pointing to the processed file.
     * 
     * @param processedFileUrl The URL of the processed file in HDFS
     * @param originalMessage The original message JSON node
     * @return JSON string with the same format but updated URL
     */
    private String createProcessedFileMessage(String processedFileUrl, JsonNode originalMessage) {
        try {
            // Create a new JSON object with the same structure
            com.fasterxml.jackson.databind.node.ObjectNode messageNode = objectMapper.createObjectNode();
            
            // Copy all fields from the original message
            messageNode.put("type", "HDFS");
            messageNode.put("url", processedFileUrl);
            
            // Copy optional fields if they exist
            if (originalMessage.has("inputStream")) {
                messageNode.put("inputStream", originalMessage.get("inputStream").asText());
            }
            if (originalMessage.has("outputStream")) {
                messageNode.put("outputStream", originalMessage.get("outputStream").asText());
            }
            
            // Add a flag to indicate this is a processed file
            messageNode.put("processed", true);
            messageNode.put("originalFile", originalMessage.get("url").asText());
            
            return objectMapper.writeValueAsString(messageNode);
        } catch (Exception e) {
            logger.error("Failed to create processed file message", e);
            // Fallback to simple JSON
            return "{\"type\":\"HDFS\",\"url\":\"" + processedFileUrl + "\",\"processed\":true}";
        }
    }

    @Bean
    public Function<Message<String>, Message<byte[]>> textProc() {
        return inputMsg -> {
            String payload = inputMsg.getPayload();
            MessageHeaders headers = inputMsg.getHeaders();
            logger.debug("Received message: {}", payload);
        
            try {
                JsonNode root = objectMapper.readTree(payload);
                String type = root.path("type").asText("");
                String webhdfsUrl;

                    switch (type.toUpperCase()) {
                        case "S3":
                            // For S3, we'll keep the existing behavior for now
                            String extractedText = processS3File(root);
                            logger.info("Extraction complete ({} chars)", extractedText.length());
                            return MessageBuilder.withPayload(extractedText.getBytes(StandardCharsets.UTF_8))
                                    .copyHeaders(inputMsg.getHeaders())
                                    .build();
                            
                        case "HDFS":
                            webhdfsUrl = root.path("url").asText();
                            if (webhdfsUrl == null || webhdfsUrl.trim().isEmpty()) {
                                throw new IllegalArgumentException("Missing or empty 'url' field for HDFS type");
                            }
                            
                            // Extract the filename from the URL
                            String filename = webhdfsUrl.substring(webhdfsUrl.lastIndexOf('/') + 1);
                            try {
                                filename = java.net.URLDecoder.decode(filename, StandardCharsets.UTF_8.name());
                            } catch (UnsupportedEncodingException e) {
                                logger.warn("Failed to decode filename: " + filename, e);
                            }
                            // Check if we've already processed this file
                            String fileKey = getFileKey(webhdfsUrl);
                            if (processedFiles.containsKey(fileKey)) {
                                logger.info("Skipping already processed file: {}", webhdfsUrl);
                                return MessageBuilder.withPayload(new byte[0])
                                        .copyHeaders(inputMsg.getHeaders())
                                        .build();
                            }
                            
                            // Track file processing
                            FileProcessingInfo fileInfo = new FileProcessingInfo();
                            fileInfo.setFilename(filename);
                            fileInfo.setProcessedAt(LocalDateTime.now());
                            fileInfo.setStatus("PROCESSING");
                            fileInfo.setChunkSize(CHUNK_SIZE);
                            fileInfo.setInputStream(root.path("inputStream").asText("default-input"));
                            fileInfo.setOutputStream(root.path("outputStream").asText("default-output"));
                            
                            // Set stream names in the service
                            fileProcessingService.setStreamNames(
                                fileInfo.getInputStream(),
                                fileInfo.getOutputStream()
                            );
                            
                            logger.info("Processing new file: {}", webhdfsUrl);
                            
                            // Download the file once and process it
                            Path tempFile = downloadHdfsFile(webhdfsUrl);
                            try {
                                // Extract the full text content
                                String processedText = extractionService.extractTextFromFile(tempFile);
                                
                                if (processedText == null || processedText.trim().isEmpty()) {
                                    logger.warn("No text was extracted from file: {}", webhdfsUrl);
                                    return MessageBuilder.withPayload(new byte[0])
                                            .copyHeaders(inputMsg.getHeaders())
                                            .build();
                                }
                                
                                // Update file info with processing results using the temporary file
                                fileInfo.setFileSize(Files.size(tempFile));
                                fileInfo.setChunkCount(1); // Single processed file
                                fileInfo.setStatus("COMPLETED");
                                fileInfo.setFileType(Files.probeContentType(tempFile));
                                fileProcessingService.addProcessedFile(fileInfo);
                                
                                // Log processing information
                                logger.info("Extracted {} characters from file: {}", processedText.length(), webhdfsUrl);
                                
                                // Write processed text to temp file for UI display
                                try {
                                    Path tempTextFile = extractionService.writeExtractedTextToTempFile(filename, processedText);
                                    logger.debug("Saved processed text for UI display: {}", tempTextFile);
                                } catch (Exception e) {
                                    logger.warn("Failed to write processed text to temp file for {}: {}", filename, e.getMessage());
                                }
                                
                                // Write the full processed text to HDFS
                                String processedFileUrl = writeProcessedFileToHdfs(webhdfsUrl, filename, processedText);
                                logger.info("Successfully wrote processed file to HDFS: {}", processedFileUrl);
                                
                                // Only mark as processed after successful completion
                                processedFiles.put(fileKey, true);
                                logger.info("Successfully processed and tracked file: {}", webhdfsUrl);
                                
                                // Create a message with the same format pointing to the processed file
                                String processedMessage = createProcessedFileMessage(processedFileUrl, root);
                                logger.debug("Sending processed file message to queue: {}", processedMessage);
                                            
                                // Return the JSON message using the preferred SCDF pattern
                                // This sends the processed file information in JSON format
                                return MessageBuilder.withPayload(processedMessage.getBytes(StandardCharsets.UTF_8))
                                        .copyHeaders(headers)
                                        .setHeader("originalFile", webhdfsUrl)
                                        .setHeader("processedFileUrl", processedFileUrl)
                                        .setHeader("extractedTextLength", processedText.length())
                                        .build();
                                
                            } finally {
                                // Clean up the temporary file after we're done with it
                                cleanupTempFile(tempFile);
                            }
                            
                        default:
                            throw new IllegalArgumentException("Unsupported file source type: " + type);
                    }
                    
                } catch (Exception e) {
                    logger.error("Error processing message", e);
                    throw new RuntimeException("Error processing message", e);
                }
        };
    }
}
    