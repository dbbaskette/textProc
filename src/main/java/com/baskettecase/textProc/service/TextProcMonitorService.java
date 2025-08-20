package com.baskettecase.textProc.service;

import com.baskettecase.textProc.config.ProcessorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;

@Service
@EnableScheduling
@Profile({"standalone","scdf","default"})
public class TextProcMonitorService {
    private static final Logger logger = LoggerFactory.getLogger(TextProcMonitorService.class);

    private final MetricsPublisher metricsPublisher;
    private final ProcessingStateService processingStateService;
    private final ConsumerLifecycleService consumerLifecycleService;
    private final FileProcessingService fileProcessingService;
    private final ProcessorProperties processorProperties;
    private final HdfsService hdfsService;
    private final String instanceId;
    private final Instant startTime;
    private final String hostname;
    private final String publicHostname;
    private final String url;
    private final String internalUrl;
    private final String publicUrl;
    private final Integer port;
    private final long bootEpoch;
    private final String version;
    private final Map<String, String> tags;

    public TextProcMonitorService(MetricsPublisher metricsPublisher,
                                  ProcessingStateService processingStateService,
                                  ConsumerLifecycleService consumerLifecycleService,
                                  FileProcessingService fileProcessingService,
                                  ProcessorProperties processorProperties,
                                  HdfsService hdfsService,
                                  @Value("${spring.application.name:textProc}") String appName,
                                  @Value("${CF_INSTANCE_INDEX:${INSTANCE_ID:}}") String instanceIndex,
                                  @Value("${app.monitoring.instance-id:}") String instanceIdOverride) {
        this.metricsPublisher = metricsPublisher;
        this.processingStateService = processingStateService;
        this.consumerLifecycleService = consumerLifecycleService;
        this.fileProcessingService = fileProcessingService;
        this.processorProperties = processorProperties;
        this.hdfsService = hdfsService;
        this.hostname = resolveHostname();
        this.instanceId = resolveInstanceId(appName, instanceIndex, this.hostname, instanceIdOverride);
        this.startTime = Instant.now();
        this.publicHostname = resolvePublicHostname(this.hostname);
        this.port = resolvePort();
        this.internalUrl = resolveInternalUrl(this.hostname, this.port);
        this.publicUrl = resolvePublicUrl(this.publicHostname, this.port);
        this.url = this.publicUrl; // Use publicUrl as the main url
        this.bootEpoch = this.startTime.toEpochMilli();
        this.version = resolveVersion();
        this.tags = Collections.emptyMap();
    }

