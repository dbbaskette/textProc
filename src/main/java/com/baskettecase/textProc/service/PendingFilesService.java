package com.baskettecase.textProc.service;

import com.baskettecase.textProc.config.ProcessorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PendingFilesService {
    private static final Logger logger = LoggerFactory.getLogger(PendingFilesService.class);

    private final ProcessorProperties processorProperties;

    public PendingFilesService(ProcessorProperties processorProperties) {
        this.processorProperties = processorProperties;
    }

    public Map<String, Object> getPendingItems() {
        String mode = processorProperties.getMode();
        if ("standalone".equalsIgnoreCase(mode)) {
            return getStandalonePending();
        } else if ("scdf".equalsIgnoreCase(mode)) {
            return getScdfQueueDepth();
        }
        return Map.of("mode", mode, "status", "unsupported");
    }

    private Map<String, Object> getStandalonePending() {
        String dir = processorProperties.getStandalone().getInputDirectory();
        List<String> files = new ArrayList<>();
        try {
            Files.list(Paths.get(dir))
                    .filter(Files::isRegularFile)
                    .forEach(p -> files.add(p.getFileName().toString()));
        } catch (IOException e) {
            logger.warn("Failed to list standalone input directory {}: {}", dir, e.getMessage());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("mode", "standalone");
        result.put("directory", dir);
        result.put("count", files.size());
        result.put("files", files);
        return result;
    }

    private Map<String, Object> getScdfQueueDepth() {
        // Optional RabbitMQ Management API integration via env vars
        String mgmtUrl = getenv("RABBITMQ_MGMT_URL");
        String user = getenv("RABBITMQ_USERNAME");
        String pass = getenv("RABBITMQ_PASSWORD");

        if (mgmtUrl == null || user == null || pass == null) {
            return Map.of(
                    "mode", "scdf",
                    "queueDepth", -1,
                    "status", "unavailable",
                    "reason", "RabbitMQ management not configured (set RABBITMQ_MGMT_URL, RABBITMQ_USERNAME, RABBITMQ_PASSWORD)"
            );
        }

        try {
            URL url = java.net.URI.create(mgmtUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            String basic = java.util.Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic " + basic);
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);

            int code = conn.getResponseCode();
            if (code == 200) {
                String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                // Expect either direct queue object or an array; try to find messages count
                int depth = extractQueueDepth(json);
                return Map.of("mode", "scdf", "queueDepth", depth, "status", "ok");
            }
            return Map.of("mode", "scdf", "queueDepth", -1, "status", "error", "httpCode", code);
        } catch (Exception e) {
            logger.warn("RabbitMQ management query failed: {}", e.getMessage());
            return Map.of("mode", "scdf", "queueDepth", -1, "status", "error", "reason", e.getMessage());
        }
    }

    private static int extractQueueDepth(String json) {
        // Very small dependency-free extraction: look for "messages": <number>
        try {
            String key = "\"messages\"";
            int idx = json.indexOf(key);
            if (idx >= 0) {
                int colon = json.indexOf(':', idx + key.length());
                if (colon > 0) {
                    int end = colon + 1;
                    while (end < json.length() && Character.isWhitespace(json.charAt(end))) end++;
                    int start = end;
                    while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
                    return Integer.parseInt(json.substring(start, end));
                }
            }
        } catch (Exception ignore) {
        }
        return -1;
    }

    private static String getenv(String name) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? null : v;
    }
}


