package com.abc.claims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class ClaimsProcessorApplication {

    public static void main(String[] args) {
        boolean batchMode = args.length > 0 && "batch".equalsIgnoreCase(args[0]);
        if (batchMode) {
            // Headless CLI: skip Tomcat, run the BatchCommandLineRunner, then exit cleanly.
            new SpringApplicationBuilder(ClaimsProcessorApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(args)
                    .close();
            return;
        }
        SpringApplication.run(ClaimsProcessorApplication.class, args);
    }
}
