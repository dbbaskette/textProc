package com.baskettecase.textProc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.monitoring.rabbitmq.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpMetricsPublisher implements MetricsPublisher {
    private static final Logger logger = LoggerFactory.getLogger(NoOpMetricsPublisher.class);

    public NoOpMetricsPublisher() {
        logger.info("NoOpMetricsPublisher initialized - RabbitMQ metrics publishing disabled");
    }

    @Override
    public void publishMetrics(TextProcMonitorService.MonitoringData monitoringData) {
        // no-op
    }
}


