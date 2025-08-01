package com.baskettecase.textProc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.endpoint.BindingsEndpoint;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Service for managing Spring Cloud Stream binding lifecycle.
 * Listens to processing state events and manages consumer bindings accordingly.
 * Uses Spring Cloud Stream's BindingsEndpoint to properly pause/resume bindings
 * ensuring messages remain in the queue when processing is disabled.
 */
@Service
public class ConsumerLifecycleService {
    private static final Logger logger = LoggerFactory.getLogger(ConsumerLifecycleService.class);
    
    // The binding name for the textProc function input
    private static final String BINDING_NAME = "textProc-in-0";
    
    @Autowired(required = false)
    private BindingsEndpoint bindingsEndpoint;
    
    /**
     * Initialize the binding state to STOPPED when the application context is refreshed.
     * This ensures that regardless of the auto-startup configuration, the binding starts in STOPPED state
     * to match the ProcessingStateService default state.
     */
    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        // Use a slight delay to ensure BindingsEndpoint is fully initialized
        new Thread(() -> {
            try {
                Thread.sleep(2000); // 2 second delay
                logger.info("Initializing binding {} to STOPPED state", BINDING_NAME);
                changeBindingState("STOPPED");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while initializing binding state");
            }
        }).start();
    }
    
    /**
     * Event listener for processing started events.
     * @param event The processing started event
     */
    @EventListener
    public void handleProcessingStarted(ProcessingStateService.ProcessingStartedEvent event) {
        logger.info("Received processing started event, starting binding: {}", BINDING_NAME);
        changeBindingState("STARTED");
    }
    
    /**
     * Event listener for processing stopped events.
     * @param event The processing stopped event
     */
    @EventListener
    public void handleProcessingStopped(ProcessingStateService.ProcessingStoppedEvent event) {
        logger.info("Received processing stopped event, stopping binding: {}", BINDING_NAME);
        changeBindingState("STOPPED");
    }
    
    /**
     * Changes the state of the Spring Cloud Stream binding using the BindingsEndpoint.
     * Uses reflection to access the private State enum and changeState method.
     * 
     * @param stateName The state name ("STARTED", "STOPPED", "PAUSED", "RESUMED")
     */
    private void changeBindingState(String stateName) {
        logger.info("Attempting to change binding {} state to {}", BINDING_NAME, stateName);
        
        if (bindingsEndpoint == null) {
            logger.warn("BindingsEndpoint not available, cannot control binding state. " +
                       "Make sure spring-boot-starter-actuator is on the classpath and actuator endpoints are enabled.");
            return;
        }
        
        // First, let's see what bindings are available
        try {
            var allBindings = bindingsEndpoint.queryStates();
            logger.info("Available bindings: {}", allBindings);
        } catch (Exception e) {
            logger.warn("Could not query all binding states: {}", e.getMessage());
        }
        
        try {
            // Use direct start/stop methods instead of complex reflection
            
            // Check current state before change
            try {
                var currentBindings = bindingsEndpoint.queryState(BINDING_NAME);
                logger.info("Current binding {} state before change: {}", BINDING_NAME, 
                    currentBindings != null && !currentBindings.isEmpty() ? currentBindings.get(0) : "NOT_FOUND");
            } catch (Exception e) {
                logger.warn("Could not query current binding state: {}", e.getMessage());
            }
            
            // Use the changeState method with String-based states
            if ("STARTED".equals(stateName) || "STOPPED".equals(stateName)) {
                // The changeState method accepts (String bindingName, String state)
                // We'll use reflection to call it with string parameters
                Method changeStateMethod = bindingsEndpoint.getClass().getMethod("changeState", String.class, String.class);
                Object result = changeStateMethod.invoke(bindingsEndpoint, BINDING_NAME, stateName);
                logger.info("changeState method returned: {}", result);
            } else {
                logger.warn("Unsupported state name: {}. Use STARTED or STOPPED.", stateName);
                return;
            }
            
            // Wait a moment for the change to take effect
            Thread.sleep(500);
            
            // Check state after change
            try {
                var updatedBindings = bindingsEndpoint.queryState(BINDING_NAME);
                logger.info("Updated binding {} state after change: {}", BINDING_NAME, 
                    updatedBindings != null && !updatedBindings.isEmpty() ? updatedBindings.get(0) : "NOT_FOUND");
            } catch (Exception e) {
                logger.warn("Could not query updated binding state: {}", e.getMessage());
            }
            
            logger.info("Successfully changed binding {} state to {}", BINDING_NAME, stateName);
            
        } catch (Exception e) {
            logger.error("Failed to change binding state for {}: {}", BINDING_NAME, e.getMessage(), e);
            // Don't throw the exception - log it and continue
            // This prevents the application from failing if there are binding control issues
        }
    }
    
    /**
     * Checks if the binding is currently running.
     * @return true if binding is running, false otherwise
     */
    public boolean areConsumersRunning() {
        if (bindingsEndpoint == null) {
            return false;
        }
        
        try {
            var bindings = bindingsEndpoint.queryState(BINDING_NAME);
            if (bindings != null && !bindings.isEmpty()) {
                var binding = bindings.get(0);
                // Use reflection to access the state since the API might vary
                Method getStateMethod = binding.getClass().getMethod("getState");
                Object state = getStateMethod.invoke(binding);
                return "STARTED".equals(state.toString());
            }
            return false;
        } catch (Exception e) {
            logger.warn("Failed to query binding state: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets the status of the binding.
     * @return A string describing the binding status
     */
    public String getConsumerStatus() {
        if (bindingsEndpoint == null) {
            return "BindingsEndpoint not available";
        }
        
        try {
            var bindings = bindingsEndpoint.queryState(BINDING_NAME);
            if (bindings != null && !bindings.isEmpty()) {
                var binding = bindings.get(0);
                // Use reflection to access the state since the API might vary
                Method getStateMethod = binding.getClass().getMethod("getState");
                Object state = getStateMethod.invoke(binding);
                return String.format("Binding %s: %s", BINDING_NAME, state.toString());
            } else {
                return String.format("Binding %s: NOT_FOUND", BINDING_NAME);
            }
        } catch (Exception e) {
            return String.format("Binding %s: ERROR (%s)", BINDING_NAME, e.getMessage());
        }
    }
} 