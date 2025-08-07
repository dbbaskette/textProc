package com.baskettecase.textProc.config;

import com.baskettecase.textProc.service.ConsumerLifecycleService;
import com.baskettecase.textProc.service.FileProcessingService;
import com.baskettecase.textProc.service.HdfsService;
import com.baskettecase.textProc.service.ProcessingStateService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class TextProcHealthIndicator implements HealthIndicator {

    private final ProcessingStateService processingStateService;
    private final ConsumerLifecycleService consumerLifecycleService;
    private final HdfsService hdfsService;
    private final FileProcessingService fileProcessingService;

    public TextProcHealthIndicator(ProcessingStateService processingStateService,
                                   ConsumerLifecycleService consumerLifecycleService,
                                   HdfsService hdfsService,
                                   FileProcessingService fileProcessingService) {
        this.processingStateService = processingStateService;
        this.consumerLifecycleService = consumerLifecycleService;
        this.hdfsService = hdfsService;
        this.fileProcessingService = fileProcessingService;
    }

    @Override
    public Health health() {
        boolean hdfsOk = hdfsService.processedFilesDirectoryExists();
        boolean consumerRunning = consumerLifecycleService.areConsumersRunning();
        String state = processingStateService.getProcessingState();
        int processedCount = fileProcessingService.getAllProcessedFiles().size();

        Health.Builder builder = Health.up()
                .withDetail("processingState", state)
                .withDetail("consumerRunning", consumerRunning)
                .withDetail("hdfsProcessedDirExists", hdfsOk)
                .withDetail("processedCount", processedCount);

        if (!hdfsOk) {
            builder = Health.status("DEGRADED");
        }
        return builder.build();
    }
}


