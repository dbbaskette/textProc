package com.baskettecase.textProc.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for managing the processing state of the application.
 * Controls whether the application should process incoming files or not.
 * Uses Spring events to notify other components of state changes.
 */
@Service
public class ProcessingStateService {
    private final AtomicBoolean isProcessingEnabled = new AtomicBoolean(false); // Default to stopped
    private final ApplicationEventPublisher eventPublisher;
    
    @Autowired
    public ProcessingStateService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Checks if processing is currently enabled.
     * @return true if processing is enabled, false otherwise
     */
    public boolean isProcessingEnabled() {
        return isProcessingEnabled.get();
    }
    
    /**
     * Enables file processing and publishes a start event.
     */
    public void startProcessing() {
        isProcessingEnabled.set(true);
        eventPublisher.publishEvent(new ProcessingStartedEvent(this));
    }
    
    /**
     * Disables file processing and publishes a stop event.
     */
    public void stopProcessing() {
        isProcessingEnabled.set(false);
        eventPublisher.publishEvent(new ProcessingStoppedEvent(this));
    }
    
    /**
     * Gets the current processing state as a string.
     * @return "STARTED" if processing is enabled, "STOPPED" otherwise
     */
    public String getProcessingState() {
        return isProcessingEnabled.get() ? "STARTED" : "STOPPED";
    }
    
    /**
     * Event published when processing is started.
     */
    public static class ProcessingStartedEvent {
        private final ProcessingStateService source;
        
        public ProcessingStartedEvent(ProcessingStateService source) {
            this.source = source;
        }
        
        public ProcessingStateService getSource() {
            return source;
        }
    }
    
    /**
     * Event published when processing is stopped.
     */
    public static class ProcessingStoppedEvent {
        private final ProcessingStateService source;
        
        public ProcessingStoppedEvent(ProcessingStateService source) {
            this.source = source;
        }
        
        public ProcessingStateService getSource() {
            return source;
        }
    }
} 