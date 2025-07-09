package com.baskettecase.textProc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.binder.rabbit.RabbitMessageChannelBinder;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitBinderConfigurationProperties;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for managing RabbitMQ consumer lifecycle.
 * Allows pausing and resuming message consumption.
 */
@Service
public class ConsumerLifecycleService {
    private static final Logger logger = LoggerFactory.getLogger(ConsumerLifecycleService.class);
    
    private final List<SimpleMessageListenerContainer> containers = new CopyOnWriteArrayList<>();
    private ProcessingStateService processingStateService;
    
    /**
     * Registers a message listener container for lifecycle management.
     * @param container The container to register
     */
    public void registerContainer(SimpleMessageListenerContainer container) {
        containers.add(container);
        logger.info("Registered message listener container: {}", container.getListenerId());
        
        // Set initial state based on processing state
        if (processingStateService != null && !processingStateService.isProcessingEnabled()) {
            pauseConsumers();
        }
    }
    
    /**
     * Pauses all registered message consumers.
     */
    public void pauseConsumers() {
        for (SimpleMessageListenerContainer container : containers) {
            if (container.isRunning()) {
                container.stop();
                logger.info("Paused message consumer: {}", container.getListenerId());
            }
        }
    }
    
    /**
     * Resumes all registered message consumers.
     */
    public void resumeConsumers() {
        for (SimpleMessageListenerContainer container : containers) {
            if (!container.isRunning()) {
                container.start();
                logger.info("Resumed message consumer: {}", container.getListenerId());
            }
        }
    }
    
    /**
     * Checks if any consumers are currently running.
     * @return true if any consumer is running, false otherwise
     */
    public boolean areConsumersRunning() {
        return containers.stream().anyMatch(SimpleMessageListenerContainer::isRunning);
    }
    
    /**
     * Gets the status of all registered consumers.
     * @return A string describing the consumer status
     */
    public String getConsumerStatus() {
        long runningCount = containers.stream().filter(SimpleMessageListenerContainer::isRunning).count();
        long totalCount = containers.size();
        return String.format("%d/%d consumers running", runningCount, totalCount);
    }
    
    /**
     * Sets the processing state service (called after initialization to break circular dependency).
     * @param processingStateService The processing state service
     */
    @Autowired
    public void setProcessingStateService(ProcessingStateService processingStateService) {
        this.processingStateService = processingStateService;
    }
} 