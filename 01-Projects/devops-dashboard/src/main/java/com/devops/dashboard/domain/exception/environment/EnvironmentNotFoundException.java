package com.devops.dashboard.domain.exception.environment;

public class EnvironmentNotFoundException extends EnvironmentException {

    private final String environmentId;

    public EnvironmentNotFoundException(String environmentId) {
        super("Environment not found: " + environmentId);
        this.environmentId = environmentId;
    }

    public String getEnvironmentId() {
        return environmentId;
    }
}