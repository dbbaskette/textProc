package com.baskettecase.textProc.endpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.cloud.stream.endpoint.BindingsEndpoint;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Custom Spring Boot Actuator endpoint for controlling Spring Cloud Stream bindings.
 * Provides start/stop operations for message consumers at runtime.
 */
@Component
@Endpoint(id = "stream-control")
public class StreamControlEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(StreamControlEndpoint.class);
    
    private final BindingsEndpoint bindingsEndpoint;
    
    public StreamControlEndpoint(BindingsEndpoint bindingsEndpoint) {
        this.bindingsEndpoint = bindingsEndpoint;
    }
    
    /**
     * Starts the specified binding.
     * 
     * @param bindingName The name of the binding to start (e.g., "textProc-in-0")
     * @return Result of the operation
     */
    @WriteOperation
    public Map<String, Object> startBinding(String bindingName) {
        logger.info("Starting binding: {}", bindingName);
        
        try {
            // Query current state
            var currentState = bindingsEndpoint.queryState(bindingName);
            logger.info("Current state before start: {}", currentState);
            
            // Start the binding using the documented REST approach
            // The BindingsEndpoint supports changeState via reflection
            // We'll try multiple approaches to find the right method
            try {
                // Approach 1: Try to find changeState method that accepts State enum
                Class<?> bindingsClass = bindingsEndpoint.getClass();
                java.lang.reflect.Method[] methods = bindingsClass.getMethods();
                
                for (java.lang.reflect.Method method : methods) {
                    if ("changeState".equals(method.getName())) {
                        Class<?>[] paramTypes = method.getParameterTypes();
                        if (paramTypes.length == 2 && 
                            String.class.equals(paramTypes[0]) && 
                            paramTypes[1].isEnum()) {
                            
                            // Found the enum-based method, get the STARTED enum value
                            Object[] enumConstants = paramTypes[1].getEnumConstants();
                            for (Object enumConstant : enumConstants) {
                                if ("STARTED".equals(enumConstant.toString())) {
                                    method.invoke(bindingsEndpoint, bindingName, enumConstant);
                                    logger.info("Successfully called changeState with STARTED enum");
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to use changeState method, binding might not support state changes: {}", e.getMessage());
                throw new RuntimeException("Unable to start binding - " + e.getMessage());
            }
            
            // Wait a moment for the change to take effect
            Thread.sleep(1000);
            
            // Query new state
            var newState = bindingsEndpoint.queryState(bindingName);
            logger.info("New state after start: {}", newState);
            
            return Map.of(
                "operation", "start",
                "bindingName", bindingName,
                "success", true,
                "previousState", currentState,
                "newState", newState
            );
            
        } catch (Exception e) {
            logger.error("Failed to start binding {}: {}", bindingName, e.getMessage(), e);
            return Map.of(
                "operation", "start",
                "bindingName", bindingName,
                "success", false,
                "error", e.getMessage()
            );
        }
    }
    
    /**
     * Stops the specified binding.
     * 
     * @param bindingName The name of the binding to stop (e.g., "textProc-in-0")
     * @return Result of the operation
     */
    @WriteOperation
    public Map<String, Object> stopBinding(String bindingName) {
        logger.info("Stopping binding: {}", bindingName);
        
        try {
            // Query current state
            var currentState = bindingsEndpoint.queryState(bindingName);
            logger.info("Current state before stop: {}", currentState);
            
            // Stop the binding using the documented REST approach
            try {
                // Find changeState method that accepts State enum
                Class<?> bindingsClass = bindingsEndpoint.getClass();
                java.lang.reflect.Method[] methods = bindingsClass.getMethods();
                
                for (java.lang.reflect.Method method : methods) {
                    if ("changeState".equals(method.getName())) {
                        Class<?>[] paramTypes = method.getParameterTypes();
                        if (paramTypes.length == 2 && 
                            String.class.equals(paramTypes[0]) && 
                            paramTypes[1].isEnum()) {
                            
                            // Found the enum-based method, get the STOPPED enum value
                            Object[] enumConstants = paramTypes[1].getEnumConstants();
                            for (Object enumConstant : enumConstants) {
                                if ("STOPPED".equals(enumConstant.toString())) {
                                    method.invoke(bindingsEndpoint, bindingName, enumConstant);
                                    logger.info("Successfully called changeState with STOPPED enum");
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to use changeState method, binding might not support state changes: {}", e.getMessage());
                throw new RuntimeException("Unable to stop binding - " + e.getMessage());
            }
            
            // Wait a moment for the change to take effect
            Thread.sleep(1000);
            
            // Query new state
            var newState = bindingsEndpoint.queryState(bindingName);
            logger.info("New state after stop: {}", newState);
            
            return Map.of(
                "operation", "stop",
                "bindingName", bindingName,
                "success", true,
                "previousState", currentState,
                "newState", newState
            );
            
        } catch (Exception e) {
            logger.error("Failed to stop binding {}: {}", bindingName, e.getMessage(), e);
            return Map.of(
                "operation", "stop",
                "bindingName", bindingName,
                "success", false,
                "error", e.getMessage()
            );
        }
    }
    
    /**
     * Gets the current state of a binding.
     * 
     * @param bindingName The name of the binding to query
     * @return Current state information
     */
    @WriteOperation
    public Map<String, Object> getBindingState(String bindingName) {
        logger.info("Getting state for binding: {}", bindingName);
        
        try {
            var state = bindingsEndpoint.queryState(bindingName);
            
            return Map.of(
                "operation", "getState",
                "bindingName", bindingName,
                "success", true,
                "state", state
            );
            
        } catch (Exception e) {
            logger.error("Failed to get state for binding {}: {}", bindingName, e.getMessage(), e);
            return Map.of(
                "operation", "getState",
                "bindingName", bindingName,
                "success", false,
                "error", e.getMessage()
            );
        }
    }
}