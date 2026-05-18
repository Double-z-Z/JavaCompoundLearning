package com.devops.dashboard.domain.exception;

public class EnvironmentDestroyException extends DomainException {
    
    public EnvironmentDestroyException(String envId, String reason) {
        super("Failed to destroy environment " + envId + ": " + reason);
    }
}
