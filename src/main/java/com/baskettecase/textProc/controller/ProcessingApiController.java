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
        processingStateService.startProcessing();
        return Map.of(
                "status", "success",
                "message", "Processing started",
                "processingState", processingStateService.getProcessingState(),
                "consumerStatus", consumerLifecycleService.getConsumerStatus()
        );
    }

    @PostMapping("/processing/stop")
    public Map<String, Object> stopProcessing() {
        processingStateService.stopProcessing();
        return Map.of(
                "status", "success",
                "message", "Processing stopped",
                "processingState", processingStateService.getProcessingState(),
                "consumerStatus", consumerLifecycleService.getConsumerStatus()
        );
    }

    @PostMapping("/processing/reset")
    public Map<String, Object> resetProcessing() {
        processingStateService.stopProcessing();
        fileProcessingService.clearProcessedFiles();
        boolean hdfsCleared = hdfsService.deleteProcessedFilesDirectory();
        boolean directoryRecreated = hdfsService.createProcessedFilesDirectory();
        return Map.of(
                "status", "success",
                "message", "Reset completed",
                "processingState", processingStateService.getProcessingState(),
                "consumerStatus", consumerLifecycleService.getConsumerStatus(),
                "hdfsCleared", hdfsCleared,
                "directoryRecreated", directoryRecreated
        );
    }

    @GetMapping("/processing/state")
    public Map<String, Object> getProcessingState() {
        return Map.of(
                "processingState", processingStateService.getProcessingState(),
                "isProcessingEnabled", processingStateService.isProcessingEnabled(),
                "consumerStatus", consumerLifecycleService.getConsumerStatus()
        );
    }
}


