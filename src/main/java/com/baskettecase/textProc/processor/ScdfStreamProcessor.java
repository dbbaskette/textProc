package com.baskettecase.textProc.processor;

import com.baskettecase.textProc.config.ProcessorProperties;
import com.baskettecase.textProc.service.ExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
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
    private final S3Client s3Client;

    public ScdfStreamProcessor(ExtractionService extractionService) {
        this.extractionService = extractionService;
        // Region can be made configurable via properties
        this.s3Client = S3Client.builder()
                .region(Region.US_EAST_1) // TODO: Make configurable
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Spring Cloud Stream Function: receives S3 reference as input, outputs extracted text.
     * Input message payload should be a JSON string: {"bucket":"...","key":"..."}
     */
    @org.springframework.context.annotation.Bean
    public Function<Message<String>, Message<String>> processS3File() {
        return inputMsg -> {
            String payload = inputMsg.getPayload();
            String bucket;
            String key;
            try {
                // Simple JSON parsing (replace with Jackson if desired)
                int bIdx = payload.indexOf("\"bucket\":");
                int kIdx = payload.indexOf("\"key\":");
                if (bIdx == -1 || kIdx == -1) throw new IllegalArgumentException("Invalid input JSON: " + payload);
                bucket = payload.split("\"bucket\":\"")[1].split("\"")[0];
                key = payload.split("\"key\":\"")[1].split("\"")[0];
            } catch (Exception e) {
                logger.error("Failed to parse S3 reference from message: {}", payload, e);
                return MessageBuilder.withPayload("").build();
            }
            logger.info("Processing S3 object: {}/{}", bucket, key);

            // Download S3 object to temp file
            Path tempFile;
            try {
                tempFile = Files.createTempFile("s3file-", "-input");
                GetObjectRequest req = GetObjectRequest.builder().bucket(bucket).key(key).build();
                try (ResponseInputStream<?> s3is = s3Client.getObject(req)) {
                    Files.copy(s3is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                logger.error("Error downloading S3 object: {}/{}", bucket, key, e);
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
