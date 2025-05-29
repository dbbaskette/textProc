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
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

        // Create a temporary file with a meaningful prefix/suffix
        Path tempFile = Files.createTempFile("hdfs-download-", ".dat");
        logger.debug("Created temporary file: {}", tempFile);

        try (InputStream in = java.net.URI.create(url).toURL().openStream()) {
            // Copy with progress logging
            long bytesCopied = Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Downloaded {} bytes to {}", bytesCopied, tempFile);
            return tempFile;
        } catch (IOException e) {
            // Clean up the temp file if there was an error
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException deleteEx) {
                logger.warn("Failed to delete temporary file after download error", deleteEx);
            }
            throw new IOException("Failed to download file from " + url, e);
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
                Files.deleteIfExists(tempFile);
                logger.debug("Deleted temporary file: {}", tempFile);
            } catch (IOException e) {
                logger.warn("Failed to delete temporary file: " + tempFile, e);
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

    @org.springframework.context.annotation.Bean
    @Profile("scdf")
    public Function<Message<String>, Message<byte[]>> textProc() {
        return inputMsg -> {
            String payload = inputMsg.getPayload();
            logger.debug("Received message: {}", payload);
            byte[] response;
            
            try {
                JsonNode root = objectMapper.readTree(payload);
                String type = root.path("type").asText("");
                String webhdfsUrl;

                switch (type.toUpperCase()) {
                    case "S3":
                        // For S3, we'll keep the existing behavior for now
                        String extractedText = processS3File(root);
                        logger.info("Extraction complete ({} chars)", extractedText.length());
                        response = extractedText.getBytes(StandardCharsets.UTF_8);
                        return MessageBuilder.withPayload(response)
                                .copyHeaders(inputMsg.getHeaders())
                                .build();
                        
                    case "HDFS":
                        webhdfsUrl = root.path("url").asText();
                        if (webhdfsUrl == null || webhdfsUrl.trim().isEmpty()) {
                            throw new IllegalArgumentException("Missing or empty 'url' field for HDFS type");
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
                        fileInfo.setFilename(fileKey);
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
                        
                        processedFiles.put(fileKey, true);
                        logger.info("Processing new file: {}", webhdfsUrl);
                        
                        // Process in chunks and send each chunk as a separate message
                        List<String> chunks = processHdfsFileInChunks(webhdfsUrl).collect(Collectors.toList());
                        
                        if (chunks.isEmpty()) {
                            logger.warn("No chunks were generated for file: {}", webhdfsUrl);
                            return MessageBuilder.withPayload(new byte[0])
                                    .copyHeaders(inputMsg.getHeaders())
                                    .build();
                        }
                        
                        // Update file info with processing results
                        fileInfo.setFileSize(Files.size(Path.of(webhdfsUrl)));
                        fileInfo.setChunkCount(chunks.size());
                        fileInfo.setStatus("COMPLETED");
                        fileInfo.setFileType(Files.probeContentType(Path.of(webhdfsUrl)));
                        fileProcessingService.addProcessedFile(fileInfo);
                        
                        // Log chunk information
                        logger.info("Processed {} chunks for file: {}", chunks.size(), webhdfsUrl);
                        for (int i = 0; i < chunks.size(); i++) {
                            logger.debug("Chunk {} size: {} characters", i, chunks.get(i).length());
                        }
                        
                        // Return the first chunk immediately
                        String firstChunk = chunks.get(0);
                        logger.info("Returning first chunk of size: {} characters", firstChunk.length());
                        response = firstChunk.getBytes(StandardCharsets.UTF_8);
                        
                        // Process remaining chunks asynchronously if there are any
                        if (chunks.size() > 1) {
                            CompletableFuture.runAsync(() -> {
                                try {
                                    for (int i = 1; i < chunks.size(); i++) {
                                        String chunk = chunks.get(i);
                                        logger.info("Processing chunk {} of size: {} characters", i, chunk.length());
                                        // Here you would send each chunk to the output channel
                                        // Example: outputChannel.send(MessageBuilder.withPayload(chunk.getBytes(StandardCharsets.UTF_8)).build());
                                    }
                                } catch (Exception e) {
                                    logger.error("Error processing remaining chunks", e);
                                }
                            });
                        }
                        
                        return MessageBuilder.withPayload(response)
                                .copyHeaders(inputMsg.getHeaders())
                                .build();
                        
                    default:
                        throw new IllegalArgumentException("Unsupported file source type: " + type);
                }
                
            } catch (Exception e) {
                logger.error("Error processing message: {}", e.getMessage(), e);
                String errorMessage = "Error: " + e.getMessage();
                return MessageBuilder.withPayload(errorMessage.getBytes(StandardCharsets.UTF_8))
                        .copyHeaders(inputMsg.getHeaders())
                        .build();
            }
        };
    }
}
    