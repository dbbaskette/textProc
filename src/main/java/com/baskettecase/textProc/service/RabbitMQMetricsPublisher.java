package com.baskettecase.textProc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name = "app.monitoring.rabbitmq.enabled", havingValue = "true", matchIfMissing = false)
public class RabbitMQMetricsPublisher implements MetricsPublisher {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQMetricsPublisher.class);

    private final AmqpTemplate amqpTemplate;
    private final ObjectMapper objectMapper;
    private final String queueName;
    private final AtomicBoolean circuitBreakerOpen = new AtomicBoolean(false);
    private volatile long lastFailureTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 1 minute

    @Autowired
    public RabbitMQMetricsPublisher(AmqpTemplate amqpTemplate,
                                    ObjectMapper objectMapper,
                                    @Value("${app.monitoring.rabbitmq.queue-name:pipeline.metrics}") String queueName) {
        this.amqpTemplate = amqpTemplate;
        this.objectMapper = objectMapper;
        this.queueName = queueName;
        logger.info("RabbitMQMetricsPublisher initialized with queue: {}", queueName);
    }

    @Override
    public void publishMetrics(TextProcMonitorService.MonitoringData monitoringData) {
        // Circuit breaker check
        if (circuitBreakerOpen.get()) {
            if (System.currentTimeMillis() - lastFailureTime < CIRCUIT_BREAKER_TIMEOUT) {
                logger.trace("Circuit breaker open, skipping metrics publish");
                return;
            } else {
                logger.info("Circuit breaker timeout expired, attempting to publish metrics");
                circuitBreakerOpen.set(false);
            }
        }

        try {
            String jsonMessage = objectMapper.writeValueAsString(monitoringData);

            org.springframework.amqp.core.Message message = new org.springframework.amqp.core.Message(
                    jsonMessage.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    new org.springframework.amqp.core.MessageProperties()
            );
            message.getMessageProperties().setContentType("application/json");

            amqpTemplate.send(queueName, message);

            if (circuitBreakerOpen.get()) {
                circuitBreakerOpen.set(false);
                logger.info("Circuit breaker reset - RabbitMQ connection restored");
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize monitoring data to JSON: {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("Failed to publish metrics to RabbitMQ: {}", e.getMessage());
            circuitBreakerOpen.set(true);
            lastFailureTime = System.currentTimeMillis();
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "app.monitoring.rabbitmq.enabled", havingValue = "true", matchIfMissing = false)
    public static class MetricsQueueConfiguration {
        @Bean
        public Queue metricsQueue(@Value("${app.monitoring.rabbitmq.queue-name:pipeline.metrics}") String queueName) {
            return new Queue(queueName, true, false, false);
        }
    }
}


