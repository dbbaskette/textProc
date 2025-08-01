package com.baskettecase.textProc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.endpoint.BindingsEndpoint;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;


import java.lang.reflect.Method;

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
        if (bindingsEndpoint == null) {
            logger.warn("BindingsEndpoint not available, cannot control binding state. " +
                       "Make sure spring-boot-starter-actuator is on the classpath and actuator endpoints are enabled.");
            return;
        }
        
        try {
            // Get the State enum class (it's private, so we need reflection)
            Class<?> stateClass = Class.forName("org.springframework.cloud.stream.endpoint.BindingsEndpoint$State");
            Object stateValue = null;
            
            // Find the enum constant that matches our state name
            for (Object enumConstant : stateClass.getEnumConstants()) {
                if (enumConstant.toString().equals(stateName)) {
                    stateValue = enumConstant;
                    break;
                }
            }
            
            if (stateValue == null) {
                logger.error("Unknown binding state: {}", stateName);
                return;
            }
            
            // Find and invoke the changeState method
            Method changeStateMethod = bindingsEndpoint.getClass().getMethod("changeState", String.class, stateClass);
            changeStateMethod.invoke(bindingsEndpoint, BINDING_NAME, stateValue);
            
            logger.info("Successfully changed binding {} state to {}", BINDING_NAME, stateName);
            
        } catch (Exception e) {
            logger.error("Failed to change binding state for {}: {}", BINDING_NAME, e.getMessage(), e);
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