package com.baskettecase.textProc.controller;

import com.baskettecase.textProc.model.FileProcessingInfo;
import com.baskettecase.textProc.service.ConsumerLifecycleService;
import com.baskettecase.textProc.service.FileProcessingService;
import com.baskettecase.textProc.service.HdfsService;
import com.baskettecase.textProc.service.PendingFilesService;
import com.baskettecase.textProc.service.ProcessingStateService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProcessingApiController {

    private final FileProcessingService fileProcessingService;
    private final ProcessingStateService processingStateService;
    private final ConsumerLifecycleService consumerLifecycleService;
    private final HdfsService hdfsService;
    private final PendingFilesService pendingFilesService;

    public ProcessingApiController(
            FileProcessingService fileProcessingService,
            ProcessingStateService processingStateService,
            ConsumerLifecycleService consumerLifecycleService,
            HdfsService hdfsService,
            PendingFilesService pendingFilesService) {
        this.fileProcessingService = fileProcessingService;
        this.processingStateService = processingStateService;
        this.consumerLifecycleService = consumerLifecycleService;
        this.hdfsService = hdfsService;
        this.pendingFilesService = pendingFilesService;
    }

    /**
     * Helper method to map consumer status from internal values to standardized values.
     * @param currentStatus The current consumer status string
     * @return Standardized status value ("CONSUMING", "IDLE", or uppercase fallback)
     */
    private String mapConsumerStatus(String currentStatus) {
        if (currentStatus == null) {
            return "UNKNOWN";
        }
        // Map from textProc's current values to standardized values
        String lowerStatus = currentStatus.toLowerCase();
        if (lowerStatus.contains("running")) {
            return "CONSUMING";
        } else if (lowerStatus.contains("stopped")) {
            return "IDLE";
        } else {
            return currentStatus.toUpperCase(); // fallback
        }
    }

    @GetMapping("/files/processed")
    public List<FileProcessingInfo> listProcessedFiles() {
        return fileProcessingService.getAllProcessedFiles();
    }

    @GetMapping("/files/pending")
    public Map<String, Object> listPending() {
        return pendingFilesService.getPendingItems();
    }

    @PostMapping("/processing/start")
    public Map<String, Object> startProcessing() {
        boolean stateChanged = processingStateService.startProcessing();
        return Map.of(
                "success", true,
                "message", "Processing started successfully",
                "stateChanged", stateChanged,
                "enabled", true,
                "status", "STARTED",
                "consumerStatus", "CONSUMING",
                "lastChanged", processingStateService.getLastChanged().toString(),
                "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString()
        );
    }

    @PostMapping("/processing/stop")
    public Map<String, Object> stopProcessing() {
        boolean stateChanged = processingStateService.stopProcessing();
        return Map.of(
                "success", true,
                "message", "Processing stopped successfully. Messages will remain in queue.",
                "stateChanged", stateChanged,
                "enabled", false,
                "status", "STOPPED",
                "consumerStatus", "IDLE",
                "lastChanged", processingStateService.getLastChanged().toString(),
                "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString()
        );
    }

    @PostMapping("/processing/toggle")
    public Map<String, Object> toggleProcessing() {
        boolean wasEnabled = processingStateService.isProcessingEnabled();

        // Toggle the state
        boolean stateChanged;
        if (wasEnabled) {
            stateChanged = processingStateService.stopProcessing();
        } else {
            stateChanged = processingStateService.startProcessing();
        }

        boolean currentEnabled = processingStateService.isProcessingEnabled();
        String action = currentEnabled ? "started" : "stopped";

        return Map.of(
                "success", true,
                "message", String.format("Processing %s successfully. Previous state: %s, Current state: %s. %s",
                    action,
                    wasEnabled ? "enabled" : "disabled",
                    currentEnabled ? "enabled" : "disabled",
                    currentEnabled ? "Now consuming messages from queue." : "Messages will remain in queue."),
                "action", action,
                "previousState", Map.of(
                    "enabled", wasEnabled,
                    "status", wasEnabled ? "STARTED" : "STOPPED"
                ),
                "currentState", Map.of(
                    "enabled", currentEnabled,
                    "status", currentEnabled ? "STARTED" : "STOPPED",
                    "consumerStatus", currentEnabled ? "CONSUMING" : "IDLE"
                ),
                "lastChanged", processingStateService.getLastChanged().toString(),
                "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString()
        );
    }

    @PostMapping("/processing/reset")
    public Map<String, Object> resetProcessing() {
        processingStateService.stopProcessing();
        fileProcessingService.clearProcessedFiles();
        boolean hdfsCleared = hdfsService.deleteProcessedFilesDirectory();
        boolean directoryRecreated = hdfsService.createProcessedFilesDirectory();
        return Map.of(
                "success", true,
                "message", "Reset completed successfully. Processing stopped and files cleared.",
                "stateChanged", true, // Reset always changes state
                "enabled", false,
                "status", "STOPPED",
                "consumerStatus", "IDLE",
                "hdfsCleared", hdfsCleared,
                "directoryRecreated", directoryRecreated,
                "lastChanged", processingStateService.getLastChanged().toString(),
                "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString()
        );
    }

    @GetMapping("/processing/state")
    public Map<String, Object> getProcessingState() {
        return Map.of(
                "enabled", processingStateService.isProcessingEnabled(),
                "status", processingStateService.getProcessingState(),
                "consumerStatus", mapConsumerStatus(consumerLifecycleService.getConsumerStatus()),
                "lastChanged", processingStateService.getLastChanged().toString(),
                "lastChangeReason", processingStateService.getLastChangeReason(),
                "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString()
        );
    }
}


