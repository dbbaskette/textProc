package com.baskettecase.textProc.service;

import com.baskettecase.textProc.model.FileProcessingInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for tracking file processing information across the application.
 * Maintains a thread-safe map of processed files and stream information.
 */
@Service
public class FileProcessingService {
    private final Map<String, FileProcessingInfo> processedFiles = new ConcurrentHashMap<>();
    private String inputStreamName = "default-input";
    private String outputStreamName = "default-output";

    /**
     * Adds or updates a file processing information record.
     * @param fileInfo The file processing information to add/update.
     */
    public void addProcessedFile(FileProcessingInfo fileInfo) {
        if (fileInfo != null && fileInfo.getFilename() != null) {
            processedFiles.put(fileInfo.getFilename(), fileInfo);
        }
    }

    /**
     * Retrieves all processed files information.
     * @return A list of all file processing information records.
     */
    public List<FileProcessingInfo> getAllProcessedFiles() {
        return new ArrayList<>(processedFiles.values());
    }

    /**
     * Retrieves processing information for a specific file.
     * @param filename The name of the file to look up.
     * @return The file processing information or null if not found.
     */
    public FileProcessingInfo getFileInfo(String filename) {
        return filename != null ? processedFiles.get(filename) : null;
    }

    /**
     * Updates the input and output stream names.
     * @param inputStream The name of the input stream.
     * @param outputStream The name of the output stream.
     */
    public void setStreamNames(String inputStream, String outputStream) {
        if (inputStream != null) {
            this.inputStreamName = inputStream;
        }
        if (outputStream != null) {
            this.outputStreamName = outputStream;
        }
    }

    public String getInputStreamName() {
        return inputStreamName;
    }

    public String getOutputStreamName() {
        return outputStreamName;
    }
    
    /**
     * Clears all processed files from memory.
     * Used during reset operations.
     */
    public void clearProcessedFiles() {
        processedFiles.clear();
    }
}
