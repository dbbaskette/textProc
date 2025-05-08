package com.baskettecase.textProc.processor;

import com.baskettecase.textProc.service.ExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import io.minio.MinioClient;
import io.minio.GetObjectArgs;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Function;

/**
 * SCDF Stream Processor for "scdf" profile. Receives S3 object references, downloads files, extracts text, and outputs results.
 */
@Component
@Profile("scdf")
public class ScdfStreamProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ScdfStreamProcessor.class);
    private final ExtractionService extractionService;
    private final MinioClient minioClient;

    public ScdfStreamProcessor(ExtractionService extractionService) {
        this.extractionService = extractionService;

        // Read MinIO config from environment variables
        String endpoint = System.getenv().getOrDefault("S3_ENDPOINT", "http://localhost:9000");
        String accessKey = System.getenv("S3_ACCESS_KEY");
        String secretKey = System.getenv("S3_SECRET_KEY");
        logger.atDebug().log("MinIO config: endpoint={}, accessKey={}, secretKey={}", endpoint, accessKey, secretKey);
        if (accessKey == null || secretKey == null) {
            throw new IllegalStateException("S3_ACCESS_KEY and S3_SECRET_KEY environment variables must be set");
        }

        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * Spring Cloud Stream Function: receives S3 reference as input, outputs extracted text.
     * Input message payload should be 'bucket/filename' (e.g., mybucket/myfile.pdf)
     */
    @Profile("scdf")
    @Bean
    public Function<Message<String>, Message<String>> textProc() {
        logger.atDebug().log("SCDF mode activated in FUNCTION");
        return inputMsg -> {
            String payload = inputMsg.getPayload();
            String bucket;
            String key;
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            try {
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(payload);
                bucket = root.get("bucketName").asText();
                key = root.get("key").asText();
            } catch (Exception e) {
                logger.error("Failed to parse bucketName/key from JSON message: {}", payload, e);
                return MessageBuilder.withPayload("").build();
            }
            logger.info("Processing S3 object: {}/{}", bucket, key);

            // Download object from MinIO to /tmp/<bucketName>/<key>
            Path bucketDir = Path.of("/tmp", bucket);
            Path tempFile;
            try {
                Files.createDirectories(bucketDir);
                tempFile = bucketDir.resolve(key);
                try (var stream = minioClient.getObject(
                        GetObjectArgs.builder().bucket(bucket).object(key).build())) {
                    Files.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (io.minio.errors.ErrorResponseException |
                     io.minio.errors.InsufficientDataException |
                     io.minio.errors.InternalException |
                     io.minio.errors.InvalidResponseException |
                     io.minio.errors.ServerException |
                     io.minio.errors.XmlParserException |
                     java.security.InvalidKeyException |
                     java.security.NoSuchAlgorithmException |
                     IOException e) {
                logger.error("Error downloading MinIO object: {}/{}", bucket, key, e);
                return MessageBuilder.withPayload("").build();
            }

            // Extract text
            String extractedText;
            try {
                extractedText = extractionService.extractTextFromFile(tempFile);
            } catch (IOException e) {
                logger.error("Error extracting text from file {}: {}", tempFile, e.getMessage(), e);
                return MessageBuilder.withPayload("").build();
            } finally {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }

            logger.info("Extraction complete for {}/{} ({} chars)", bucket, key, extractedText.length());
            return MessageBuilder.withPayload(extractedText).build();
        };
    }
}
    