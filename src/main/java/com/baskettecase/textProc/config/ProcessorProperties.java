package com.baskettecase.textProc.config;


import jakarta.validation.constraints.NotBlank; // Using jakarta for Boot 3.x
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "app.processor")
@Validated
public class ProcessorProperties {

    @NotBlank
    private String mode = "standalone"; // Default mode

    private Standalone standalone = new Standalone();
    private K8s k8s = new K8s();
    private CloudFoundry cf = new CloudFoundry();

    // Getters and Setters
    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Standalone getStandalone() {
        return standalone;
    }

    public void setStandalone(Standalone standalone) {
        this.standalone = standalone;
    }

    public K8s getK8s() {
        return k8s;
    }

    public void setK8s(K8s k8s) {
        this.k8s = k8s;
    }

    public CloudFoundry getCf() {
        return cf;
    }

    public void setCf(CloudFoundry cf) {
        this.cf = cf;
    }

    // Nested static classes for mode-specific properties
    public static class Standalone {
        @NotBlank
        private String inputDirectory = "./input_files/";
        @NotBlank
        private String outputDirectory = "./output_text/";
        @NotBlank
        private String errorDirectory = "./error_files/";
        private String processedDirectory = "./processed_files/"; // Optional: to move successfully processed files

        // Getters and Setters
        public String getInputDirectory() { return inputDirectory; }
        public void setInputDirectory(String inputDirectory) { this.inputDirectory = inputDirectory; }
        public String getOutputDirectory() { return outputDirectory; }
        public void setOutputDirectory(String outputDirectory) { this.outputDirectory = outputDirectory; }
        public String getErrorDirectory() { return errorDirectory; }
        public void setErrorDirectory(String errorDirectory) { this.errorDirectory = errorDirectory; }
        public String getProcessedDirectory() { return processedDirectory; }
        public void setProcessedDirectory(String processedDirectory) { this.processedDirectory = processedDirectory; }
    }

    public static class K8s {
        // Define K8s specific properties, e.g., related to PVC, config maps, or SCDF stream properties
        private String sourceTopic; // Example
        private String sinkTopic;   // Example

        public String getSourceTopic() { return sourceTopic; }
        public void setSourceTopic(String sourceTopic) { this.sourceTopic = sourceTopic; }
        public String getSinkTopic() { return sinkTopic; }
        public void setSinkTopic(String sinkTopic) { this.sinkTopic = sinkTopic; }
    }

    public static class CloudFoundry {
        // Define CF specific properties, e.g., related to service bindings or SCDF stream properties
        private String inputChannel;  // Example
        private String outputChannel; // Example

        public String getInputChannel() { return inputChannel; }
        public void setInputChannel(String inputChannel) { this.inputChannel = inputChannel; }
        public String getOutputChannel() { return outputChannel; }
        public void setOutputChannel(String outputChannel) { this.outputChannel = outputChannel; }
    }
}