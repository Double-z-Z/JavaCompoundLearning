package com.devops.dashboard.domain.exception.environment;

public class EnvironmentCreationException extends EnvironmentException {
    
    public EnvironmentCreationException(String reason) {
        super("Failed to create environment: " + reason);
    }
    
    public EnvironmentCreationException(String reason, Throwable cause) {
        super("Failed to create environment: " + reason, cause);
    }
}
