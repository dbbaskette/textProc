package com.baskettecase.textProc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for managing Spring Cloud Stream binding lifecycle.
 * 
 * NOTE: This configuration class is no longer needed as ConsumerLifecycleService
 * now directly uses Spring Cloud Stream's BindingsEndpoint to control binding lifecycle
 * instead of managing SimpleMessageListenerContainer instances.
 * 
 * The class is kept for now to avoid breaking existing configuration but may be 
 * removed in future versions.
 */
@Configuration
public class ConsumerLifecycleConfig {
    private static final Logger logger = LoggerFactory.getLogger(ConsumerLifecycleConfig.class);
    
    public ConsumerLifecycleConfig() {
        logger.info("ConsumerLifecycleConfig initialized. Note: Consumer lifecycle is now managed " +
                   "through Spring Cloud Stream BindingsEndpoint rather than traditional message containers.");
    }
} 