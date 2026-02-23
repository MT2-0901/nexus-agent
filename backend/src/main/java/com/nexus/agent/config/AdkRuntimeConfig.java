package com.nexus.agent.config;

import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.InMemorySessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdkRuntimeConfig {

    @Bean
    public BaseSessionService adkSessionService() {
        return new InMemorySessionService();
    }
}
