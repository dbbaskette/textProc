package com.baskettecase.textProc.model;

import java.time.LocalDateTime;

public class FileProcessingInfo {
    private String filename;
    private String fileType;
    private long fileSize;
    private int chunkSize;
    private int chunkCount;
    private LocalDateTime processedAt;
    private String status;
    private String inputStream;
    private String outputStream;

    // Getters and Setters
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    
    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getInputStream() { return inputStream; }
    public void setInputStream(String inputStream) { this.inputStream = inputStream; }
    
    public String getOutputStream() { return outputStream; }
    public void setOutputStream(String outputStream) { this.outputStream = outputStream; }
}
