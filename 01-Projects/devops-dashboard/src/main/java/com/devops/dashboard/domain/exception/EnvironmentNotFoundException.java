package com.devops.dashboard.domain.exception;

public class EnvironmentNotFoundException extends DomainException {
    
    private final String environmentId;
    
    public EnvironmentNotFoundException(String environmentId) {
        super("Environment not found: " + environmentId);
        this.environmentId = environmentId;
    }
    
    public String getEnvironmentId() {
        return environmentId;
    }
}