    @Scheduled(fixedDelayString = "#{${app.monitoring.emit-interval-seconds:10} * 1000}")
    public void publish() {
        try {
            metricsPublisher.publishMetrics(getMonitoringData("HEARTBEAT", null, "RUNNING", deriveStageForHeartbeat()));
        } catch (Exception e) {
            logger.debug("Metrics publish failed: {}", e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publishInit() {
        try {
            String status = "STARTING";
            MonitoringData.Meta meta = new MonitoringData.Meta(
                    "textProc",
                    consumerLifecycleService.areConsumersRunning() ? "running" : "stopped",
                    hdfsService.processedFilesDirectoryExists(),
                    processorProperties.getMode(),
                    "starting",
                    tags
            );

            MonitoringData initData = new MonitoringData(
                    instanceId,
                    OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    "INIT",
                    0,
                    0,
                    0,
                    0.0,
                    "0s",
                    status,
                    null,
                    0,
                    -1,
                    null,
                    getMemoryUsedMB(),
                    -1,
                    hostname,
                    publicHostname,
                    null,
                    url,
                    internalUrl,
                    publicUrl,
                    port,
                    bootEpoch,
                    version,
                    meta
            );
            metricsPublisher.publishMetrics(initData);
        } catch (Exception e) {
            logger.debug("Init metrics publish failed: {}", e.getMessage());
        }
    }

    public void publishEvent(String event, String filename) {
        try {
            String status = "PROCESSING".equalsIgnoreCase(event) || "FILE_START".equalsIgnoreCase(event) || "FILE_COMPLETE".equalsIgnoreCase(event)
                    ? "PROCESSING" : computeStatus();
            String stage = ("FILE_START".equalsIgnoreCase(event) || "FILE_COMPLETE".equalsIgnoreCase(event)) ? "processing" : deriveStageForHeartbeat();
            metricsPublisher.publishMetrics(getMonitoringData(event, filename, status, stage));
        } catch (Exception e) {
            logger.debug("Event metrics publish failed: {}", e.getMessage());
        }
    }

    public MonitoringData getMonitoringData(String event, String filename, String status, String processingStage) {
        int processedCount = fileProcessingService.getAllProcessedFiles().size();
        long uptimeSeconds = Duration.between(startTime, Instant.now()).toSeconds();
        double processingRate = uptimeSeconds > 0 ? processedCount / (double) uptimeSeconds : 0.0;

        MonitoringData.Meta meta = new MonitoringData.Meta(
                "textProc",
                consumerLifecycleService.areConsumersRunning() ? "running" : "stopped",
                hdfsService.processedFilesDirectoryExists(),
                processorProperties.getMode(),
                processingStage,
                tags
        );

        return new MonitoringData(
                instanceId,
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                event,
                0,
                0,
                0,
                processingRate,
                formatUptime(),
                status,
                null,
                processedCount,
                -1,
                null,
                getMemoryUsedMB(),
                -1,
                hostname,
                publicHostname,
                filename,
                url,
                internalUrl,
                publicUrl,
                port,
                bootEpoch,
                version,
                meta
        );
    }

    private String formatUptime() {
        Duration uptime = Duration.between(startTime, Instant.now());
        long hours = uptime.toHours();
        long minutes = uptime.toMinutesPart();
        return String.format("%dh %dm", hours, minutes);
    }

    private long getMemoryUsedMB() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        return (totalMemory - freeMemory) / (1024 * 1024);
    }

    public static class MonitoringData {
        private final String instanceId;
        private final String timestamp;
        private final String event;
        private final long totalChunks;
        private final long processedChunks;
        private final long errorCount;
        private final double processingRate;
        private final String uptime;
        private final String status;
        private final String currentFile;
        private final long filesProcessed;
        private final long filesTotal;
        private final String lastError;
        private final long memoryUsedMB;
        private final long pendingMessages;
        private final String hostname;
        private final String publicHostname;
        private final String filename;
        private final String url;
        private final String internalUrl;
        private final String publicUrl;
        private final Integer port;
        private final long bootEpoch;
        private final String version;
        private final Meta meta;

        public MonitoringData(String instanceId, String timestamp, String event, long totalChunks, long processedChunks, long errorCount, double processingRate, String uptime, String status, String currentFile, long filesProcessed, long filesTotal, String lastError, long memoryUsedMB, long pendingMessages, String hostname, String publicHostname, String filename, String url, String internalUrl, String publicUrl, Integer port, long bootEpoch, String version, Meta meta) {
            this.instanceId = instanceId;
            this.timestamp = timestamp;
            this.event = event;
            this.totalChunks = totalChunks;
            this.processedChunks = processedChunks;
            this.errorCount = errorCount;
            this.processingRate = processingRate;
            this.uptime = uptime;
            this.status = status;
            this.currentFile = currentFile;
            this.filesProcessed = filesProcessed;
            this.filesTotal = filesTotal;
            this.lastError = lastError;
            this.memoryUsedMB = memoryUsedMB;
            this.pendingMessages = pendingMessages;
            this.hostname = hostname;
            this.publicHostname = publicHostname;
            this.filename = filename;
            this.url = url;
            this.internalUrl = internalUrl;
            this.publicUrl = publicUrl;
            this.port = port;
            this.bootEpoch = bootEpoch;
            this.version = version;
            this.meta = meta;
        }

        public String getInstanceId() { return instanceId; }
        public String getTimestamp() { return timestamp; }
        public String getEvent() { return event; }
        public long getTotalChunks() { return totalChunks; }
        public long getProcessedChunks() { return processedChunks; }
        public long getErrorCount() { return errorCount; }
        public double getProcessingRate() { return processingRate; }
        public String getUptime() { return uptime; }
        public String getStatus() { return status; }
        public String getCurrentFile() { return currentFile; }
        public long getFilesProcessed() { return filesProcessed; }
        public long getFilesTotal() { return filesTotal; }
        public String getLastError() { return lastError; }
        public long getMemoryUsedMB() { return memoryUsedMB; }
        public long getPendingMessages() { return pendingMessages; }
        public String getHostname() { return hostname; }
        public String getPublicHostname() { return publicHostname; }
        public String getUrl() { return url; }
        public String getInternalUrl() { return internalUrl; }
        public String getPublicUrl() { return publicUrl; }
        public Integer getPort() { return port; }
        public String getFilename() { return filename; }
        public long getBootEpoch() { return bootEpoch; }
        public String getVersion() { return version; }
        public Meta getMeta() { return meta; }

        public static class Meta {
            private final String service;
            private final String bindingState;
            private final boolean hdfsProcessedDirExists;
            private final String inputMode;
            private final String processingStage;
            private final Map<String, String> tags;

            public Meta(String service, String bindingState, boolean hdfsProcessedDirExists, String inputMode, String processingStage, Map<String, String> tags) {
                this.service = service;
                this.bindingState = bindingState;
                this.hdfsProcessedDirExists = hdfsProcessedDirExists;
                this.inputMode = inputMode;
                this.processingStage = processingStage;
                this.tags = tags == null ? Collections.emptyMap() : tags;
            }

            public String getService() { return service; }
            public String getBindingState() { return bindingState; }
            public boolean isHdfsProcessedDirExists() { return hdfsProcessedDirExists; }
            public String getInputMode() { return inputMode; }
            public String getProcessingStage() { return processingStage; }
            public Map<String, String> getTags() { return tags; }
        }
    }

    public String getInstanceId() { return instanceId; }
    public String getHostname() { return hostname; }
    public String getPublicHostname() { return publicHostname; }

    private static String resolveHostname() {
        try {
            String env = System.getenv("HOSTNAME");
            if (env != null && !env.isBlank()) return env;
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String resolvePublicHostname(String fallback) {
        // 1. Check for explicit override
        String env = System.getenv("PUBLIC_HOSTNAME");
        if (env != null && !env.isBlank()) return env;
        
        // 2. Try to extract from VCAP_APPLICATION (Cloud Foundry) - same as hdfsWatcher
        String vcapHostname = extractHostnameFromVcap();
        if (vcapHostname != null) return vcapHostname;
        
        return fallback;
    }
    
    private static String extractHostnameFromVcap() {
        String vcapApplicationJson = System.getenv("VCAP_APPLICATION");
        if (vcapApplicationJson != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>> typeRef = 
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {};
                java.util.Map<String, Object> vcapMap = mapper.readValue(vcapApplicationJson, typeRef);
                @SuppressWarnings("unchecked")
                java.util.List<String> uris = (java.util.List<String>) vcapMap.get("application_uris");
                if (uris != null && !uris.isEmpty()) {
                    return uris.get(0).toLowerCase();
                }
            } catch (Exception e) {
                // Fall through to default if parsing fails
            }
        }
        return null;
    }

    private static Integer resolvePort() {
        String port = System.getenv().getOrDefault("PORT", "8080");
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException e) {
            return 8080;
        }
    }

    private static String resolveInternalUrl(String hostname, Integer port) {
        return "http://" + hostname + ":" + port;
    }

    private static String resolvePublicUrl(String publicHostname, Integer port) {
        String env = System.getenv("PUBLIC_URL");
        if (env != null && !env.isBlank()) return env;
        
        // Use HTTPS for public hostname (same as hdfsWatcher)
        if (publicHostname != null && !publicHostname.isBlank()) {
            return "https://" + publicHostname;
        }
        
        // Fallback to localhost with port
        return "http://localhost:" + port;
    }

    private static String resolveUrl(String publicHost, String host) {
        String env = System.getenv("PUBLIC_URL");
        if (env != null && !env.isBlank()) return env;
        String targetHost = (publicHost != null && !publicHost.isBlank()) ? publicHost : host;
        String port = System.getenv().getOrDefault("PORT", "8080");
        return "http://" + targetHost + ":" + port;
    }

    private static String resolveVersion() {
        try {
            String impl = TextProcMonitorService.class.getPackage().getImplementationVersion();
            if (impl != null && !impl.isBlank()) return impl;
        } catch (Exception ignored) {}
        try (java.io.InputStream in = TextProcMonitorService.class.getResourceAsStream("/VERSION")) {
            if (in != null) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            }
        } catch (Exception ignored) {}
        String env = System.getenv("APP_VERSION");
        return (env != null && !env.isBlank()) ? env : "unknown";
    }

    private String computeStatus() {
        boolean enabled = processingStateService.isProcessingEnabled();
        boolean running = consumerLifecycleService.areConsumersRunning();
        if (!enabled) return "IDLE";
        return running ? "RUNNING" : "IDLE";
    }

    private String deriveStageForHeartbeat() {
        boolean enabled = processingStateService.isProcessingEnabled();
        boolean running = consumerLifecycleService.areConsumersRunning();
        if (!enabled) return "idle";
        return running ? "processing" : "idle";
    }

    private static String resolveInstanceId(String appName, String instanceIndex, String host, String override) {
        if (override != null && !override.isBlank()) return override;
        if (instanceIndex != null && !instanceIndex.isBlank()) {
            return appName + "-" + instanceIndex;
        }
        long pid = -1L;
        try { pid = java.lang.ProcessHandle.current().pid(); } catch (Throwable ignored) {}
        return appName + "-" + (pid > 0 ? pid : "unknown") + "@" + host;
    }
}


