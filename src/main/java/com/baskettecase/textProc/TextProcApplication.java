package com.baskettecase.textProc;

import com.baskettecase.textProc.config.ProcessorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ProcessorProperties.class) // Explicitly enable your custom properties
public class TextProcApplication {

    public static void main(String[] args) {
        SpringApplication.run(TextProcApplication.class, args);
    }

}
