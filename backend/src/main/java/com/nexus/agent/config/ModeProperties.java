package com.nexus.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.modes")
public class ModeProperties {

    private String path = "modes";

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
