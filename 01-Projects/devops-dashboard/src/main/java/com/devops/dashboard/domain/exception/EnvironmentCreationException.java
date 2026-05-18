package com.devops.dashboard.domain.exception;

public class EnvironmentCreationException extends DomainException {
    
    public EnvironmentCreationException(String reason) {
        super("Failed to create environment: " + reason);
    }
    
    public EnvironmentCreationException(String reason, Throwable cause) {
        super("Failed to create environment: " + reason, cause);
    }
}
