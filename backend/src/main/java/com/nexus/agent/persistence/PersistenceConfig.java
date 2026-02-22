package com.nexus.agent.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.agent.config.PersistenceProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PersistenceConfig {

    @Bean
    public ChatHistoryStore chatHistoryStore(PersistenceProperties properties,
                                             ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                             ObjectMapper objectMapper) {
        if (!properties.isEnabled()) {
            return new NoopChatHistoryStore();
        }

        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new IllegalStateException("Relational persistence is enabled but JdbcTemplate is unavailable.");
        }

        return new JdbcChatHistoryStore(jdbcTemplate, objectMapper);
    }
}
