package com.nexus.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "nexus.adk")
public class AdkProperties {

    private String appName = "nexus-agent";
    private String model = "gemini-2.0-flash";
    private List<String> availableModels = List.of("gemini-2.0-flash");
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

    public List<String> getAvailableModels() {
        return availableModels;
    }

    public void setAvailableModels(List<String> availableModels) {
        this.availableModels = availableModels;
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
