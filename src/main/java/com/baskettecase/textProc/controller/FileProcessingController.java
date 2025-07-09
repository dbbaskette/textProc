package com.baskettecase.textProc.controller;

import com.baskettecase.textProc.model.FileProcessingInfo;
import com.baskettecase.textProc.service.FileProcessingService;
import com.baskettecase.textProc.service.ProcessingStateService;
import com.baskettecase.textProc.service.HdfsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for handling file processing information web interface.
 * Provides endpoints to view processed files and stream information.
 */
@Controller
public class FileProcessingController {

    private final FileProcessingService fileProcessingService;
    private final ProcessingStateService processingStateService;
    private final HdfsService hdfsService;
    
    @Value("${app.hdfs.base-url:http://35.196.56.130:9870/webhdfs/v1}")
    private String hdfsBaseUrl;
    
    @Value("${app.hdfs.processed-files-path:/processed_files}")
    private String processedFilesPath;

    public FileProcessingController(FileProcessingService fileProcessingService, 
                                 ProcessingStateService processingStateService,
                                 HdfsService hdfsService) {
        this.fileProcessingService = fileProcessingService;
        this.processingStateService = processingStateService;
        this.hdfsService = hdfsService;
    }

    /**
     * Displays a modern dashboard with all processed files and their details.
     * @param model The model to add attributes to for the view.
     * @return The name of the Thymeleaf template to render.
     */
    @GetMapping({"/", "/files"})
    public String listProcessedFiles(Model model) {
        List<FileProcessingInfo> files = fileProcessingService.getAllProcessedFiles();
        
        // Calculate statistics
        long totalFiles = files.size();
        long totalSize = files.stream().mapToLong(FileProcessingInfo::getFileSize).sum();
        long totalChunks = files.stream().mapToLong(file -> file.getChunkCount()).sum();
        
        // Group files by status
        Map<String, Long> statusCounts = files.stream()
            .collect(Collectors.groupingBy(
                FileProcessingInfo::getStatus,
                Collectors.counting()
            ));
        
        // Group files by type
        Map<String, Long> typeCounts = files.stream()
            .collect(Collectors.groupingBy(
                FileProcessingInfo::getFileType,
                Collectors.counting()
            ));
        
        model.addAttribute("inputStream", fileProcessingService.getInputStreamName());
        model.addAttribute("outputStream", fileProcessingService.getOutputStreamName());
        model.addAttribute("files", files);
        model.addAttribute("totalFiles", totalFiles);
        model.addAttribute("totalSize", totalSize);
        model.addAttribute("totalChunks", totalChunks);
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("typeCounts", typeCounts);
        model.addAttribute("processingState", processingStateService.getProcessingState());
        
        return "files";
    }

    /**
     * API endpoint to get processing statistics for AJAX updates.
     * @return JSON response with processing statistics
     */
    @GetMapping("/api/stats")
    @ResponseBody
    public Map<String, Object> getProcessingStats() {
        List<FileProcessingInfo> files = fileProcessingService.getAllProcessedFiles();
        
        long totalFiles = files.size();
        long totalSize = files.stream().mapToLong(FileProcessingInfo::getFileSize).sum();
        long totalChunks = files.stream().mapToLong(file -> file.getChunkCount()).sum();
        
        Map<String, Long> statusCounts = files.stream()
            .collect(Collectors.groupingBy(
                FileProcessingInfo::getStatus,
                Collectors.counting()
            ));
        
        return Map.of(
            "totalFiles", totalFiles,
            "totalSize", totalSize,
            "totalChunks", totalChunks,
            "statusCounts", statusCounts
        );
    }

    /**
     * Serves processed text files for display in the browser.
     * Files are stored in HDFS with a standardized naming convention.
     * @param filename The safe filename to retrieve
     * @return The processed text content for display
     */
    @GetMapping("/processed-text/{filename}")
    public ResponseEntity<String> getProcessedText(@PathVariable String filename) {
        try {
            // URL encode the filename for HDFS path
            String encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8.name());
            
            // Construct the HDFS WebHDFS URL using configurable properties
            String hdfsUrl = hdfsBaseUrl + processedFilesPath + "/" + encodedFilename + ".txt?op=OPEN";
            
            // Create HTTP connection to read from HDFS
            java.net.URL url = new java.net.URL(hdfsUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // Read the content from HDFS
                try (java.io.InputStream inputStream = connection.getInputStream()) {
                    String content = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.TEXT_PLAIN);
                    headers.set("Cache-Control", "no-cache");
                    
                    return new ResponseEntity<>(content, headers, HttpStatus.OK);
                }
            } else {
                return new ResponseEntity<>("File not found in HDFS: " + filename + " (Response: " + responseCode + ")", HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            return new ResponseEntity<>("Error reading file from HDFS: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Starts file processing.
     * @return JSON response indicating the new processing state
     */
    @PostMapping("/api/processing/start")
    @ResponseBody
    public Map<String, Object> startProcessing() {
        processingStateService.startProcessing();
        return Map.of(
            "status", "success",
            "message", "Processing started",
            "processingState", processingStateService.getProcessingState(),
            "consumerStatus", processingStateService.getConsumerStatus()
        );
    }
    
    /**
     * Stops file processing.
     * @return JSON response indicating the new processing state
     */
    @PostMapping("/api/processing/stop")
    @ResponseBody
    public Map<String, Object> stopProcessing() {
        processingStateService.stopProcessing();
        return Map.of(
            "status", "success",
            "message", "Processing stopped",
            "processingState", processingStateService.getProcessingState(),
            "consumerStatus", processingStateService.getConsumerStatus()
        );
    }
    
    /**
     * Resets the application by stopping processing and clearing processed files from HDFS.
     * @return JSON response indicating the reset operation result
     */
    @PostMapping("/api/processing/reset")
    @ResponseBody
    public Map<String, Object> resetProcessing() {
        // Stop processing first
        processingStateService.stopProcessing();
        
        // Clear processed files from memory
        fileProcessingService.clearProcessedFiles();
        
        // Delete processed files directory from HDFS
        boolean hdfsCleared = hdfsService.deleteProcessedFilesDirectory();
        
        // Recreate the directory for future use
        boolean directoryRecreated = hdfsService.createProcessedFilesDirectory();
        
        return Map.of(
            "status", "success",
            "message", "Reset completed",
            "processingState", processingStateService.getProcessingState(),
            "consumerStatus", processingStateService.getConsumerStatus(),
            "hdfsCleared", hdfsCleared,
            "directoryRecreated", directoryRecreated
        );
    }
    
    /**
     * Gets the current processing state.
     * @return JSON response with the current processing state
     */
    @GetMapping("/api/processing/state")
    @ResponseBody
    public Map<String, Object> getProcessingState() {
        return Map.of(
            "processingState", processingStateService.getProcessingState(),
            "isProcessingEnabled", processingStateService.isProcessingEnabled(),
            "consumerStatus", processingStateService.getConsumerStatus()
        );
    }
}
