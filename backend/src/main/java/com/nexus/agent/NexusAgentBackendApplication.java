package com.nexus.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.nexus.agent.config")
public class NexusAgentBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusAgentBackendApplication.class, args);
    }
}
