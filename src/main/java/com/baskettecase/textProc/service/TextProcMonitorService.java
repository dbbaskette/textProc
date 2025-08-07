package com.baskettecase.textProc.service;

import com.baskettecase.textProc.config.ProcessorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    public TextProcMonitorService(MetricsPublisher metricsPublisher,
                                  ProcessingStateService processingStateService,
                                  ConsumerLifecycleService consumerLifecycleService,
                                  FileProcessingService fileProcessingService,
                                  ProcessorProperties processorProperties,
                                  HdfsService hdfsService,
                                  @Value("${spring.application.name:textProc}") String appName,
                                  @Value("${CF_INSTANCE_INDEX:${INSTANCE_ID:0}}") String instanceIndex) {
        this.metricsPublisher = metricsPublisher;
        this.processingStateService = processingStateService;
        this.consumerLifecycleService = consumerLifecycleService;
        this.fileProcessingService = fileProcessingService;
        this.processorProperties = processorProperties;
        this.hdfsService = hdfsService;
        this.instanceId = appName + "-" + instanceIndex;
        this.startTime = Instant.now();
    }

    @Scheduled(fixedDelayString = "${app.monitoring.publish-interval-ms:5000}")
    public void publish() {
        try {
            metricsPublisher.publishMetrics(getMonitoringData());
        } catch (Exception e) {
            logger.debug("Metrics publish failed: {}", e.getMessage());
        }
    }

    public MonitoringData getMonitoringData() {
        int processedCount = fileProcessingService.getAllProcessedFiles().size();
        long uptimeSeconds = Duration.between(startTime, Instant.now()).toSeconds();
        double processingRate = uptimeSeconds > 0 ? processedCount / (double) uptimeSeconds : 0.0;
        String status = processingStateService.isProcessingEnabled() && consumerLifecycleService.areConsumersRunning() ? "PROCESSING" : "IDLE";

        MonitoringData.Meta meta = new MonitoringData.Meta(
                "textProc",
                processingStateService.getProcessingState(),
                consumerLifecycleService.areConsumersRunning() ? "running" : "stopped",
                hdfsService.processedFilesDirectoryExists(),
                processorProperties.getMode()
        );

        return new MonitoringData(
                instanceId,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
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
        private final Meta meta;

        public MonitoringData(String instanceId, String timestamp, long totalChunks, long processedChunks, long errorCount, double processingRate, String uptime, String status, String currentFile, long filesProcessed, long filesTotal, String lastError, long memoryUsedMB, long pendingMessages, Meta meta) {
            this.instanceId = instanceId;
            this.timestamp = timestamp;
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
            this.meta = meta;
        }

        public String getInstanceId() { return instanceId; }
        public String getTimestamp() { return timestamp; }
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
        public Meta getMeta() { return meta; }

        public static class Meta {
            private final String service;
            private final String processingState;
            private final String bindingState;
            private final boolean hdfsProcessedDirExists;
            private final String inputMode;

            public Meta(String service, String processingState, String bindingState, boolean hdfsProcessedDirExists, String inputMode) {
                this.service = service;
                this.processingState = processingState;
                this.bindingState = bindingState;
                this.hdfsProcessedDirExists = hdfsProcessedDirExists;
                this.inputMode = inputMode;
            }

            public String getService() { return service; }
            public String getProcessingState() { return processingState; }
            public String getBindingState() { return bindingState; }
            public boolean isHdfsProcessedDirExists() { return hdfsProcessedDirExists; }
            public String getInputMode() { return inputMode; }
        }
    }
}


