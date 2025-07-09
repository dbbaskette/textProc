package com.baskettecase.textProc.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for managing the processing state of the application.
 * Controls whether the application should process incoming files or not.
 */
@Service
public class ProcessingStateService {
    private final AtomicBoolean isProcessingEnabled = new AtomicBoolean(false); // Default to stopped
    
    /**
     * Checks if processing is currently enabled.
     * @return true if processing is enabled, false otherwise
     */
    public boolean isProcessingEnabled() {
        return isProcessingEnabled.get();
    }
    
    /**
     * Enables file processing.
     */
    public void startProcessing() {
        isProcessingEnabled.set(true);
    }
    
    /**
     * Disables file processing.
     */
    public void stopProcessing() {
        isProcessingEnabled.set(false);
    }
    
    /**
     * Gets the current processing state as a string.
     * @return "STARTED" if processing is enabled, "STOPPED" otherwise
     */
    public String getProcessingState() {
        return isProcessingEnabled.get() ? "STARTED" : "STOPPED";
    }
} 