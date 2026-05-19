package com.devops.dashboard.domain.exception.environment;

public class EnvironmentDestroyException extends EnvironmentException {

    public EnvironmentDestroyException(String envId, String reason) {
        super("Failed to destroy environment " + envId + ": " + reason);
    }
}