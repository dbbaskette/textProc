package com.baskettecase.textProc.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    
    // State change tracking fields
    private volatile OffsetDateTime lastStateChange = OffsetDateTime.now(ZoneOffset.UTC);
    private volatile String lastChangeReason = "Application startup";
    
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
     * @return true if state changed, false if already enabled
     */
    public boolean startProcessing() {
        boolean previousState = isProcessingEnabled.getAndSet(true);
        if (!previousState) {
            lastStateChange = OffsetDateTime.now(ZoneOffset.UTC);
            lastChangeReason = "Processing started via API";
            eventPublisher.publishEvent(new ProcessingStartedEvent(this));
            return true; // State changed
        }
        return false; // State didn't change
    }
    
    /**
     * Disables file processing and publishes a stop event.
     * @return true if state changed, false if already disabled
     */
    public boolean stopProcessing() {
        boolean previousState = isProcessingEnabled.getAndSet(false);
        if (previousState) {
            lastStateChange = OffsetDateTime.now(ZoneOffset.UTC);
            lastChangeReason = "Processing stopped via API";
            eventPublisher.publishEvent(new ProcessingStoppedEvent(this));
            return true; // State changed
        }
        return false; // State didn't change
    }
    
    /**
     * Gets the current processing state as a string.
     * @return "STARTED" if processing is enabled, "STOPPED" otherwise
     */
    public String getProcessingState() {
        return isProcessingEnabled.get() ? "STARTED" : "STOPPED";
    }
    
    /**
     * Gets the timestamp of the last state change.
     * @return OffsetDateTime of the last state change
     */
    public OffsetDateTime getLastChanged() {
        return lastStateChange;
    }
    
    /**
     * Gets the reason for the last state change.
     * @return String describing the reason for the last state change
     */
    public String getLastChangeReason() {
        return lastChangeReason;
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