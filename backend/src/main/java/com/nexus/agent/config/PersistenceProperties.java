package com.nexus.agent.config;

import com.nexus.agent.domain.PersistenceProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.persistence")
public class PersistenceProperties {

    private boolean enabled = true;
    private PersistenceProvider provider = PersistenceProvider.SQLITE;
    private int historyDefaultLimit = 20;
    private int historyMaxLimit = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public PersistenceProvider getProvider() {
        return provider;
    }

    public void setProvider(PersistenceProvider provider) {
        this.provider = provider;
    }

    public int getHistoryDefaultLimit() {
        return historyDefaultLimit;
    }

    public void setHistoryDefaultLimit(int historyDefaultLimit) {
        this.historyDefaultLimit = historyDefaultLimit;
    }

    public int getHistoryMaxLimit() {
        return historyMaxLimit;
    }

    public void setHistoryMaxLimit(int historyMaxLimit) {
        this.historyMaxLimit = historyMaxLimit;
    }
}
