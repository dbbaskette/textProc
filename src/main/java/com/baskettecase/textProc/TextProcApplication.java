package com.baskettecase.textProc;

import com.baskettecase.textProc.config.ProcessorProperties;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main entry point for the textProc Spring Boot application.
 * Configures and launches the application with custom processor properties.
 */
@SpringBootApplication
@EnableConfigurationProperties(ProcessorProperties.class) // Explicitly enable your custom properties
public class TextProcApplication {

    /**
     * Main method to launch the Spring Boot application.
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        new SpringApplicationBuilder(TextProcApplication.class)
            .web(WebApplicationType.NONE) // Disable web environment
            .run(args);
    }

}
