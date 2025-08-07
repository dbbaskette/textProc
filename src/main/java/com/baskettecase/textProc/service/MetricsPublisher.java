package com.baskettecase.textProc.service;

public interface MetricsPublisher {
    void publishMetrics(TextProcMonitorService.MonitoringData monitoringData);
}


