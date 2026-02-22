package com.nexus.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.adk")
public class AdkProperties {

    private String appName = "nexus-agent";
    private String model = "gemini-2.0-flash";
    private String defaultUserId = "local-user";
    private String defaultSessionPrefix = "sess";

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getDefaultUserId() {
        return defaultUserId;
    }

    public void setDefaultUserId(String defaultUserId) {
        this.defaultUserId = defaultUserId;
    }

    public String getDefaultSessionPrefix() {
        return defaultSessionPrefix;
    }

    public void setDefaultSessionPrefix(String defaultSessionPrefix) {
        this.defaultSessionPrefix = defaultSessionPrefix;
    }
}
