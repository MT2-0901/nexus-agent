package com.nexus.agent.persistence;

import com.nexus.agent.config.PersistenceProperties;
import com.nexus.agent.domain.PersistenceProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
public class SqliteSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    private final PersistenceProperties persistenceProperties;

    public SqliteSchemaInitializer(DataSource dataSource, PersistenceProperties persistenceProperties) {
        this.dataSource = dataSource;
        this.persistenceProperties = persistenceProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!persistenceProperties.isEnabled() || persistenceProperties.getProvider() != PersistenceProvider.SQLITE) {
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("sql/schema-sqlite.sql"));
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to initialize SQLite schema", ex);
        }
    }
}
