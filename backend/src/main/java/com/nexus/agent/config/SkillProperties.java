package com.nexus.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.skills")
public class SkillProperties {

    private String path = "skills";

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
