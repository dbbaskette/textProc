package com.baskettecase.textProc.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for managing the processing state of the application.
 * Controls whether the application should process incoming files or not.
 */
@Service
public class ProcessingStateService {
    private final AtomicBoolean isProcessingEnabled = new AtomicBoolean(false); // Default to stopped
    private final ConsumerLifecycleService consumerLifecycleService;
    
    @Autowired
    public ProcessingStateService(ConsumerLifecycleService consumerLifecycleService) {
        this.consumerLifecycleService = consumerLifecycleService;
    }
    
    /**
     * Checks if processing is currently enabled.
     * @return true if processing is enabled, false otherwise
     */
    public boolean isProcessingEnabled() {
        return isProcessingEnabled.get();
    }
    
    /**
     * Enables file processing and resumes message consumers.
     */
    public void startProcessing() {
        isProcessingEnabled.set(true);
        consumerLifecycleService.resumeConsumers();
    }
    
    /**
     * Disables file processing and pauses message consumers.
     */
    public void stopProcessing() {
        isProcessingEnabled.set(false);
        consumerLifecycleService.pauseConsumers();
    }
    
    /**
     * Gets the current processing state as a string.
     * @return "STARTED" if processing is enabled, "STOPPED" otherwise
     */
    public String getProcessingState() {
        return isProcessingEnabled.get() ? "STARTED" : "STOPPED";
    }
    
    /**
     * Gets the consumer status.
     * @return A string describing the consumer status
     */
    public String getConsumerStatus() {
        return consumerLifecycleService.getConsumerStatus();
    }
} 