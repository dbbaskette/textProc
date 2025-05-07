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
    private Scdf scdf = new Scdf();

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

    public Scdf getScdf() {
        return scdf;
    }

    public void setScdf(Scdf scdf) {
        this.scdf = scdf;
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

    /**
     * Properties for Spring Cloud Data Flow (SCDF) mode.
     * Example: input/output channels for SCDF stream integration.
     */
    public static class Scdf {
        private String inputChannel;
        private String outputChannel;

        public String getInputChannel() { return inputChannel; }
        public void setInputChannel(String inputChannel) { this.inputChannel = inputChannel; }
        public String getOutputChannel() { return outputChannel; }
        public void setOutputChannel(String outputChannel) { this.outputChannel = outputChannel; }
    }


}