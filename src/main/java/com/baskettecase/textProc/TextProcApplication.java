package com.baskettecase.textProc;

import com.baskettecase.textProc.config.ProcessorProperties;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the textProc Spring Boot application.
 * Configures and launches the application with custom processor properties.
 */
@SpringBootApplication
@EnableConfigurationProperties(ProcessorProperties.class) // Explicitly enable your custom properties
@EnableScheduling
public class TextProcApplication extends SpringBootServletInitializer {

    /**
     * Main method to launch the Spring Boot application.
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        new SpringApplicationBuilder(TextProcApplication.class)
            .web(WebApplicationType.SERVLET)
            .run(args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(TextProcApplication.class);
    }
}
