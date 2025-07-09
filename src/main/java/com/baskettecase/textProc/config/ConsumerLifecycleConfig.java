package com.baskettecase.textProc.config;

import com.baskettecase.textProc.service.ConsumerLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.Map;

/**
 * Configuration for managing RabbitMQ consumer lifecycle.
 * Automatically registers consumer containers with the lifecycle service.
 */
@Configuration
public class ConsumerLifecycleConfig {
    private static final Logger logger = LoggerFactory.getLogger(ConsumerLifecycleConfig.class);
    
    private final ConsumerLifecycleService consumerLifecycleService;
    
    @Autowired
    public ConsumerLifecycleConfig(ConsumerLifecycleService consumerLifecycleService) {
        this.consumerLifecycleService = consumerLifecycleService;
    }
    
    /**
     * Event listener that registers all SimpleMessageListenerContainer beans
     * with the lifecycle service when the application context is refreshed.
     */
    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        Map<String, SimpleMessageListenerContainer> containers = 
            event.getApplicationContext().getBeansOfType(SimpleMessageListenerContainer.class);
        
        logger.info("Found {} message listener containers", containers.size());
        
        for (SimpleMessageListenerContainer container : containers.values()) {
            consumerLifecycleService.registerContainer(container);
        }
    }
} 