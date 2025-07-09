package com.baskettecase.textProc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for managing RabbitMQ consumer lifecycle.
 * Listens to processing state events and manages consumer lifecycle accordingly.
 */
@Service
public class ConsumerLifecycleService {
    private static final Logger logger = LoggerFactory.getLogger(ConsumerLifecycleService.class);
    
    private final List<SimpleMessageListenerContainer> containers = new CopyOnWriteArrayList<>();
    
    /**
     * Registers a message listener container for lifecycle management.
     * @param container The container to register
     */
    public void registerContainer(SimpleMessageListenerContainer container) {
        containers.add(container);
        logger.info("Registered message listener container: {}", container.getListenerId());
        
        // Start paused since processing starts disabled by default
        if (container.isRunning()) {
            container.stop();
            logger.info("Initially paused container: {}", container.getListenerId());
        }
    }
    
    /**
     * Event listener for processing started events.
     * @param event The processing started event
     */
    @EventListener
    public void handleProcessingStarted(ProcessingStateService.ProcessingStartedEvent event) {
        logger.info("Received processing started event, resuming consumers");
        resumeConsumers();
    }
    
    /**
     * Event listener for processing stopped events.
     * @param event The processing stopped event
     */
    @EventListener
    public void handleProcessingStopped(ProcessingStateService.ProcessingStoppedEvent event) {
        logger.info("Received processing stopped event, pausing consumers");
        pauseConsumers();
    }
    
    /**
     * Pauses all registered message consumers.
     */
    private void pauseConsumers() {
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
    private void resumeConsumers() {
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
} 